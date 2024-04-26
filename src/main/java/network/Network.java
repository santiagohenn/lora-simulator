package network;

import assets.Transmission;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.orekit.frames.FramesFactory;
import org.orekit.utils.IERSConventions;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;
import structures.EnhancedInterval;

import java.util.*;

public class Network {

    static final Logger Log = LogManager.getLogger(Network.class);

    private List<Interval> accessIntervals;
    private List<Position> earthPositions;
    private List<Satellite> satellites;
    private List<Transmission> transmissions;

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
        Log.info("Starting simulation with: toa(ms) = " + timeOnAirMS
                + " | dc_silent(ms) = " + this.dutyCycleSilentTimeMS
                + " | frameGenerationRate = " + this.frameGenerationRate);
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
        if (lastSimHash != getSimHashCode() || lastSimHash == 0) {
            Log.debug("[Start] Computing access intervals");
            computeAccessIntervals();
            Log.debug("[End] Computing access intervals");
        }
        TrafficFactory tf = new TrafficFactory(this); //TODO: add params to the constructor
        Log.debug("[Start] Generating traffic with policy 3");
        transmissions = tf.generateTrafficPolicy3(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);
        Log.debug("[End] Generating traffic with policy 3");
    }

    public void computeAccessIntervals() {

        this.lastSimHash = getSimHashCode();
        List<Interval> allAccesses = new ArrayList<>();

        if (earthPositions.isEmpty() || satellites.isEmpty()) {
            Log.warn("Check assets! Earth Positions size: " + earthPositions.isEmpty() + " N° of satellites: " + satellites.isEmpty());
            return;
        }

        // Compute intervals for each pair position -> satellite
        for (Position earthPos : earthPositions) {

            List<Interval> thisPositionIntervals = new ArrayList<>();

            for (Satellite satellite : satellites) {
                simulation = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, satellite, timeStepSeconds, visibilityThresholdDegrees); // TODO: do not hardcode th
                simulation.setInertialFrame(FramesFactory.getEME2000());
                simulation.setFixedFrame(FramesFactory.getITRF(IERSConventions.IERS_2010, true));
                simulation.computeAccess();

                List<Interval> intervals = new ArrayList<>(simulation.getIntervals());
                intervals.forEach(interval -> {
                    simulation.computePVDBetween(interval.getStart(), interval.getEnd());
                    thisPositionIntervals.add(new EnhancedInterval(interval, simulation.getEphemerisList()));
                });

            }

            if (thisPositionIntervals.isEmpty()) {
                Log.warn("No access intervals for position ID: " + earthPos.getId());
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
                if (!inContact.contains(event.toID)) {  // If I establish contact with a new asset
                    inContact.add(event.toID);
                    if (!currentInterval.getToAssets().isEmpty()) {
                        combinedIntervals.add(new Interval(currentInterval.getStart(), event.getTime(), currentInterval.getFromAssets(), currentInterval.getToAssets()));
                    }
                    currentInterval.addTo(event.toID);
                } else {    // If the asset is already in contact
                    inContact.remove(event.toID);
                    combinedIntervals.add(new Interval(currentInterval.getStart(), event.getTime(), currentInterval.getFromAssets(), currentInterval.getToAssets()));
                    currentInterval.removeTo(event.toID);
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
            Log.warn("N of Satellites = " + satellites.size() + " => Satellites == 1 => SG mode");
        } else {
            Log.warn("N of Satellites = " + satellites.size() + " => Satellites > 1 => MG mode");
        }
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
        return simulation;
    }

    class Event implements Comparable<Event> {

        private final long time;
        private final int fromID;
        private final int toID;
        private final boolean type; // 1: gain contact, 0: lost contact

        public Event(long time, int fromID, int toID, boolean type) {
            this.time = time;
            this.fromID = fromID;
            this.toID = toID;
            this.type = type;
        }

        public long getTime() {
            return time;
        }

        public int getFromID() {
            return fromID;
        }

        public int getToID() {
            return toID;
        }

        public boolean gainedContact() {
            return type;
        }

        @Override
        public String toString() {
            return time + "," + fromID + "," + toID;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Event) {
                Event objAsEvent = (Event) obj;
                return objAsEvent.getFromID() == this.fromID && objAsEvent.getToID() == this.toID;
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(@NotNull Event o) {
            return Long.compare(this.time, o.time);
        }

    }


}


