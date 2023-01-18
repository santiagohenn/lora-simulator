import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.TimeScalesFactory;
import reports.ReportGenerator;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.structures.OrbitalElements;
import satellite.tools.utils.Utils;

import java.util.*;

public class Network {

    private static final Logger Log = LogManager.getLogger(Network.class);

    private List<Position> earthPositions = new ArrayList<>();
    private List<IOTDevice> devices = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Interval> intervals = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();
    private List<String> packetStatistics = new ArrayList<>();

    private long timeOnAirMS = 1000;
    private double dutyCyclePercentage = 100;
    private double dutyCycleSilentTimeMS = (100 - dutyCyclePercentage) * timeOnAirMS;
    private double frameGenerationRate = (1.0 / (100.0 * 1000.0)); // (1/99) / 100

    private String scenarioStartTime = "2022-01-01T16:00:00.000";
    private String scenarioEndTime = "2022-01-01T16:30:00.000";

    public static void main(String[] args) {

        Network network = new Network();
        network.loadSatellites("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");
        PositionsGenerator pg = new PositionsGenerator();

        for (int nodes = 10; nodes <= 50; nodes += 10) {
            Log.info("Simulating for " + nodes + " nodes");
            pg.setParams(nodes, -31.6, -65.9, 7.1946);
            network.setEarthPositions(pg.generatePositions());
            network.obtainAccessIntervals();
//            network.populateIOTDevices();
            network.generateTraffic();
            network.analyzeTraffic();
            network.savePacketStatisticsToFile("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\", nodes + "N");
        }

    }

    public void savePacketStatisticsToFile(String path, String name) {

        ReportGenerator rg = new ReportGenerator(path);
        rg.saveAsCSV(packetStatistics, name);
        rg.saveAsJSON(transmissions, "TX_" + name);

    }

    public void analyzeTraffic() {

        packetStatistics.clear();

        // Sort transmissions by time
        transmissions.sort(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp));

//        System.out.println("### Transmissions ###");
//        transmissions.forEach(System.out::println);
//        transmissions.forEach(t -> System.out.println(Utils.unix2stamp(t.getStartTransmissionTimestamp()) + " -> " + t));

        Deque<Transmission> transmissionsSuccessful = new ArrayDeque<>();
        Deque<Transmission> transmissionsCollided = new ArrayDeque<>();
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
                if (!transmissionInGateway.isCorrupt()) {
                    transmissionsSuccessful.addLast(transmissionInGateway);
                }

                // If this was the last transmission and does not collide, it reaches the server
                if (!transmissionIterator.hasNext()) {
                    transmissionsSuccessful.addLast(nextTransmission);
                }

            } else {    // If transmissions collide both are discarded
                transmissionInGateway.setCorrupt(true);
                nextTransmission.setCorrupt(true);
                collisions.add(transmissionInGateway);
                collisions.add(nextTransmission);
            }

            transmissionInGateway = nextTransmission;

        }

        long debugCorruptCount = transmissions.stream().filter(Transmission::isCorrupt).count();
        long debugSuccessCount = transmissions.stream().filter(t -> !t.isCorrupt()).count();

        Log.info("debugCorruptCount: " + debugCorruptCount);
        Log.info("debugSuccessCount: " + debugSuccessCount);

        System.out.println("Total transmissions: " + transmissions.size());
        System.out.println("Successful transmissions: " + transmissionsSuccessful.size());
        System.out.println("Collided transmissions (tx - successful): " + (transmissions.size() - transmissionsSuccessful.size()));
        System.out.println("Collided transmissions (TreeSet): " + collisions.size());

//        System.out.println("### Collisions ###");
//        collisions.forEach(System.out::println);

        long timestepMS = 100000;
        long epochTimeMS = 0;
        long successfulPackets;
        long collidedPackets;
        long totalPackets;

        for (long time = Utils.stamp2unix(scenarioStartTime) + timestepMS; time <= Utils.stamp2unix(scenarioEndTime); time += timestepMS) {

            long currentTime = time;

//            successfulPackets = transmissionsSuccessful.stream()
//                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime)).count();
//
//            collidedPackets = collisions.stream()
//                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime)).count();

            totalPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime)).count();

            successfulPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime
                            && !t.isCorrupt())).count();

            collidedPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - timestepMS) && t.getStartTransmissionTimestamp() < currentTime
                            && t.isCorrupt())).count();

            Log.debug(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);
            packetStatistics.add(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);

            epochTimeMS += timestepMS;

        }

    }

    public void generateTraffic() {

        Log.debug("duty cycle silent time (ms) - " + dutyCycleSilentTimeMS);
        Log.debug("time on air (ms) - " + timeOnAirMS);

        for (Interval i : intervals) {

            long timeMS = i.getStart();

            while (timeMS < i.getEnd()) {

                long nextTransmissionAfterMS = generatePoissonEvent(frameGenerationRate);
                long startTransmissionTimestamp = timeMS + nextTransmissionAfterMS;
                long endTransmissionTimestamp = timeMS + timeOnAirMS;

                // Transmit
                Transmission tx = new Transmission(startTransmissionTimestamp, i.getFirstFrom(), i.getFirstTo(), 1);
                tx.setEndTransmissionTimestamp(endTransmissionTimestamp);
                transmissions.add(tx);

                // Advance time - packet time on air
                timeMS = endTransmissionTimestamp + timeOnAirMS;

                // Advance time - duty cycle
                timeMS += dutyCycleSilentTimeMS;

            }

        }

    }

    public long generatePoissonEvent(double rate) {

        Random r = new Random();
        double nextEvent = -Math.log(1.0 - r.nextDouble()) / rate;
        return (long) nextEvent;

    }

    public void populateIOTDevices() {

        earthPositions.forEach(position -> {
            Transmisor tx = new Transmisor(position.getId());
            devices.add(position.getId(), new IOTDevice(tx, position));
        });

    }

    public void obtainAccessIntervals() {

        Simulation sim = new Simulation();

        sim.setParams(scenarioStartTime, scenarioEndTime, 60.0, 25);
        Satellite sat = satellites.get(0);

        for (Position earthPos : earthPositions) {
            sim = new Simulation(scenarioStartTime, scenarioEndTime, earthPos, sat, 60.0, 25);
            sim.computeAccess();
//            sim.getIntervals().forEach(interval -> interval.setFirstFrom(earthPos.getId()));
            intervals.addAll(sim.getIntervals());
        }

//        System.out.println("### SATELLITE INFO ###");
//        printSatelliteInfo(sat);
//        System.out.println("### POSITIONS INFO ###");
//        earthPositions.forEach(p -> System.out.println(p.getLat() + "," + p.getLon()));
//        System.out.println("### ACCESS INTERVALS ###");
//        intervals.forEach(i -> System.out.println(i.getFirstFrom() + " to " + i.getFirstTo() + " -> "
//                + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd())));
//        System.out.println(Collections.max(intervals, (i1, i2) -> i1.getDuration() >= i2.getDuration() ? 1 : -1));

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
