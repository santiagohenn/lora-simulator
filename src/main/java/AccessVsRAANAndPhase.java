import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;

/**
 * Sweeps the RAAN and the in-plane anomaly separation of all satellites and records the mean ROI coverage.
 *
 * <p>For every (RAAN, anomaly separation) pair on a 1-degree grid, all satellites get the same RAAN and
 * anomalies {@code a_0 + k * separation}; coverage is computed with constellation-toolkit and, for each
 * coverage column, the mean over the time steps with non-zero coverage is stored. Output:
 * {@code <outputDir>/coverage_heatmap_raan<min>_<max>_anomalySep<min>_<max>.json}
 * (plot with {@code scripts/plot_heatmap_raan_and_phase.py}).
 *
 * <p>Usage: {@code AccessVsRAANAndPhase [config.properties] [roi.csv] [outputDir]}. The baseline
 * satellites are read from the {@code satellites_file} entry of the configuration file.
 */
public class AccessVsRAANAndPhase {

    static final String DEFAULT_CONFIG = "config/optimization_argentina_eez.properties";
    static final String DEFAULT_ROI = "inputs/rois/argentina_eez.csv";
    static final String DEFAULT_OUTPUT_DIR = "results/access_vs_raan_and_phase/";

    public static void main(String[] args) {

        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG;
        String roiPath = args.length > 1 ? args[1] : DEFAULT_ROI;
        final String OUTPUT_DIR = withTrailingSlash(args.length > 2 ? args[2] : DEFAULT_OUTPUT_DIR);
        new File(OUTPUT_DIR).mkdirs();

        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(configPath);

        // Load from file
        List<double[]> ROI = FileUtils.file2DoubleList(roiPath);
        constellationCoverageComputer.setROI(ROI);

        constellationCoverageComputer.setOutputPath(OUTPUT_DIR);
        List<Satellite> satelliteList = constellationCoverageComputer.getSatelliteList();

        double maxAnomalySep = 15;
        double minAnomalySep = 1;
        double maxRAAN = 10;
        double minRAAN = -10;
        double startingAnomaly = satelliteList.get(0).getElements().getAnomaly();

        List<Map<String, Object>> results = new ArrayList<>();

        for (double raan = minRAAN; raan <= maxRAAN; raan += 1.0) {

            for (double anomalySep = minAnomalySep; anomalySep <= maxAnomalySep; anomalySep += 1.0) {

                for (int satN = 0; satN < satelliteList.size(); satN++) {
                    constellationCoverageComputer.getSatelliteList().get(satN).getElements()
                            .setAnomaly(startingAnomaly + satN * anomalySep);
                    constellationCoverageComputer.getSatelliteList().get(satN).getElements()
                            .setRightAscension(raan);
                }

                constellationCoverageComputer.run();
                String hash = constellationCoverageComputer.getSimulationHash();

                List<Double> averages = computeColumnAverages(OUTPUT_DIR + "coverage_" + hash + ".csv");

                Map<String, Object> entry = new HashMap<>();
                entry.put("raan", raan);
                entry.put("anomalySep", anomalySep);
                entry.put("averages", averages);
                results.add(entry);
                new File(OUTPUT_DIR + "coverage_" + hash + ".csv").delete();
                new File(OUTPUT_DIR + "surface_metrics_" + hash + ".json").delete();
            }
        }

        String outputName = String.format(Locale.US, "coverage_heatmap_raan%.0f_%.0f_anomalySep%.0f_%.0f.json",
                minRAAN, maxRAAN, minAnomalySep, maxAnomalySep);
        writeJson(OUTPUT_DIR + outputName, results, minRAAN, maxRAAN, minAnomalySep, maxAnomalySep);
        Log.info("Results written to " + OUTPUT_DIR + outputName);
    }

    static String withTrailingSlash(String path) {
        return (path.endsWith("/") || path.endsWith("\\")) ? path : path + "/";
    }

    /**
     * Reads a coverage CSV and returns the mean access percentage per satellite
     * column, averaging only over non-zero timesteps.
     * Column count is determined from the first row of the file.
     */
    private static List<Double> computeColumnAverages(String filePath) {
        List<List<Double>> columnValues = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split(",");
                // Initialise one list per data column on the first row
                if (columnValues.isEmpty()) {
                    for (int i = 1; i < parts.length; i++) {
                        columnValues.add(new ArrayList<>());
                    }
                }
                // parts[0] is the timestamp; parts[1..] are coverage percentages
                for (int i = 1; i < parts.length; i++) {
                    double val = Double.parseDouble(parts[i].trim());
                    if (val > 0.0) {
                        columnValues.get(i - 1).add(val);
                    }
                }
            }
        } catch (IOException e) {
            Log.info("Warning: could not read coverage file " + filePath + " — " + e.getMessage());
            return Collections.emptyList();
        }

        List<Double> averages = new ArrayList<>();
        for (List<Double> vals : columnValues) {
            double avg = vals.isEmpty() ? 0.0
                    : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            averages.add(avg);
        }
        return averages;
    }

    /** Writes the accumulated results as a JSON file. */
    private static void writeJson(String filePath, List<Map<String, Object>> results,
            double minRAAN, double maxRAAN, double minAnomalySep, double maxAnomalySep) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"metadata\": {\n");
        sb.append("    \"minRaan\": ").append(minRAAN).append(",\n");
        sb.append("    \"maxRaan\": ").append(maxRAAN).append(",\n");
        sb.append("    \"minAnomalySep\": ").append(minAnomalySep).append(",\n");
        sb.append("    \"maxAnomalySep\": ").append(maxAnomalySep).append(",\n");
        sb.append("    \"step\": 1.0\n");
        sb.append("  },\n");
        sb.append("  \"results\": [\n");

        for (int r = 0; r < results.size(); r++) {
            Map<String, Object> entry = results.get(r);
            @SuppressWarnings("unchecked")
            List<Double> averages = (List<Double>) entry.get("averages");

            sb.append("    {");
            sb.append("\"raan\": ").append(entry.get("raan")).append(", ");
            sb.append("\"anomalySep\": ").append(entry.get("anomalySep")).append(", ");
            sb.append("\"averages\": [");
            for (int i = 0; i < averages.size(); i++) {
                sb.append(String.format(Locale.US, "%.6f", averages.get(i)));
                if (i < averages.size() - 1)
                    sb.append(", ");
            }
            sb.append("]}");
            if (r < results.size() - 1)
                sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            Log.info("Error writing JSON output: " + e.getMessage());
        }
    }
}
