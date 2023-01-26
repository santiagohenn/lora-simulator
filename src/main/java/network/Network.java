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

    private List<Interval> accessIntervals;
    private List<Position> earthPositions;
    private List<Satellite> satellites;
    private List<Transmission> transmissions;

    private double timeStepSeconds;
    private long timeOnAirMS;
    private long dutyCycleSilentTimeMS;
    private double frameGenerationRate;
    private String scenarioStartTime;
    private String scenarioEndTime;
    private Simulation simulation;

    public Network(double timeStepSeconds, long timeOnAirMS, long dutyCyclePercentage, double frameGenerationRate) {
        setParams(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);
        simulation = new Simulation();
        simulation.setVisibilityThreshold(25);
        accessIntervals = new ArrayList<>();
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

        accessIntervals = new ArrayList<>();

        for (Position earthPos : earthPositions) {
            simulation = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, satellites.get(0), timeStepSeconds, 25);
            simulation.computeAccess();
            accessIntervals.addAll(simulation.getIntervals());
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

}
