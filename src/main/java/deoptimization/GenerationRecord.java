package deoptimization;
import java.util.Arrays;
import java.util.Locale;

/**
 * Snapshot of the DE state at the end of one generation.
 *
 * <p>Stored in an in-memory list by {@link DifferentialEvolutionOptimizer} and,
 * optionally, written row-by-row to a CSV file for downstream plotting.
 *
 * <p><b>CSV column order:</b>
 * <pre>
 *   generation, evaluations, bestProfit, bestAccess, bestRate,
 *   h, inc, anomalySep, pTx,
 *   diversityMean, [diversityStd]
 * </pre>
 */
public final class GenerationRecord {

    // -------------------------------------------------------------------------
    // Generation metadata
    // -------------------------------------------------------------------------

    /** Zero-based generation index (generation 0 = after population initialisation). */
    public final int generation;

    /** Total number of black-box evaluations consumed up to and including this generation. */
    public final int evaluations;

    // -------------------------------------------------------------------------
    // Best individual in this generation
    // -------------------------------------------------------------------------

    /** Best combined profit seen so far (global, not just this generation). */
    public final double bestProfit;

    /** accessPct of the best individual [0, 100]. */
    public final double bestAccessPct;

    /** dataRatePct of the best individual [0, 100]. */
    public final double bestDataRatePct;

    /**
     * Decision vector of the best individual.
     * Layout: [h, inc, anomalySep, pTx] (same order as {@link Bounds#forSatelliteProblem}).
     */
    public final double[] bestVector;

    // -------------------------------------------------------------------------
    // Diversity metrics (optional — NaN if not computed)
    // -------------------------------------------------------------------------

    /**
     * Mean pairwise Euclidean distance normalised by the space diagonal,
     * averaged across all population pairs for a representative diversity signal.
     * {@code NaN} if {@link DEConfig#logDiversity} is {@code false}.
     */
    public final double diversityMean;

    /**
     * Standard deviation of pairwise distances (same normalisation).
     * {@code NaN} if diversity logging is disabled.
     */
    public final double diversityStd;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public GenerationRecord(
            int generation,
            int evaluations,
            double bestProfit,
            double bestAccessPct,
            double bestDataRatePct,
            double[] bestVector,
            double diversityMean,
            double diversityStd) {

        this.generation       = generation;
        this.evaluations      = evaluations;
        this.bestProfit       = bestProfit;
        this.bestAccessPct    = bestAccessPct;
        this.bestDataRatePct  = bestDataRatePct;
        this.bestVector       = Arrays.copyOf(bestVector, bestVector.length);
        this.diversityMean    = diversityMean;
        this.diversityStd     = diversityStd;
    }

    // -------------------------------------------------------------------------
    // CSV serialisation
    // -------------------------------------------------------------------------

    /**
     * CSV header. Column names describe what each column means so that Python / R can
     * consume the file without extra documentation.
     */
    public static String csvHeader() {
        return "generation,evaluations,bestProfit,bestAccessPct,bestDataRatePct,"
             + "h_km,inc_deg,anomalySep_deg,pTx,"
             + "diversityMean,diversityStd";
    }

    /**
     * Serialises this record as a CSV row (no trailing newline).
     * Fields that are {@code NaN} are written as empty strings so that pandas/numpy
     * can parse them with {@code pd.read_csv(..., na_values='')}.
     */
    public String toCsvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(generation).append(',')
          .append(evaluations).append(',')
          .append(fmt(bestProfit)).append(',')
          .append(fmt(bestAccessPct)).append(',')
          .append(fmt(bestDataRatePct)).append(',');

        for (double v : bestVector) {
            sb.append(fmt(v)).append(',');
        }

        sb.append(fmtOptional(diversityMean)).append(',')
          .append(fmtOptional(diversityStd));

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static String fmtOptional(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.US, "%.6f", v);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
            "Gen %4d | eval %4d | profit=%.4f | access=%.2f%% | rate=%.2f%% | "
          + "x=[%.3f, %.3f, %.3f, %.3f]%s",
            generation, evaluations,
            bestProfit, bestAccessPct, bestDataRatePct,
            bestVector.length > 0 ? bestVector[0] : Double.NaN,
            bestVector.length > 1 ? bestVector[1] : Double.NaN,
            bestVector.length > 2 ? bestVector[2] : Double.NaN,
            bestVector.length > 3 ? bestVector[3] : Double.NaN,
            Double.isNaN(diversityMean) ? "" : String.format(Locale.US, " | div=%.4f", diversityMean));
    }
}
