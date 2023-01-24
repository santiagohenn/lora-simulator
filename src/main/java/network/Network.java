package network;

import assets.Transmission;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Network {

    static final Logger Log = LogManager.getLogger(Network.class);
    private List<Position> earthPositions = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Interval> accessIntervals = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();
    private List<String> packetStatistics = new ArrayList<>();

    private double timeStepSeconds;
    private long timeOnAirMS = 1000;
    private long dutyCyclePercentage = 1;
    private long dutyCycleSilentTimeMS = ((100 - dutyCyclePercentage) * timeOnAirMS);
    private double frameGenerationRate = (1.0 / (100.0 * 1000.0)); // (1/99) / 100

    private String scenarioStartTime = "2022-01-01T16:00:00.000";
    private String scenarioEndTime = "2022-01-01T16:30:00.000";

    public Network() {

    }

    public Network(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        setParams(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);
        Log.info("Simulation start timestamp: " + Utils.stamp2unix(scenarioStartTime));
    }

    public void setParams(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        this.timeStepSeconds = timeStepSeconds;
        this.timeOnAirMS = timeOnAirMS;
        this.dutyCyclePercentage = dutyCyclePercentage;
        this.frameGenerationRate = frameGenerationRate;
    }

    public void simulateTraffic() {
        computeAccessIntervals();
        TrafficFactory tf = new TrafficFactory();
        transmissions = tf.generateTrafficPolicy2(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);
    }

    public void computeAccessIntervals() {

        accessIntervals.clear();

        Simulation sim = new Simulation();

        sim.setParams(scenarioStartTime, scenarioEndTime, timeStepSeconds, 25);
        Satellite sat = satellites.get(0);

        for (Position earthPos : earthPositions) {
            sim = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, sat, 60.0, 25);
            sim.computeAccess();
            accessIntervals.addAll(sim.getIntervals());
        }

    }

    public void loadPositions(String positionsPath, String satellitesPath) {
        loadSatellites(satellitesPath);
        loadEarthPositions(positionsPath);
    }

    public void loadSatellites(String satellitesPath) {
        satellites = Utils.satellitesFromFile(satellitesPath);
    }

    public void loadEarthPositions(String positionsPath) {
        earthPositions = Utils.positionsFromFile(positionsPath);
    }

    public void setEarthPositions(List<Position> earthPositions) {
        this.earthPositions = earthPositions;
    }

    public List<Transmission> getTransmissions() {
        return transmissions;
    }

    public String getScenarioStartTime() {
        return scenarioStartTime;
    }

    public String getScenarioEndTime() {
        return scenarioEndTime;
    }

    public List<Interval> getAccessIntervals() {
        return accessIntervals;
    }

}
