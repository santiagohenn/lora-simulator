package network;

import assets.Transmission;
import constellation.tools.math.TimedMetricsRecord;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.frames.FramesFactory;
import org.orekit.utils.IERSConventions;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;
import structures.Event;

import java.util.*;

/**
 * Scenario container: end-device positions, satellites, access intervals and generated transmissions.
 *
 * <p>Access intervals are computed with satellite-tools/Orekit for every (end device, satellite) pair
 * (EME2000 inertial frame, ITRF/IERS 2010 fixed frame, minimum elevation {@code visibilityThresholdDegrees})
 * and merged per end device into intervals labelled with the set of satellites in view.
 * All timestamps are Unix epoch milliseconds; time on air and silent times are in milliseconds.
 */
public class Network {

    static final Logger Log = LogManager.getLogger(Network.class);

    private List<Interval> accessIntervals;
    private List<Position> earthPositions;
    private List<Satellite> satellites;
    private List<Transmission> transmissions;

    private TrafficFactory trafficFactory;

    private double timeStepSeconds;
    private double timeOnAirMS;
    private long dutyCycleSilentTimeMS;
    private double frameGenerationRate;
    private double dutyCyclePercentage;
    private double visibilityThresholdDegrees;
    private String scenarioStartTime;
    private String scenarioEndTime;
    private Simulation simulation;

    private int lastSimHash = 0;

    public Network(String orekitDataPath) {
        simulation = new Simulation(orekitDataPath);
        accessIntervals = new ArrayList<>();
    }

    public Network() {
        simulation = new Simulation();
        accessIntervals = new ArrayList<>();
    }

    public Network(double timeStepSeconds, double timeOnAirMS, double dutyCyclePercentage, double frameGenerationRatePacketsPerSecond) {
        setParams(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRatePacketsPerSecond);
        simulation = new Simulation();
        simulation.setVisibilityThreshold(visibilityThresholdDegrees);
        accessIntervals = new ArrayList<>();
    }

    public void setParams(double timeStepSeconds, double timeOnAirMS, double dutyCyclePercentage, double frameGenerationRate) {
        this.timeStepSeconds = timeStepSeconds;
        this.timeOnAirMS = timeOnAirMS;
        this.dutyCycleSilentTimeMS = (long) ((100.0 - dutyCyclePercentage) * timeOnAirMS);
        this.dutyCyclePercentage = dutyCyclePercentage;
        this.frameGenerationRate = frameGenerationRate;
        Log.info("Starting simulation with: toa(ms) = {} | dc_silent(ms) = {} | frameGenerationRate = {}",
                timeOnAirMS, this.dutyCycleSilentTimeMS, this.frameGenerationRate);
    }

    public void setParams(double timeOnAirMS, double frameGenerationRate) {
        this.timeOnAirMS = timeOnAirMS;
        this.dutyCycleSilentTimeMS = (long) ((100.0 - dutyCyclePercentage) * timeOnAirMS);
        this.frameGenerationRate = frameGenerationRate;
    }

    public void simulateContinuousCoverage() {

        long start = simulation.getStartTimeUnix();
        long end = simulation.getEndTimeUnix();

        Log.warn("Mocking 100% coverage");
        List<Interval> intervals = new ArrayList<>();

        for (Position p : earthPositions) {
            for (Satellite s : satellites) {
                intervals.add(new Interval(start, end, p.getId(), s.getId()));
            }
        }

        accessIntervals = intervals;
        TrafficFactory tf = new TrafficFactory(this);
        transmissions = tf.generateTrafficPolicy3(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);

    }

    public void simulateTraffic() {
        accessIntervals = new ArrayList<>();
        simulateTraffic(null, false);
    }

    public void simulateTraffic(List<TimedMetricsRecord> backOffTimeSeries) {
        accessIntervals = new ArrayList<>();
        simulateTraffic(backOffTimeSeries, false);
    }

