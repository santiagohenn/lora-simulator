package pareto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.ConstellationHash;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

public class Pareto {

    static final String OUTPUT_DIR = "C:/projects/results/pareto/";

    // Decision-variable bounds
    private static final double H_MIN = 500.0; // km
    private static final double H_MAX = 600.0; // km
    private static final double I_MIN = 80.0; // deg
    private static final double I_MAX = 100.0; // deg
    private static final double ANOM_SEP_MIN = 1.0; // deg
    private static final double ANOM_SEP_MAX = 15.0; // deg
    private static final double P_MIN = 30.0; // dBm
    private static final double P_MAX = 35.0; // dBm
    private static final double P_NOMINAL = 32.0; // dBm
    private static final double H_NOMINAL = 550.0; // km
    private static final double I_NOMINAL = 97.0356; // deg

    // RAAN sweep
    private static final double RAAN_MIN = -10.0;
    private static final double RAAN_MAX = 10.0;

    // Column index in coverage CSV (1 = first satellite column)
    private static final int ACCESS_COL = 1;

    // ISL link-budget constants
    private static final double ISL_FC_HZ = 2.2e9; // Hz
    private static final double ISL_BW_HZ = 265e6; // Hz
    private static final double ISL_NF_DB = 2.5; // dB
    private static final double ISL_GT_DBI = 8.0; // dBi
    private static final double ISL_GR_DBI = 8.0; // dBi
    private static final double ISL_R_MAX_BPS = 7.5e6; // bps (GomSpace hardware cap)

    // Steps
    private static final double ANOM_SEP_STEP = 0.5; // deg
    private static final double I_STEP = 0.5; // deg
    private static final double RAAN_STEP = 1.0; // deg
    private static final double P_STEP = 1.0; // dB

    // CONSTANTS
    private static final double C_VACUUM = 299792458.0; // m/s
    private static final double EARTH_RADIUS_KM = 6371.0; // km

    private static final String SATELLITE_FILE = "C:/Projects/lora-simulator/inputs/satellites/constellation_baseline.csv";

    // Capture startingAnomaly once from a fresh load
    private static final double STARTING_ANOMALY = Utils.satellitesFromFile(SATELLITE_FILE).get(0).getElements()
            .getAnomaly();

    public static void main(String[] args) {

        // // TODO: avoid TLE caching in ConstellationCoverageComputer — currently we
        // have to create a new instance for each run to ensure the updated TLEs are
        // picked up.
        // ConstellationCoverageComputer ccc = new ConstellationCoverageComputer(
        // "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties");

        // // Load from file
        // List<double[]> ROI =
        // FileUtils.file2DoubleList("C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv");
        // ccc.setOutputPath(OUTPUT_DIR);
        // ccc.setROI(ROI);

        // List<Satellite> satelliteList = Utils
        // .satellitesFromFile(SATELLITE_FILE);
        // ccc.setSatelliteList(satelliteList);

        BufferedWriter csv = openCsv(OUTPUT_DIR + "pareto_front.csv");

        for (double anomalySep = ANOM_SEP_MIN; anomalySep <= ANOM_SEP_MAX; anomalySep += ANOM_SEP_STEP) {

            for (double inc = I_MIN; inc <= I_MAX; inc += I_STEP) {

                double avgAccess = runRAANsweep(STARTING_ANOMALY, anomalySep, inc);

                for (double power = P_MIN; power <= P_MAX; power += P_STEP) {

                    double dataRatePct = computeDataRatePct(anomalySep, H_NOMINAL, power);
                    writeCsvRow(csv, anomalySep, inc, power, dataRatePct, avgAccess);

                }

            }

        }

        closeCsv(csv);

    }

    private static double runRAANsweep(double startingAnomaly, double anomalySep, double inc) {

        double accessSum = 0.0;
        int count = 0;

        for (double raan = RAAN_MIN; raan <= RAAN_MAX; raan += RAAN_STEP) {

            ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(
                    "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties");

            // Load from file
            List<double[]> ROI = FileUtils.file2DoubleList("C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv");
            constellationCoverageComputer.setOutputPath("C:/projects/results/pareto/");
            constellationCoverageComputer.setROI(ROI);

            List<Satellite> satelliteList = Utils
                    .satellitesFromFile("C:/Projects/lora-simulator/inputs/satellites/constellation_baseline.csv");

            for (int satIdx = 0; satIdx < satelliteList.size(); satIdx++) {
                satelliteList.get(satIdx).getElements()
                        .setAnomaly(startingAnomaly + satIdx * anomalySep);
                satelliteList.get(satIdx).getElements().setRightAscension(raan);
                satelliteList.get(satIdx).getElements().setInclination(inc);
            }

            constellationCoverageComputer.setSatelliteList(satelliteList);
            String hash = ConstellationHash.hash2(satelliteList);

            // TODO: Check if file exists before running the simulation, to avoid redundant runs for identical configurations
            if (!fileExists(OUTPUT_DIR + "coverage_" + hash + ".csv")) {
                constellationCoverageComputer.run();
                hash = constellationCoverageComputer.getSimulationHash(); // get the actual hash after the run, in case it differs from the pre-run hash due to TLE caching or other factors
            }

            //String hash = constellationCoverageComputer.getSimulationHash();

            // String hash = runSimulation(ccc, H_NOMINAL, I_NOMINAL, anomalySep, raan,
            // startingAnomaly);

            double thisRunAccess = readAccessPct(OUTPUT_DIR + "coverage_" + hash + ".csv");
            accessSum += thisRunAccess;
            count++;

            // deleteIfExists(OUTPUT_DIR + "coverage_" + hash + ".csv");
            deleteIfExists(OUTPUT_DIR + "surface_metrics_" + hash + ".json");

        }

        return count > 0 ? accessSum / count : 0.0;
    }

