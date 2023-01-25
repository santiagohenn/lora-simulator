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

    private final List<Interval> accessIntervals = new ArrayList<>();
    private List<Position> earthPositions = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();

    private double timeStepSeconds;
    private long timeOnAirMS;
    private long dutyCycleSilentTimeMS;
    private double frameGenerationRate;
    private String scenarioStartTime;
    private String scenarioEndTime;
    private Simulation sim;

    public Network() {

    }

    public Network(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        setParams(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);
    }

    public void setParams(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        this.timeStepSeconds = timeStepSeconds;
        this.timeOnAirMS = timeOnAirMS;
        this.dutyCycleSilentTimeMS = ((100 - dutyCyclePercentage) * timeOnAirMS);
        this.frameGenerationRate = frameGenerationRate;
    }

    public void simulateTraffic() {
        computeAccessIntervals();
        TrafficFactory tf = new TrafficFactory();
        transmissions = tf.generateTrafficPolicy2(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);
    }

    public void computeAccessIntervals() {

        accessIntervals.clear();
        sim = new Simulation();
        sim.setParams(scenarioStartTime, scenarioEndTime, timeStepSeconds, 25);
        Satellite sat = satellites.get(0);
        sim.setSatellite(sat);

        for (Position earthPos : earthPositions) {
            sim = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, sat, timeStepSeconds, 25);   // FIXME
            sim.setDevice(earthPos);
            sim.computeAccess();
            accessIntervals.addAll(sim.getIntervals());
        }

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
        Log.info("Simulation start unix timestamp: " + Utils.stamp2unix(scenarioStartTime));
        this.scenarioStartTime = scenarioStartTime;
        this.scenarioEndTime = scenarioEndTime;
    }

    public String getSimulationStartTime() {
        return scenarioStartTime;
    }

    public String getSimulationEndTime() {
        return scenarioEndTime;
    }

}