    public void simulateTraffic(List<TimedMetricsRecord> backOffTimeSeries, boolean limitDutyCycle) {
        accessIntervals = new ArrayList<>();
        if (lastSimHash != getSimHashCode() || lastSimHash == 0) {
            Log.debug("[Start] Computing access intervals");
            computeAccessIntervals();
            Log.debug("[End] Computing access intervals");
        }
        this.trafficFactory = new TrafficFactory(this); //TODO: add params to the constructor
        if (backOffTimeSeries != null) {
            trafficFactory.setBackOffTimeSeries(backOffTimeSeries);
            trafficFactory.setLimitDutyCycle(limitDutyCycle);
        } else {
            trafficFactory.setUseCoverageAwareness(false);
        }
        Log.debug("[Start] Generating traffic with policy 3");
        transmissions = trafficFactory.generateTrafficPolicy3(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);
        Log.debug("[End] Generating traffic with policy 3");
    }

    public void computeAccessIntervals() {

        checkScenarioParams();

        this.lastSimHash = getSimHashCode();
        List<Interval> allAccesses = new ArrayList<>();

        if (earthPositions.isEmpty() || satellites.isEmpty()) {
            Log.warn("Check assets! Earth Positions size: {} N° of satellites: {}", earthPositions.isEmpty(), satellites.isEmpty());
            System.exit(1);
        }

        // Compute intervals for each pair position -> satellite
        for (Position earthPos : earthPositions) {

            List<Interval> thisPositionIntervals = new ArrayList<>();

            for (Satellite satellite : satellites) {
                simulation = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, satellite, timeStepSeconds, visibilityThresholdDegrees);
                simulation.setInertialFrame(FramesFactory.getEME2000());
                simulation.setFixedFrame(FramesFactory.getITRF(IERSConventions.IERS_2010, true));
                simulation.computeAccess();
                thisPositionIntervals.addAll(simulation.getIntervals());
//                List<Interval> intervals = new ArrayList<>(simulation.getIntervals());
//                intervals.forEach(interval -> {
//                    simulation.computePVDBetween(interval.getStart(), interval.getEnd());
//                    thisPositionIntervals.add(new EnhancedInterval(interval, simulation.getEphemerisList()));
//                });

            }

            if (thisPositionIntervals.isEmpty()) {
                Log.debug("No access intervals for position ID: {}", earthPos.getId());
                continue;
            }

            thisPositionIntervals.sort(Comparator.comparingLong(Interval::getStart));

            if (satellites.size() == 1) {
                allAccesses.addAll(thisPositionIntervals);
                continue;
            }

            List<Interval> combinedIntervals = new ArrayList<>();
            Set<Integer> inContact = new LinkedHashSet<>();
            List<Event> eventList = intervals2events(thisPositionIntervals);

            Iterator<Event> eventIterator = eventList.stream()
                    .sorted(Comparator.comparingLong(Event::getTime)).iterator();
            eventIterator.next();

            var firstInterval = thisPositionIntervals.get(0);
            var currentInterval = new Interval(firstInterval.getStart(), firstInterval.getEnd(), firstInterval.getFromAssets(), firstInterval.getToAssets());
            inContact.add(currentInterval.getFirstTo());

            while (eventIterator.hasNext()) {
                Event event = eventIterator.next();
                if (!inContact.contains(event.getToID())) {  // If I establish contact with a new asset
                    inContact.add(event.getToID());
                    if (!currentInterval.getToAssets().isEmpty()) {
                        combinedIntervals.add(new Interval(currentInterval.getStart(), event.getTime(), currentInterval.getFromAssets(), currentInterval.getToAssets()));
                    }
                    currentInterval.addTo(event.getToID());
                } else {    // If the asset is already in contact
                    inContact.remove(event.getToID());
                    combinedIntervals.add(new Interval(currentInterval.getStart(), event.getTime(), currentInterval.getFromAssets(), currentInterval.getToAssets()));
                    currentInterval.removeTo(event.getToID());
                }
                currentInterval.setStart(event.getTime());
            }

            allAccesses.addAll(combinedIntervals);

        }

