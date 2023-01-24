import assets.Transmission;
import generators.PositionsFactory;
import generators.ReportFactory;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class Network {

    private static final Logger Log = LogManager.getLogger(Network.class);

    private List<Position> earthPositions = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Interval> accessIntervals = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();
    private List<String> packetStatistics = new ArrayList<>();

    private long timeStep;
    private long timeOnAirMS = 1000;
    private long dutyCyclePercentage = 1;
    private long dutyCycleSilentTimeMS = ((100 - dutyCyclePercentage) * timeOnAirMS);
    private double frameGenerationRate = (1.0 / (100.0 * 1000.0)); // (1/99) / 100

    private String scenarioStartTime = "2022-01-01T16:00:00.000";
    private String scenarioEndTime = "2022-01-01T16:30:00.000";

    public static void main(String[] args) {

        Network network = new Network();
        network.loadSatellites("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");
        PositionsFactory pg = new PositionsFactory();

        for (int nodes = 10; nodes <= 100; nodes += 10) {
            Log.info("Simulating for " + nodes + " nodes");
            pg.setParams(nodes, -31.6, -65.9, 7.1946);
            network.setEarthPositions(pg.generateUniformRandomPositions());
            network.obtainAccessIntervals();
            network.generateTraffic();
            network.analyzeTraffic();
            network.saveData("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\", nodes + "N");
        }

    }

    public Network() {
        Log.info("Simulation start timestamp: " + Utils.stamp2unix(scenarioStartTime));
    }

    public void saveData(String path, String name) {

        ReportFactory rg = new ReportFactory(path);
        rg.saveAsCSV(packetStatistics, name);
        rg.saveAsJSON(transmissions, "TX_" + name);
        rg.saveAsJSON(accessIntervals.stream().map(interval -> new long[]{interval.getFirstFrom(), interval.getStart(), interval.getEnd()})
            .collect(Collectors.toList()), "intervals_" + name);

    }

    public void analyzeTraffic() {

        packetStatistics.clear();

        // Filter transmissions that where sent to space and sort them by time
        List<Transmission> transmissionsSent = transmissions.stream().filter(t -> !t.isLost())
                .sorted(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp))
                .collect(Collectors.toList());

        Deque<Transmission> transmissionsSuccessful = new ArrayDeque<>();
        TreeSet<Transmission> collisions = new TreeSet<>();
        Iterator<Transmission> transmissionIterator = transmissionsSent.iterator();

        // Take the first transmission
        Transmission transmissionInGateway = transmissionIterator.next();

        // While there are other transmissions
        while (transmissionIterator.hasNext()) {

            // Take the next transmission
            Transmission nextTransmission = transmissionIterator.next();

            // If this transmission does not collide with the previous one
            if (nextTransmission.getStartTransmissionTimestamp() >= transmissionInGateway.getEndTransmissionTimestamp()) {

                // It means it successfully reached the server
                if (!transmissionInGateway.isLost()) {
                    transmissionsSuccessful.addLast(transmissionInGateway);
                }

                // If this was the last transmission and does not collide, it reaches the server
                if (!transmissionIterator.hasNext()) {
                    transmissionsSuccessful.addLast(nextTransmission);
                }

            } else {    // If transmissions collide both are discarded
                transmissionInGateway.setLostReason(1);
                nextTransmission.setLostReason(1);
                collisions.add(transmissionInGateway);
                collisions.add(nextTransmission);
            }

            transmissionInGateway = nextTransmission;

        }

        long debugCollisionCount = transmissions.stream().filter(t -> t.getLostReason() == 1).count();
        long debugSuccessCount = transmissions.stream().filter(t -> !t.isLost()).count();

        Log.debug("debugCollisionCount: " + debugCollisionCount);
        Log.debug("debugSuccessCount: " + debugSuccessCount);
        Log.debug("Total transmissions: " + transmissions.size());
        Log.debug("Successful transmissions: " + transmissionsSuccessful.size());
        Log.debug("Collided transmissions (tx - successful): " + (transmissions.size() - transmissionsSuccessful.size()));
        Log.debug("Collided transmissions (TreeSet): " + collisions.size());

//        System.out.println("### Collisions ###");
//        collisions.forEach(System.out::println);

        long timestepMS = 50000; // 100000;
        long epochTimeMS = 0;
        long successfulPackets;
        long collidedPackets;
        long totalPackets;

        for (long time = Utils.stamp2unix(scenarioStartTime) + timestepMS; time <= Utils.stamp2unix(scenarioEndTime); time += timestepMS) {

            long currentTime = time;

            totalPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS)
                            && t.getStartTransmissionTimestamp() < currentTime)).count();

            successfulPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && !t.isLost())).count();

            collidedPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && t.getLostReason() == 1)).count();

            Log.debug(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);
            packetStatistics.add(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);

            epochTimeMS += timestepMS;

        }
    }

    public void generateTraffic() {

        TrafficFactory tf = new TrafficFactory();
        transmissions = tf.generateTrafficPolicy2(accessIntervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);

    }

    public void obtainAccessIntervals() {

        accessIntervals.clear();

        Simulation sim = new Simulation();

        sim.setParams(scenarioStartTime, scenarioEndTime, 60.0, 25);
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

}
