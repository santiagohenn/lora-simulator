import assets.Transmission;
import generators.PositionsFactory;
import generators.TrafficFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.TimeScalesFactory;
import generators.ReportFactory;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.structures.OrbitalElements;
import satellite.tools.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class Network {

    private static final Logger Log = LogManager.getLogger(Network.class);

    private List<Position> earthPositions = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Interval> intervals = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();
    private List<String> packetStatistics = new ArrayList<>();

    private long timeOnAirMS = 10;
    private long dutyCyclePercentage = 1;
    private long dutyCycleSilentTimeMS = ((100 - dutyCyclePercentage) * timeOnAirMS);
    private double frameGenerationRate = (1.0 / (100.0 * 1000.0)) * 100.0; // (1/99) / 100

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
        rg.saveAsJSON(intervals.stream().map(interval -> new long[]{interval.getFirstFrom(), interval.getStart(), interval.getEnd()})
            .collect(Collectors.toList()), "intervals_" + name);

    }

    public void analyzeTraffic() {

        packetStatistics.clear();

        // Sort transmissions by time
        transmissions.sort(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp));

//        System.out.println("### Transmissions ###");
//        transmissions.forEach(System.out::println);
//        transmissions.forEach(t -> System.out.println(Utils.unix2stamp(t.getStartTransmissionTimestamp()) + " -> " + t));

        Deque<Transmission> transmissionsSuccessful = new ArrayDeque<>();
        TreeSet<Transmission> collisions = new TreeSet<>();
        Iterator<Transmission> transmissionIterator = transmissions.iterator();

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

        long debugCorruptCount = transmissions.stream().filter(Transmission::isLost).count();
        long debugSuccessCount = transmissions.stream().filter(t -> !t.isLost()).count();

        Log.info("debugCorruptCount: " + debugCorruptCount);
        Log.info("debugSuccessCount: " + debugSuccessCount);

        System.out.println("Total transmissions: " + transmissions.size());
        System.out.println("Successful transmissions: " + transmissionsSuccessful.size());
        System.out.println("Collided transmissions (tx - successful): " + (transmissions.size() - transmissionsSuccessful.size()));
        System.out.println("Collided transmissions (TreeSet): " + collisions.size());

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
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime)).count();

            successfulPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime
                            && !t.isLost())).count();

            collidedPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime
                            && t.getLostReason() == 1)).count();

            Log.debug(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);
            packetStatistics.add(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);

            epochTimeMS += timestepMS;

        }
    }

    public void generateTraffic() {

        TrafficFactory tf = new TrafficFactory();
        transmissions = tf.generateTrafficPolicy1(intervals, frameGenerationRate, timeOnAirMS, dutyCycleSilentTimeMS);

    }

    public void obtainAccessIntervals() {

        intervals.clear();

        Simulation sim = new Simulation();

        sim.setParams(scenarioStartTime, scenarioEndTime, 60.0, 25);
        Satellite sat = satellites.get(0);

        for (Position earthPos : earthPositions) {
            sim = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, sat, 60.0, 25);
            sim.computeAccess();
            intervals.addAll(sim.getIntervals());
        }

//        Log.info("### SATELLITE INFO ###");
//        printSatelliteInfo(sat);
//        Log.info("### POSITIONS INFO ###");
//        earthPositions.forEach(p -> Log.info(p.getLat() + "," + p.getLon()));
//        System.out.println("### ACCESS INTERVALS ###");
//        intervals.forEach(i -> Log.info(i.getFirstFrom() + " to " + i.getFirstTo() + " -> "
//                + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd())));
//        Log.info(Collections.max(intervals, (i1, i2) -> i1.getDuration() >= i2.getDuration() ? 1 : -1));

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

    public void printSatelliteInfo(Satellite sat) {
        OrbitalElements oe = sat.getElements();
        TLE tle = new TLE(9999 + sat.getId(), 'U', 2022, 1, "A",
                0, 999, Utils.stamp2AD("2022-01-01T16:00:00.000"),
                Math.sqrt(3.986004418E14D / Math.pow(sat.getElement("a"), 3.0D)),
                0, 0, 0, oe.getInclinationRads(), oe.getArgOfPerigeeRads()
                , oe.getRightAscensionRads(), oe.getAnomalyRads(),
                1, 0, TimeScalesFactory.getUTC());

        System.out.println(tle.getLine1() + '\n' + tle.getLine2());
        System.out.println(sat.getElements());
    }

    public void setEarthPositions(List<Position> earthPositions) {
        this.earthPositions = earthPositions;
    }
}
