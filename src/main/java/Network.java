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

import java.util.ArrayList;
import java.util.List;

public class Network {

    private static final Logger Log = LogManager.getLogger(Network.class);
    private List<Position> earthPositions = new ArrayList<>();
    private List<IOTDevice> devices = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();
    private List<Interval> intervals = new ArrayList<>();
    private List<Transmission> transmissions = new ArrayList<>();

    public static void main(String[] args) {

        Network network = new Network();
        network.loadAssets("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\random_sites_N40_-31.6-31.6_-65.9_r7.1946.csv",
                "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");
        network.obtainAccessIntervals();
        network.populateDevices();
        network.generateTraffic();

    }

    public void populateDevices() {



    }

    public void generateTraffic() {

        long timeStepMilliSeconds = 100;

        long clock = 0;

        for (Interval i : intervals) {

            for (long ts = i.getStart() ; ts < i.getEnd() ; ts += timeStepMilliSeconds) {

                clock += timeStepMilliSeconds;

                if (clock >= 1000) {
                    clock = 0;
                    transmissions.add(new Transmission(ts, i.getFirstFrom(), i.getFirstTo(), 1));
                }

            }

        }

        System.out.println("### TRANSMISSIONS ###");
        transmissions.forEach(System.out::println);

    }

    public void obtainAccessIntervals() {

        Simulation sim = new Simulation();

        sim.setParams("2022-01-01T15:00:00.000","2022-01-01T17:30:00.000",60.0, 25);

        Satellite sat = satellites.get(0);

        for (Position earthPos : earthPositions) {
            sim = new Simulation("2022-01-01T15:00:00.000", "2022-01-01T17:30:00.000",
                    earthPos, sat, 60.0, 25);
            sim.computeAccess();
            intervals.addAll(sim.getIntervals());
        }

        printSatelliteInfo(sat);
        intervals.forEach(i -> {
            System.out.println(i.getFirstFrom() + " to " + i.getFirstTo() + " -> "
                    + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd()));
        });

    }

    public void loadAssets(String positionsPath, String satellitesPath) {

        satellites = Utils.satellitesFromFile(satellitesPath);
        earthPositions = Utils.positionsFromFile(positionsPath);


    }

    public void printSatelliteInfo(Satellite sat) {
        OrbitalElements oe = sat.getElements();
        System.out.println(oe.getInclination());
        TLE tle = new TLE(9999 + sat.getId(), 'U', 2022, 1, "A",
                0, 999, Utils.stamp2AD("2022-01-01T16:00:00.000"),
                Math.sqrt(3.986004418E14D / Math.pow(sat.getElement("a"), 3.0D)),
                0, 0, 0, oe.getInclinationRads(), oe.getArgOfPerigeeRads()
                , oe.getRightAscensionRads(), oe.getAnomalyRads(),
                1 , 0, TimeScalesFactory.getUTC());

        System.out.println(tle.getLine1() + '\n' + tle.getLine2());
        System.out.println(sat.getElements());
    }

}