        accessIntervals = allAccesses;

    }

    /**
     * Transform intervals to events
     **/ //TODO: implement within computeMultiGateway, delete after implementation on computeMultiGateway
    private List<Event> intervals2events(List<Interval> intervals) {

        List<Event> eventsList = new ArrayList<>();

        for (Interval interval : intervals) {
            eventsList.add(new Event(interval.getStart(), interval.getFirstFrom(), interval.getFirstTo(), true));
            eventsList.add(new Event(interval.getEnd(), interval.getFirstFrom(), interval.getFirstTo(), false));
        }

        return eventsList;

    }

    public double getTimeOnAirMS() {
        return timeOnAirMS;
    }

    public void setTimeOnAirMS(double timeOnAirMS) {
        this.timeOnAirMS = timeOnAirMS;
    }

    public void setEarthPositions(List<Position> earthPositions) {
        this.earthPositions = earthPositions;
    }

    public void setSatellites(List<Satellite> satellites) {
        if (satellites.size() == 1) {
            Log.debug("N of Satellites = {} => Satellites == 1 => SG mode", satellites.size());
        } else {
            Log.debug("N of Satellites = {} => Satellites > 1 => MG mode", satellites.size());
        }
        Log.info("Satellite elements: {}", satellites.get(0).getElements());
        this.satellites = satellites;
    }

    public List<Transmission> getTransmissions() {
        return transmissions;
    }

    public List<Interval> getAccessIntervals() {
        return accessIntervals;
    }

    public void setSimulationInterval(String scenarioStartTime, String scenarioEndTime) {
        Log.debug("Simulation start unix timestamp: {} ", Utils.stamp2unix(scenarioStartTime));
        this.scenarioStartTime = scenarioStartTime;
        this.scenarioEndTime = scenarioEndTime;
        simulation.setStartTime(scenarioStartTime);
        simulation.setEndTime(scenarioEndTime);
    }

    public String getSimulationStartTime() {
        return scenarioStartTime;
    }

    public String getSimulationEndTime() {
        return scenarioEndTime;
    }

    public long getSimulationStartTimestamp() {
        return Utils.stamp2unix(scenarioStartTime);
    }

    public long getSimulationEndTimestamp() {
        return Utils.stamp2unix(scenarioEndTime);
    }

    public List<Position> getEarthPositions() {
        return earthPositions;
    }

    public List<Satellite> getSatellites() {
        return satellites;
    }

    public double getFrameGenerationRate() {
        return frameGenerationRate;
    }

    public void setFrameGenerationRate(double frameGenerationRate) {
        this.frameGenerationRate = frameGenerationRate;
    }

    public double getTimeStepSeconds() {
        return timeStepSeconds;
    }

    public void setTimeStepSeconds(double timeStepSeconds) {
        this.timeStepSeconds = timeStepSeconds;
    }

    public void setDutyCycleSilentTimeMS(long dutyCycleSilentTimeMS) {
        this.dutyCycleSilentTimeMS = dutyCycleSilentTimeMS;
    }

    private void checkScenarioParams() {
        if (timeStepSeconds == 0 || visibilityThresholdDegrees < 0
                || scenarioStartTime == null || scenarioEndTime == null) {
            String sb = "Time Step Seconds: " + timeStepSeconds + "\n" +
                    "Visibility Threshold Degrees: " + visibilityThresholdDegrees + "\n" +
                    "Scenario Start Time: " + scenarioStartTime + "\n" +
                    "Scenario End Time: " + scenarioEndTime + "\n";
            Log.error("Check scenario parameters! " + sb);
            System.exit(1);
        }
    }

    private int getSimHashCode() {

        // Combine the hash codes
        int combinedHash = satellites.hashCode() * 31 + scenarioStartTime.hashCode();
        combinedHash = combinedHash * 31 + scenarioEndTime.hashCode();
        combinedHash = combinedHash * 31 + earthPositions.hashCode();

        // Apply a hash function to the combined value
        return combinedHash ^ (combinedHash >>> 16);
    }

    public void setVisibilityThresholdDegrees(double visibilityThresholdDegrees) {
        this.visibilityThresholdDegrees = visibilityThresholdDegrees;
    }

    public Simulation getSimulation() {
        return this.simulation;
    }

    public TrafficFactory getTrafficFactory() {
        return this.trafficFactory;
    }


}


