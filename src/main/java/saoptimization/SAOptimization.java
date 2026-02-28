package saoptimization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;

public class SAOptimization {

    static final String OUTPUT_DIR = "C:/projects/results/sa_results/";

    // Decision-variable bounds
    private static final double H_MIN        = 500.0;  // km
    private static final double H_MAX        = 600.0;  // km
    private static final double I_MIN        = 80.0;   // deg
    private static final double I_MAX        = 100.0;  // deg
    private static final double ANOM_SEP_MIN = 1.0;    // deg
    private static final double ANOM_SEP_MAX = 15.0;   // deg
    private static final double P_MIN        = 30.0;   // dBm
    private static final double P_MAX        = 35.0;   // dBm

    // ISL link-budget constants
    private static final double ISL_FC_HZ     = 2.2e9;          // Frecuencia central (Hz)
    private static final double ISL_BW_HZ     = 265e6;          // Ancho de banda (Hz)
    private static final double ISL_NF_DB     = 2.5;            // Figura de ruido (dB)
    private static final double ISL_GT_DBI    = 8.0;            // Ganancia antena TX (dBi)
    private static final double ISL_GR_DBI    = 8.0;            // Ganancia antena RX (dBi)
    private static final double ISL_R_MAX_BPS = 7.5e6;          // Cap. hardware GomSpace (bps)
    private static final double C_VACUUM      = 299792458.0;    // Velocidad de la luz (m/s)

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {

        ConstellationCoverageComputer ccc = new ConstellationCoverageComputer(
                "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties");

        ccc.setROI(FileUtils.file2DoubleList(
                "C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv"));
        ccc.setOutputPath(OUTPUT_DIR);

        // x = [h (km), inc (deg), phase (deg), pTx (dBm)]
        FitnessEvaluator evaluator = x -> {
            double h     = x[0];
            double inc   = x[1];
            double phase = x[2];
            double pTx   = x[3];

            String hash      = runSimulation(ccc, h, inc, phase);
            double accessPct = readAccessPctFromFiles(OUTPUT_DIR + "coverage_" + hash + ".csv");
            double dataRatePct = computeDataRatePct(phase, h, pTx);

            deleteIfExists(OUTPUT_DIR + "coverage_" + hash + ".csv");
            deleteIfExists(OUTPUT_DIR + "surface_metrics_" + hash + ".json");

            // profit placeholder — SimulatedAnnealingOptimizer overwrites it
            return new Evaluation(accessPct, dataRatePct, 0.0);
        };

        double[] lo = { H_MIN, I_MIN, ANOM_SEP_MIN, P_MIN };
        double[] hi = { H_MAX, I_MAX, ANOM_SEP_MAX, P_MAX };

        SimulatedAnnealingOptimizer sa = new SimulatedAnnealingOptimizer(lo, hi, evaluator)
                .withT0(5.0)
                .withAlpha(0.995)
                .withStepFraction(0.025)
                .withMaxEvaluations(2_000)
                .withEarlyStopPatience(300)
                .withWeights(1.0, 1.0, 1.0)
                .withCsvOutput(OUTPUT_DIR + "sa_history.csv")
                .withSeed(42L);

        Result result = sa.run();

        double[] best = result.bestVector();
        Log.info(String.format(Locale.US,
                "[SA] Done. Evaluations: %d | Best profit: %.4f | accessPct: %.2f%% | dataRatePct: %.2f%%",
                result.totalEvaluations(),
                result.bestEvaluation().profit(),
                result.bestEvaluation().accessPct(),
                result.bestEvaluation().dataRatePct()));
        Log.info(String.format(Locale.US,
                "[SA] Best x: h=%.2f km, inc=%.2f deg, phase=%.2f deg, pTx=%.2f dBm",
                best[0], best[1], best[2], best[3]));
    }

    // =========================================================================
    // Simulation wiring and file parsing
    // =========================================================================

    private static String runSimulation(ConstellationCoverageComputer ccc,
                                        double h, double inc, double phase) {
        List<Satellite> sats = ccc.getSatelliteList();
        double startingAnomaly = sats.get(0).getElements().getAnomaly();
        for (int i = 0; i < sats.size(); i++) {
            sats.get(i).getElements().setAnomaly(startingAnomaly + i * phase);
            sats.get(i).getElements().setInclination(inc);
            sats.get(i).getElements().setSemiMajorAxis((6371 + h) * 1000.0);
        }
        ccc.run();
        return ccc.getSimulationHash();
    }

    private static double readAccessPctFromFiles(String filePath) {
        List<Double> averages = computeColumnAverages(filePath);
        if (averages.isEmpty()) return 0.0;
        return averages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static List<Double> computeColumnAverages(String filePath) {
        List<List<Double>> columnValues = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (columnValues.isEmpty()) {
                    for (int i = 1; i < parts.length; i++) columnValues.add(new ArrayList<>());
                }
                for (int i = 1; i < parts.length; i++) {
                    double val = Double.parseDouble(parts[i].trim());
                    if (val > 0.0) columnValues.get(i - 1).add(val);
                }
            }
        } catch (IOException e) {
            Log.info("Warning: could not read coverage file " + filePath + " — " + e.getMessage());
            return Collections.emptyList();
        }
        List<Double> averages = new ArrayList<>();
        for (List<Double> vals : columnValues) {
            averages.add(vals.isEmpty() ? 0.0
                    : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        }
        return averages;
    }

    private static void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists() && !f.delete()) {
            Log.info("SAOptimization: could not delete temp file " + path);
        }
    }

    // =========================================================================
    // Data-rate model (Shannon link budget)
    // =========================================================================

    /**
     * Computes normalised ISL data-rate in [0, 100] using Friis + Shannon.
     *
     * Friis:   P_r [dBm] = P_t [dBm] + G_t [dBi] + G_r [dBi] - L_fs [dB]
     * Noise:   P_n [dBm] = -174 + 10·log10(B) + NF
     * Shannon: R = B · log2(1 + SNR_linear)
     *
     * G_t and G_r are antenna gains in dBi (dimensionless power ratio, not dBm).
     * Mixing dBm and dBi in the Friis sum is correct: dBm + dBi + dBi - dB = dBm.
     */
    private static double computeDataRatePct(double anomalySepDeg, double heightKm, double pTx) {
        if (anomalySepDeg <= 0.0) return 0.0;
        double orbitRadiusM = (6371.0 + heightKm) * 1000.0;
        double distanceM    = 2.0 * orbitRadiusM * Math.sin(Math.toRadians(anomalySepDeg / 2.0));
        double lFsDb        = 20.0 * Math.log10(4.0 * Math.PI * distanceM * ISL_FC_HZ / C_VACUUM);
        double prDbm        = pTx + ISL_GT_DBI + ISL_GR_DBI - lFsDb;
        double pnDbm        = -174.0 + 10.0 * Math.log10(ISL_BW_HZ) + ISL_NF_DB;
        double snrLinear    = Math.pow(10.0, (prDbm - pnDbm) / 10.0);
        double rShannon     = ISL_BW_HZ * (Math.log(1.0 + snrLinear) / Math.log(2.0));
        return 100.0 * Math.max(0.0, Math.min(rShannon, ISL_R_MAX_BPS)) / ISL_R_MAX_BPS;
    }
}
