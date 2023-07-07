package network;

import assets.Transmission;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;

import java.util.*;

public class Network {

    static final Logger Log = LogManager.getLogger(Network.class);

    private List<Interval> accessIntervals;
    private List<Position> earthPositions;
    private List<Satellite> satellites;
    private List<Transmission> transmissions;

    private double timeStepSeconds;
    private long timeOnAirMS;
    private long dutyCycleSilentTimeMS;
    private double frameGenerationRatePacketsPerSecond;
    private String scenarioStartTime;
    private String scenarioEndTime;
    private Simulation simulation;

    public Network(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRatePacketsPerSecond) {
        setParams(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRatePacketsPerSecond);
        simulation = new Simulation();
        simulation.setVisibilityThreshold(25);
        accessIntervals = new ArrayList<>();
    }

    public void setParams(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        this.timeStepSeconds = timeStepSeconds;
        this.timeOnAirMS = timeOnAirMS;
        this.dutyCycleSilentTimeMS = (((100 - dutyCyclePercentage) * timeOnAirMS) - timeOnAirMS); // TODO REMOVE -timeOnAirMS
        this.frameGenerationRatePacketsPerSecond = frameGenerationRate;
    }

    public void simulateTraffic() {
        if (accessIntervals.isEmpty()) {
            Log.info("Computing access intervals");
            computeAccessIntervals();
        }
        TrafficFactory tf = new TrafficFactory(this); //TODO: add params to the constructor
        transmissions = tf.generateTrafficPolicy3(accessIntervals, frameGenerationRatePacketsPerSecond, timeOnAirMS, dutyCycleSilentTimeMS);
    }

    public void computeAccessIntervals() {

        boolean includeCoverageGaps = false;

        List<Interval> allAccesses = new ArrayList<>();

        if (earthPositions.isEmpty() || satellites.isEmpty()) {
            Log.warn("Check assets!");
            return;
        }

        for (Position earthPos : earthPositions) {

            accessIntervals = new ArrayList<>();

            for (Satellite satellite : satellites) {
                simulation = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, satellite, timeStepSeconds, 25); // TODO: do not hardcode th
                simulation.computeAccess();
                accessIntervals.addAll(simulation.getIntervals());
            }

            accessIntervals.sort((i1, i2) -> (int) (i1.getStart() - i2.getStart()));

            if (satellites.size() == 1) {
                allAccesses.addAll(accessIntervals);
                continue;
            }

            List<Interval> combinedIntervals = new ArrayList<>();
            Set<Integer> inContact = new LinkedHashSet<>();
            List<Event> eventList = intervals2events(accessIntervals);
            eventList = eventList.subList(1, eventList.size()); // TODO: replace with iterator

            var currentInterval = accessIntervals.get(0);
            inContact.add(currentInterval.getFirstTo());

            for (Event event : eventList) {
                if (!inContact.contains(event.toID)) {  // If I establish contact with a new asset
                    inContact.add(event.toID);
                    if (includeCoverageGaps || !currentInterval.getToAssets().isEmpty()) {
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
            eventsList.add(new Event(interval.getStart(), interval.getFirstFrom(), interval.getFirstTo()));
            eventsList.add(new Event(interval.getEnd(), interval.getFirstFrom(), interval.getFirstTo()));
        }

        eventsList.sort((e1, e2) -> (int) (e1.getTime() - e2.getTime()));
        return eventsList;

    }

    public void setEarthPositions(List<Position> earthPositions) {
        this.earthPositions = earthPositions;
    }

    public void setSatellites(List<Satellite> satellites) {
        this.satellites = satellites;
    }

    public List<Transmission> getTransmissions() {
        return transmissions;
    }

    public List<Interval> getAccessIntervals() {
        return accessIntervals;
    }

    public void setSimulationInterval(String scenarioStartTime, String scenarioEndTime) {
        Log.info("Simulation start unix timestamp: {} ", Utils.stamp2unix(scenarioStartTime));
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

    public List<Position> getEarthPositions() {
        return earthPositions;
    }

    public List<Satellite> getSatellites() {
        return satellites;
    }

    class Event implements Comparable<Event> {

        private final long time;
        private final int fromID;
        private final int toID;

        public Event(long time, int fromID, int toID) {
            this.time = time;
            this.fromID = fromID;
            this.toID = toID;
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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Event) {
                Event objAsEvent = (Event) obj;
                return objAsEvent.getFromID() == this.fromID && objAsEvent.getToID() == this.toID ;
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


