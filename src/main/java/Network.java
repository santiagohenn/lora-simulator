import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.TimeScalesFactory;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Device;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Network {

    private static final Logger Log = LogManager.getLogger(Network.class);
    private List<Device> devices = new ArrayList<>();
    private List<Satellite> satellites = new ArrayList<>();

    public static void main(String[] args) {

        Network network = new Network();
        network.loadDevicesFromFile("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\random_sites_N40_-31.6-31.6_-65.9_r7.1946.csv");
        network.loadSatellitesFromFile("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");
        network.obtainAccessIntervals();

    }

    public void obtainAccessIntervals() {

        Simulation sim = new Simulation();

        List<Interval> intervals = new ArrayList<>();

        sim.setParams("2022-01-01T15:00:00.000","2022-01-01T17:30:00.000",60.0, 25);

        Satellite sat = satellites.get(0);

        TLE tle = new TLE(9999 + sat.getId(), 'U', 2022, 1, "A",
                0, 999, Utils.stamp2AD("2022-01-01T16:00:00.000"),
                Math.sqrt(3.986004418E14D / Math.pow(sat.getElement("a"), 3.0D)),
                0, 0, 0, Math.toRadians(90), Math.toRadians(0)
                , Math.toRadians(278.3), Math.toRadians(280),
                1 , 0, TimeScalesFactory.getUTC());

        System.out.println(tle.getLine1() + '\n' + tle.getLine2());
        System.out.println(sat.getElements());

        for (Device device : devices) {
            sim = new Simulation("2022-01-01T15:00:00.000", "2022-01-01T17:30:00.000", device, sat, 60.0, 25);
            sim.computeAccess();
            intervals.addAll(sim.getIntervals());
        }

        intervals.forEach(i -> {
            System.out.println(i.getFirstFrom() + " to " + i.getFirstTo() + " -> " + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd()));
        });

    }



    public void loadDevicesFromFile(String fileName) {

        var file = new File(fileName);
        try (var fr = new FileReader(file); var br = new BufferedReader(fr)) {
            String line;
            int id = 0;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("//") && line.length() > 0) {
                    var data = line.split(",");
                    devices.add(new Device(id++, Double.parseDouble(data[0]), Double.parseDouble(data[1]), Double.parseDouble(data[2])));
                }
            }
        } catch (FileNotFoundException e) {
            Log.warn("Unable to find file: " + fileName);
            e.printStackTrace();
        } catch (IOException e) {
            Log.error("IOException: " + fileName);
            e.printStackTrace();
        }

    }

    public void loadSatellitesFromFile(String fileName) {

        var file = new File(fileName);
        try (var fr = new FileReader(file); var br = new BufferedReader(fr)) {
            String line;
            int id = 0;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("//") && line.length() > 0) {
                    var data = line.split(",");
                    satellites.add(new Satellite(id++, data[0], Double.parseDouble(data[1]), Double.parseDouble(data[2]), Double.parseDouble(data[3])
                            , Double.parseDouble(data[4]), Double.parseDouble(data[5]), Double.parseDouble(data[6])));
                }
            }
        } catch (FileNotFoundException e) {
            Log.warn("Unable to find file: " + fileName);
            e.printStackTrace();
        } catch (IOException e) {
            Log.error("IOException: " + fileName);
            e.printStackTrace();
        }

    }

}
