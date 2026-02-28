package deoptimization;
import java.util.Arrays;
import java.util.Locale;

/**
 * Immutable result of a single black-box evaluation.
 *
 * <p>The combined {@link #profit} is computed by the optimizer (not the evaluator) so
 * that weights can be changed without re-running simulations. However, as a convenience
 * the evaluator may pre-fill {@code profit} if it already has a preference; the optimizer
 * will overwrite it with the configured scalarization anyway.
 *
 * <p>If the black-box simulation fails or is infeasible, construct an instance via
 * {@link #failed(double[], String)} — it carries a very low profit so DE automatically
 * discards it.
 */
public final class Evaluation {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The decision vector that produced this evaluation. Defensive copy is stored. */
    public final double[] x;

    /**
     * Average access percentage to the region of interest, averaged across RAAN bands.
     * Range: [0, 100]. Higher is better.
     */
    public final double accessPct;

    /**
     * Normalised ISL data rate in [0, 100], where 100 corresponds to the maximum
     * achievable data rate. Higher is better.
     */
    public final double dataRatePct;

    /**
     * Scalarised profit in [0, 1] computed as:
     * <pre>  profit = wAccess * (accessPct / 100) + wRate * (dataRatePct / 100)</pre>
     * This field is written by {@link DifferentialEvolutionOptimizer} after applying the
     * configured weights. A fresh {@link Evaluation} from the evaluator may leave it as
     * {@code NaN} or 0 — the optimizer always sets it before comparing individuals.
     */
    public final double profit;

    /**
     * {@code true} if the simulator threw an exception or returned clearly invalid data.
     * Failed evaluations are assigned {@code profit = PENALTY}.
     */
    public final boolean failed;

    /** Optional diagnostic message set when {@code failed = true}. */
    public final String failReason;

    /** Profit assigned to a failed evaluation — deliberately very low. */
    public static final double PENALTY = -1.0;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Full constructor used by the optimizer after computing the weighted profit.
     *
     * @param x           decision vector (will be defensively copied)
     * @param accessPct   coverage metric [0, 100]
     * @param dataRatePct data-rate metric [0, 100]
     * @param profit      weighted scalarisation [0, 1]
     */
    public Evaluation(double[] x, double accessPct, double dataRatePct, double profit) {
        this.x           = Arrays.copyOf(x, x.length);
        this.accessPct   = clamp(accessPct,   0.0, 100.0);
        this.dataRatePct = clamp(dataRatePct, 0.0, 100.0);
        this.profit      = profit;
        this.failed      = false;
        this.failReason  = null;
    }

    /**
     * Constructor for the evaluator to return raw metric values (without profit).
     * The optimizer will attach the correct profit via {@link #withProfit(double)}.
     *
     * @param x           decision vector (will be defensively copied)
     * @param accessPct   coverage metric [0, 100]
     * @param dataRatePct data-rate metric [0, 100]
     */
    public Evaluation(double[] x, double accessPct, double dataRatePct) {
        this(x, accessPct, dataRatePct, Double.NaN);
    }

    /** Private constructor for failed evaluations. */
    private Evaluation(double[] x, String reason) {
        this.x           = Arrays.copyOf(x, x.length);
        this.accessPct   = 0.0;
        this.dataRatePct = 0.0;
        this.profit      = PENALTY;
        this.failed      = true;
        this.failReason  = reason;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a sentinel evaluation for a failed simulation run.
     * The optimizer assigns it {@link #PENALTY} profit so it is never selected as best.
     *
     * @param x      the decision vector that caused the failure
     * @param reason human-readable description of what went wrong
     */
    public static Evaluation failed(double[] x, String reason) {
        return new Evaluation(x, reason);
    }

    /**
     * Returns a new {@link Evaluation} identical to this one but with the given profit.
     * Used internally by the optimizer to attach the weighted scalarisation.
     */
    public Evaluation withProfit(double profit) {
        return new Evaluation(this.x, this.accessPct, this.dataRatePct, profit);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Returns {@code true} if this individual dominates {@code other} in both objectives. */
    public boolean dominates(Evaluation other) {
        return (this.accessPct   >= other.accessPct   && this.dataRatePct >= other.dataRatePct)
            && (this.accessPct   >  other.accessPct   || this.dataRatePct >  other.dataRatePct);
    }

    @Override
    public String toString() {
        if (failed) {
            return String.format(Locale.US, "Evaluation{FAILED reason='%s', x=%s}", failReason, Arrays.toString(x));
        }
        return String.format(Locale.US,
            "Evaluation{profit=%.4f, access=%.2f%%, rate=%.2f%%, x=%s}",
            profit, accessPct, dataRatePct, Arrays.toString(x));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static double clamp(double v, double lo, double hi) {
        return Math.min(hi, Math.max(lo, v));
    }

    /** CSV header matching {@link #toCsvRow()}. */
    public static String csvHeader() {
        return "accessPct,dataRatePct,profit,h,inc,anomalySep_deg,pTx,failed";
    }

    /**
     * Returns a comma-separated row for this evaluation (no trailing newline).
     * Column order matches {@link #csvHeader()}.
     */
    public String toCsvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%.6f,%.6f,%.6f", accessPct, dataRatePct, profit));
        for (double v : x) sb.append(',').append(String.format(Locale.US, "%.6f", v));
        sb.append(',').append(failed ? "1" : "0");
        return sb.toString();
    }
}
