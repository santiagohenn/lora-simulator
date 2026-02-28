package deoptimization;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Real black-box evaluator that wraps {@link ConstellationCoverageComputer}.
 *
 * <h3>How accessPct is computed</h3>
 * For a given decision vector {@code x = [h, inc, anomalySep, pTx]} the
 * evaluator sweeps
 * a configurable range of RAAN values (Right Ascension of Ascending Node) in
 * even
 * increments — exactly mirroring the loop in {@code AccessVsRAANAndPhase}. For
 * each
 * RAAN band it:
 * <ol>
 * <li>Applies {@code h}, {@code inc}, {@code raan}, and anomalySep-spaced
 * anomalies to
 * every satellite in the coverage computer's list.</li>
 * <li>Runs the simulator and reads the output {@code coverage_*.csv}.</li>
 * <li>Computes the mean non-zero access percentage per satellite column.</li>
 * <li>Averages those per-satellite values into a single band coverage
 * figure.</li>
 * </ol>
 * The final {@code accessPct} is the mean of all RAAN-band figures.
 *
 * <h3>How dataRatePct is computed</h3>
 * Delegated to {@link #computeDataRatePct(double, double)}, which is a
 * placeholder.
 * Replace its body with your ISL link-budget formula. The method intentionally
 * receives only {@code anomalySep} and {@code h} (as stated by the user);
 * {@code pTx} is
 * available as an instance field if you need to forward it.
 *
 * <h3>Concurrency</h3>
 * Not thread-safe. The {@link ConstellationCoverageComputer} is shared across
 * calls. The DE optimizer is single-threaded by default, so this is safe.
 *
 * <h3>Temp-file hygiene</h3>
 * {@code coverage_*.csv} and {@code surface_metrics_*.json} are deleted after
 * each
 * RAAN-band run, matching the cleanup pattern in {@code AccessVsRAANAndPhase}.
 */
public class ConstellationSimEvaluator implements FitnessEvaluator {

    // =========================================================================
    // Configuration
    // =========================================================================

    /** Absolute path to the output directory used by the coverage computer. */
    private final String outputDir;

    /** Minimum RAAN value (degrees) in the averaging sweep. */
    private final double raanMin;

    /** Maximum RAAN value (degrees) in the averaging sweep. */
    private final double raanMax;

    /** Step between successive RAAN values (degrees). */
    private final double raanStep;

    /**
     * Which column of the coverage CSV to use as the access metric.
     * Column index is 1-based: 1 = "access towards at least 1 satellite",
     * 2 = "access towards at least 2 satellites simultaneously", etc.
     *
     * <p>Default is 1. Increase to reward configurations where multiple
     * satellites cover the ROI simultaneously (diversity / redundancy).
     * Note: values > number of satellites in the constellation will fall back
     * to the last available column.
     */
    private final int targetSatelliteCount;

    // =========================================================================
    // ISL link-budget constants ← TODO: replace with values from your paper
    // =========================================================================

    /** ISL carrier centre frequency (Hz). */
    private static final double ISL_FC_HZ = 2.4e9; // TODO

    /** ISL channel bandwidth (Hz). */
    private static final double ISL_BW_HZ = 1.0e6; // TODO

    /** Receiver noise figure (dB). */
    private static final double ISL_NF_DB = 5.0; // TODO

    /** Transmit antenna gain (dBi); antennas are assumed body-pointed. */
    private static final double ISL_GT_DBI = 10.0; // TODO

    /** Receive antenna gain (dBi). */
    private static final double ISL_GR_DBI = 10.0; // TODO

    /** Hardware data-rate cap (bps) — 7.5 Mbps per GomSpace datasheet. */
    private static final double ISL_R_MAX_BPS = 7.5e6;

    // =========================================================================

    /**
     * The coverage computer is constructed once and its satellite list is mutated
     * in-place on every call to {@link #evaluate(double[])}, exactly as done in
     * {@code AccessVsRAANAndPhase}.
     */
    private final ConstellationCoverageComputer coverageComputer;

    /**
     * Number of satellites in the constellation (read once at construction time
     * from the coverage computer's loaded list).
     */
    private final int numSatellites;

    /**
     * Mean anomaly of the first satellite as loaded from the baseline CSV.
     * Used as the reference offset when distributing anomalySep across the
     * constellation:
     * {@code anomaly_i = startingAnomaly + i * anomalySepDeg}.
     */
    private final double startingAnomaly;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Convenience constructor that defaults {@code targetSatelliteCount} to 1
     * (coverage towards at least 1 satellite).
     */
    public ConstellationSimEvaluator(
            String configPath,
            String roiPath,
            String outputDir,
            double raanMin,
            double raanMax,
            double raanStep) {
        this(configPath, roiPath, outputDir, raanMin, raanMax, raanStep, 1);
    }

    /**
     * Full constructor.
     *
     * @param targetSatelliteCount which "to N satellites" column to use as the
     *                             access metric (1-based). 1 = coverage towards at
     *                             least 1 satellite (default), 2 = at least 2
     *                             simultaneously, etc. Values larger than the
     *                             number of columns are clamped to the last column.
     */
    public ConstellationSimEvaluator(
            String configPath,
            String roiPath,
            String outputDir,
            double raanMin,
            double raanMax,
            double raanStep,
            int targetSatelliteCount) {

        this.outputDir = outputDir.endsWith("/") || outputDir.endsWith("\\")
                ? outputDir
                : outputDir + "/";
        this.raanMin = raanMin;
        this.raanMax = raanMax;
        this.raanStep = raanStep;
        this.targetSatelliteCount = Math.max(1, targetSatelliteCount);

        // ── Initialise coverage computer (loads Orekit, baseline satellites, etc.) ──
        this.coverageComputer = new ConstellationCoverageComputer(configPath);

        // ── Set ROI (loaded separately so the path is explicit, not buried in config)
        // ──
        List<double[]> roi = FileUtils.file2DoubleList(roiPath);
        this.coverageComputer.setROI(roi);
        this.coverageComputer.setOutputPath(this.outputDir);

        // ── Capture baseline satellite count and reference anomaly ──
        List<Satellite> sats = this.coverageComputer.getSatelliteList();
        if (sats == null || sats.isEmpty()) {
            throw new IllegalStateException(
                    "ConstellationCoverageComputer loaded 0 satellites from config '"
                            + configPath + "'. Check the satellites_file property.");
        }
        this.numSatellites = sats.size();
        this.startingAnomaly = sats.get(0).getElements().getAnomaly();

        Log.info(String.format(Locale.US,
                "ConstellationSimEvaluator ready: %d satellites, RAAN sweep [%.1f, %.1f] step %.1f deg",
                numSatellites, raanMin, raanMax, raanStep));
    }

    // =========================================================================
    // FitnessEvaluator implementation
    // =========================================================================

    /**
     * Evaluates one decision vector by sweeping RAAN bands and averaging coverage.
     *
     * @param x {@code [h_km, inc_deg, anomalySep_deg, pTx_dBm]} — all within
     *          configured bounds
     * @return {@link Evaluation} with {@code accessPct} (RAAN-averaged coverage)
     *         and
     *         {@code dataRatePct} (from placeholder {@link #computeDataRatePct})
     */
    @Override
    public Evaluation evaluate(double[] x) {

        double heightInKm = x[0]; // satellite altitude
        double inc = x[1]; // orbital inclination (degrees)
        double anomalySep = x[2]; // inter-satellite anomaly separation (degrees)
        double pTx = x[3]; // ISL TX power (dBm — or whatever unit your link model uses)

        try {
            // ── RAAN sweep: accumulate per-band averages ───────────────────────
            double accessSum = 0.0;
            int bands = 0;

            for (double raan = raanMin; raan <= raanMax + 1e-9; raan += raanStep) {

                // ── Apply this DE individual's orbital parameters to every satellite ──
                for (int satN = 0; satN < numSatellites; satN++) {

                    Satellite sat = coverageComputer.getSatelliteList().get(satN);

                    // ── Height / semi-major axis ───────────────────────────────────────
                    // VERIFY: check your satellite.tools version for the exact setter name.
                    // Common alternatives: setAltitude(km), setSemiMajorAxis(km), setHeight(km)
                    sat.getElements().setSemiMajorAxis((heightInKm + 6371.0) * 1000.0);

                    // ── Inclination ───────────────────────────────────────────────────
                    // VERIFY: check your satellite.tools version for the exact setter name.
                    // Common alternatives: setInclination(deg), setI(deg)
                    sat.getElements().setInclination(inc);

                    // ── RAAN and anomaly separation — identical pattern to AccessVsRAANAndPhase ──
                    sat.getElements().setRightAscension(raan);
                    sat.getElements().setAnomaly(startingAnomaly + satN * anomalySep);

                }

                // ── Run simulator for this RAAN band ──────────────────────────
                coverageComputer.run();
                String hash = coverageComputer.getSimulationHash();

                // ── Read and average the coverage output ───────────────────────
                String coverageCsv = outputDir + "coverage_" + hash + ".csv";
                List<Double> colAverages = readCoverageColumnAverages(coverageCsv);

                if (!colAverages.isEmpty()) {
                    // Pick the column for "access to at least targetSatelliteCount satellites"
                    // (1-based → 0-based index, clamped to available columns)
                    int colIdx = Math.min(targetSatelliteCount - 1, colAverages.size() - 1);
                    accessSum += colAverages.get(colIdx);
                }
                // Bands with empty coverage (no visible timesteps) contribute 0 implicitly

                // ── Clean up temp files — same pattern as AccessVsRAANAndPhase ──
                deleteIfExists(outputDir + "coverage_" + hash + ".csv");
                deleteIfExists(outputDir + "surface_metrics_" + hash + ".json");

                bands++;
            }

            // ── Final access metric: mean across all RAAN bands ────────────────
            double accessPct = bands > 0 ? accessSum / bands : 0.0;

            // ── Data-rate metric ──────────────────────────────
            double dataRatePct = computeDataRatePct(anomalySep, heightInKm, pTx);

            return new Evaluation(x, accessPct, dataRatePct);

        } catch (Exception e) {
            Log.info("ConstellationSimEvaluator: evaluation failed. x=" + Arrays.toString(x)
                    + "  reason=" + e.getMessage());
            return Evaluation.failed(x, e.getMessage());
        }
    }

    // =========================================================================
    // Data-rate model (Shannon link budget)
    // =========================================================================

    /**
     * Computes the normalised ISL data-rate percentage using a Shannon link-budget
     * model.
     *
     * <p>
     * Steps:
     * <ol>
     * <li>ISL chord distance: {@code d = 2·r·sin(anomalySepDeg/2)} where
     * {@code r = R_earth + h}.</li>
     * <li>Free-space path loss: {@code L_fs = 20·log10(4·π·d·f_c / c)}.</li>
     * <li>Received power: {@code P_r = P_t + G_t + G_r - L_fs} (dBm).</li>
     * <li>Noise power: {@code P_n = -174 + 10·log10(B) + NF} (dBm).</li>
     * <li>Shannon capacity: {@code R_sha = B·log2(1 + SNR_linear)}.</li>
     * <li>Cap at hardware maximum: {@code R = min(R_sha, R_max)}.</li>
     * <li>Normalise: {@code return 100 · R / R_max}.</li>
     * </ol>
     *
     * <p>
     * Link-budget constants ({@code ISL_FC_HZ}, {@code ISL_BW_HZ},
     * {@code ISL_NF_DB},
     * {@code ISL_GT_DBI}, {@code ISL_GR_DBI}) are marked TODO and must be filled in
     * from your paper's link parameter table.
     *
     * @param anomalySepDeg inter-satellite anomaly separation in degrees (decision
     *                      variable x[2])
     * @param heightKm      satellite altitude in km (decision variable x[0])
     * @param pTx           ISL TX power in dBm (decision variable x[3])
     * @return normalised data rate in {@code [0, 100]}
     */
    private double computeDataRatePct(double anomalySepDeg, double heightKm, double pTx) {

        if (anomalySepDeg <= 0.0)
            return 0.0;

        // ── 1. ISL chord distance between adjacent satellites (m) ─────────────
        // For a circular orbit at altitude h, the orbit radius is r = R_earth + h.
        // Two satellites separated by anomalySepDeg are at chord distance:
        // d = 2 · r · sin(anomalySepDeg / 2)
        double orbitRadiusM = (6371.0 + heightKm) * 1000.0;
        double distanceM = 2.0 * orbitRadiusM
                * Math.sin(Math.toRadians(anomalySepDeg / 2.0));

        // ── 2. Free-space path loss (dB) ─────────────────────────────────────
        // L_fs = 20·log10(4·π·d·f_c / c)
        double lFsDb = 20.0 * Math.log10(4.0 * Math.PI * distanceM * ISL_FC_HZ / 3e8);

        // ── 3. Received power (dBm) ───────────────────────────────────────────
        // P_r = P_t + G_t + G_r - L_fs
        double prDbm = pTx + ISL_GT_DBI + ISL_GR_DBI - lFsDb;

        // ── 4. Thermal noise power (dBm) ─────────────────────────────────────
        // P_n = -174 + 10·log10(B) + NF (kTB at 290 K → -174 dBm/Hz)
        double pnDbm = -174.0 + 10.0 * Math.log10(ISL_BW_HZ) + ISL_NF_DB;

        // ── 5. SNR (linear) ──────────────────────────────────────────────────
        double snrLinear = Math.pow(10.0, (prDbm - pnDbm) / 10.0);

        // ── 6. Shannon capacity (bps) ─────────────────────────────────────────
        // R_sha = B · log2(1 + SNR)
        double rShannon = ISL_BW_HZ * (Math.log(1.0 + snrLinear) / Math.log(2.0));

        // ── 7. Cap at hardware maximum and normalise to [0, 100] ─────────────
        double r = Math.min(rShannon, ISL_R_MAX_BPS);
        return 100.0 * Math.max(0.0, r) / ISL_R_MAX_BPS;
    }

    // =========================================================================
    // CSV parsing — adapted directly from AccessVsRAANAndPhase
    // =========================================================================

    /**
     * Reads a coverage CSV produced by {@link ConstellationCoverageComputer} and
     * returns
     * the mean non-zero access percentage for each satellite column.
     *
     * <p>
     * File layout (mirrors {@code AccessVsRAANAndPhase.computeColumnAverages}):
     * 
     * <pre>
     *   timestamp, sat0_coverage, sat1_coverage, ..., satN_coverage
     *   1500000,   0.0,           12.3,          ..., 8.7
     *   ...
     * </pre>
     * 
     * Only timesteps with coverage {@code > 0} contribute to the per-column mean
     * (matches the original logic; zero rows represent no-coverage intervals).
     *
     * @param filePath absolute path to the {@code coverage_*.csv} file
     * @return list of per-column means, one entry per satellite;
     *         empty list if the file cannot be read
     */
    private static List<Double> readCoverageColumnAverages(String filePath) {
        List<List<Double>> columnValues = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] parts = line.split(",");

                // Initialise column buckets on the first data row
                if (columnValues.isEmpty()) {
                    for (int i = 1; i < parts.length; i++) {
                        columnValues.add(new ArrayList<>());
                    }
                }

                // parts[0] = timestamp; parts[1..] = "to N satellites" coverage %
                // Only accumulate non-zero rows (conditional mean).  A timestep
                // with 0 % area coverage means the satellite is not overhead at
                // that instant; including it would dilute the metric down to a
                // tiny fraction and lose the optimiser's signal.  Zero-access
                // configurations are handled separately by the -2.0 penalty in
                // the scalarisation layer.
                for (int i = 1; i < parts.length; i++) {
                    try {
                        double val = Double.parseDouble(parts[i].trim());
                        if (val > 0.0) {
                            columnValues.get(i - 1).add(val);
                        }
                    } catch (NumberFormatException ignored) {
                        // skip non-numeric cells (header row)
                    }
                }
            }
        } catch (IOException e) {
            Log.info("ConstellationSimEvaluator: could not read '" + filePath + "': " + e.getMessage());
            return Collections.emptyList();
        }

        List<Double> averages = new ArrayList<>(columnValues.size());
        for (List<Double> col : columnValues) {
            double avg = col.isEmpty()
                    ? 0.0
                    : col.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            averages.add(avg);
        }
        return averages;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists() && !f.delete()) {
            Log.info("ConstellationSimEvaluator: could not delete temp file " + path);
        }
    }
}
