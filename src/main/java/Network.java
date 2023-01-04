import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.TimeScalesFactory;
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

    private long timeOnAirMS = 1000;
    private double dutyCyclePercentage = 1;
    private double dutyCycleSilentTimeMS = (100 - dutyCyclePercentage) * timeOnAirMS;

    public static void main(String[] args) {

        Network network = new Network();
        network.loadAssets("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\random_sites_N40_-31.6-31.6_-65.9_r7.1946.csv",
                "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");
        network.obtainAccessIntervals();
        network.populateIOTDevices();
        network.generateTraffic();
        network.analyzeTraffic();

    }

    public void analyzeTraffic() {

        // Sort transmissions by time
        transmissions.sort((t1, t2) -> t1.getStartTransmissionTimestamp() >= t2.getStartTransmissionTimestamp() ? 1 : -1);
//        transmissions.forEach(t -> System.out.println(Utils.unix2stamp(t.getStartTransmissionTimestamp()) + " -> " + t));

        Deque<Transmission> transmissionsSuccessful = new ArrayDeque<>();
        Deque<Transmission> transmissionsCollided = new ArrayDeque<>();

        Transmission transmissionInGateway = transmissions.get(0);

        Iterator<Transmission> transmissionIterator = transmissions.iterator();
        transmissionIterator.next();

        while (transmissionIterator.hasNext()) {

            Transmission nextTransmission = transmissionIterator.next();

            if (nextTransmission.getStartTransmissionTimestamp() >= transmissionInGateway.getEndTransmissionTimestamp()) {
                transmissionsSuccessful.addLast(transmissionInGateway);
                transmissionInGateway = nextTransmission;
            } else {
                transmissionsCollided.addLast(transmissionInGateway);
                transmissionsCollided.addLast(nextTransmission);
            }

        }

        System.out.println("Successful transmissions: " + transmissionsSuccessful.size());
        System.out.println("Collided transmissions: " + transmissionsCollided.size());


    }

    public void generateTraffic() {

        for (Interval i : intervals) {

            long timeMS = i.getStart();

            while (timeMS < i.getEnd()) {

                long nextTransmissionAfterMS = generatePoissonEvent(1.0 / (100.0 * 1000.0));
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

        sim.setParams("2022-01-01T15:00:00.000", "2022-01-01T17:30:00.000", 60.0, 25);

        Satellite sat = satellites.get(0);

        for (Position earthPos : earthPositions) {
            sim = new Simulation("2022-01-01T15:00:00.000", "2022-01-01T17:30:00.000",
                    earthPos, sat, 60.0, 25);
            sim.computeAccess();
            intervals.addAll(sim.getIntervals());
        }

//        System.out.println("### SATELLITE INFO ###");
//        printSatelliteInfo(sat);
//        System.out.println("### ACCESS INTERVALS ###");
//        intervals.forEach(i -> System.out.println(i.getFirstFrom() + " to " + i.getFirstTo() + " -> "
//                + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd())));

    }

    public void loadAssets(String positionsPath, String satellitesPath) {
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

}