    private static String runSimulation(ConstellationCoverageComputer ccc,
            double h, double inc, double anomalySeparation, double raan, double startingAnomaly) {

        // Always build a fresh list — avoids any stale TLE or cached state
        List<Satellite> sats = Utils.satellitesFromFile(SATELLITE_FILE);
        ccc.setSatelliteList(null);
        for (int satIdx = 0; satIdx < sats.size(); satIdx++) {
            // ccc.getSatelliteList().get(satIdx).setTLE("", ""); // force TLE regeneration
            // from elements
            sats.get(satIdx).getElements().setAnomaly(startingAnomaly + satIdx * anomalySeparation);
            sats.get(satIdx).getElements().setRightAscension(raan);
            // sats.get(satIdx).getElements().setInclination(inc);
            // sats.get(satIdx).getElements().setSemiMajorAxis((EARTH_RADIUS_KM + h) *
            // 1000.0);
        }
        ccc.setSatelliteList(sats);
        ccc.run();
        return ccc.getSimulationHash();
    }

    // Reads the average non-zero access percentage from column ACCESS_COL of the
    // CSV.
    private static double readAccessPct(String filePath) {
        double accessSum = 0.0;
        double accessIntervalN = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split(",");
                if (parts.length <= ACCESS_COL)
                    continue;
                double val = Double.parseDouble(parts[ACCESS_COL].trim());
                accessSum += val;
                accessIntervalN++;
            }
        } catch (IOException e) {
            Log.info("Warning: could not read " + filePath + ": " + e.getMessage());
        }
        return accessIntervalN == 0 ? 0.0 : accessSum / accessIntervalN;
    }

    private static void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists() && !f.delete())
            Log.info("Could not delete temp file: " + path);
    }

    private static boolean fileExists(String path) {
        File f = new File(path);
        return f.exists();
    }

    // =========================================================================
    // ISL data-rate model (Friis + Shannon)
    // Friis: P_r = P_t + G_t + G_r - L_fs [dBm + dBi + dBi - dB = dBm]
    // Noise: P_n = -174 + 10·log10(B) + NF [dBm]
    // Shannon: R = B · log2(1 + SNR_linear)
    // Returns normalised data rate in [0, 100].
    // =========================================================================

    private static double computeDataRatePct(double anomalySepDeg, double heightKm, double pTx) {
        if (anomalySepDeg <= 0.0)
            return 0.0;
        double R_m = (6371.0 + heightKm) * 1000.0;
        double dist = 2.0 * R_m * Math.sin(Math.toRadians(anomalySepDeg / 2.0));
        double lFs = 20.0 * Math.log10(4.0 * Math.PI * dist * ISL_FC_HZ / C_VACUUM);
        double pr = pTx + ISL_GT_DBI + ISL_GR_DBI - lFs;
        double pn = -174.0 + 10.0 * Math.log10(ISL_BW_HZ) + ISL_NF_DB;
        double snr = Math.pow(10.0, (pr - pn) / 10.0);
        double r = ISL_BW_HZ * (Math.log(1.0 + snr) / Math.log(2.0));
        double dataRate = 100.0 * Math.max(0.0, Math.min(r, ISL_R_MAX_BPS)) / ISL_R_MAX_BPS;
        return dataRate > ISL_R_MAX_BPS ? ISL_R_MAX_BPS : dataRate;
    }

    // ── CSV ──────────────────────────────────────────────────────────────────

    private static final String CSV_HEADER = "anomaly_sep_deg,inclination_deg,power_db,data_rate_pct,avg_access_pct";

    private static void writeCsvRow(BufferedWriter w, double anomalySep, double inclination,
            double power, double dataRatePct, double avgAccess) {
        if (w == null)
            return;
        try {
            w.write(String.format(Locale.US,
                    "%.2f,%.2f,%.4f,%.4f,%.4f%n", anomalySep, inclination, power, dataRatePct, avgAccess));
        } catch (IOException e) {
            System.err.println("[SA] CSV write error: " + e.getMessage());
        }
    }

    private static BufferedWriter openCsv(String filePath) {
        if (filePath == null)
            return null;
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(filePath));
            bw.write(CSV_HEADER);
            bw.newLine();
            return bw;
        } catch (IOException e) {
            System.err.println("[SA] Cannot open CSV: " + e.getMessage());
            return null;
        }
    }

    private static void closeCsv(BufferedWriter w) {
        if (w == null)
            return;
        try {
            w.close();
        } catch (IOException ignored) {
        }
    }

}
