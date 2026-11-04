package generators;

import assets.Transmission;
import com.google.gson.Gson;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.TimeScalesFactory;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Interval;
import satellite.tools.structures.OrbitalElements;
import satellite.tools.utils.Utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ReportFactory {

    public static final String JSON_EXTENSION = ".json";
    public static final String CSV_EXTENSION = ".csv";
    private final String outputPath;

    public ReportFactory(String outputPath) {
        if (!outputPath.endsWith("/")) {
            outputPath = outputPath + "/";
        }
        this.outputPath = outputPath;
        new java.io.File(outputPath).mkdirs();
    }

    /**
     * Saves an Object to a JSON interpretation
     *
     * @param obj  the object to be stored
     * @param name the name of the file
     */
    public void saveAsJSON(Object obj, String name) {
        Gson gson = new Gson();
        String json = gson.toJson(obj);
        saveString(json, outputPath + name + JSON_EXTENSION);
    }

    /**
     * Saves a String List to a new file, each entry as a line
     *
     * @param entryList the String to be stored
     * @param name      the name to the file
     */
    public boolean saveAsCSV(List<String> entryList, String name) {

        try (FileWriter writer = new FileWriter(outputPath + name + CSV_EXTENSION)) {
            for (String entry : entryList) {
                writer.write(entry + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Saves a String to a file depicted in path
     *
     * @param entry the String to be stored
     * @param path  the path to the file
     */
    public boolean saveString(String entry, String path) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(entry);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Saves a String List to a file depicted in path
     *
     * @param entries the String to be stored
     * @param name    the path to the file
     */
    public boolean saveAs(List<String> entries, String name) {
        try (FileWriter writer = new FileWriter(outputPath + name)) {
            for (String entry : entries) {
                writer.write(entry + '\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void printTransmissions(List<Transmission> transmissions) {
        System.out.println("### Transmissions ###");
        transmissions.forEach(System.out::println);
        transmissions.forEach(t -> System.out.println(Utils.unix2stamp((long) t.getStartTransmissionTimestamp()) + " -> " + t));
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

    public void printPositionsInfo(List<Position> positions) {
        positions.forEach(p -> System.out.println(p.getLat() + "," + p.getLon()));
    }

    public void printIntervals(List<Interval> intervals) {
        System.out.println("### ACCESS INTERVALS ###");
        intervals.forEach(i -> System.out.println(i.getFirstFrom() + " to " + i.getFirstTo() + " -> "
                + Utils.unix2stamp(i.getStart()) + "," + Utils.unix2stamp(i.getEnd())));
        System.out.println(Collections.max(intervals, (i1, i2) -> i1.getDuration() >= i2.getDuration() ? 1 : -1));
    }

}

