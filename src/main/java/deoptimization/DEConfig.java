package deoptimization;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuration for the Differential Evolution optimizer.
 *
 * <p>All parameters are public fields for ergonomic access; use the builder pattern
 * ({@link Builder}) to construct validated instances.
 *
 * <p>Design choices:
 * <ul>
 *   <li><b>Strategy: DE/current-to-best/1/bin</b> — chosen over DE/rand/1/bin because it
 *       converges faster toward good regions, which is critical when each evaluation is
 *       expensive. The "current-to-best" bias gives directional guidance (like gradient
 *       information) while the random differential term preserves enough diversity to avoid
 *       premature convergence. For a 4-D problem with ≤ 200 evaluations this is the
 *       safer choice.</li>
 *   <li><b>Scalarization (Option A)</b> — preferred over a Pareto archive because with a
 *       low evaluation budget (≤ 200) a Pareto front needs a large population to be
 *       meaningful. Weighted scalarization collapses the two objectives into a single
 *       profit value so every evaluation is fully exploited and comparison of individuals
 *       is unambiguous.</li>
 * </ul>
 */
public final class DEConfig {

    // -------------------------------------------------------------------------
    // Population / control parameters
    // -------------------------------------------------------------------------

    /** Number of individuals in the population. Default: 10 * dim (recommend 20–40 for dim=4). */
    public final int populationSize;

    /** Mutation (scale) factor F ∈ (0, 2]. Typical: 0.5–0.9. */
    public final double mutationFactor;

    /** Crossover rate CR ∈ [0, 1]. Typical: 0.7–0.95. */
    public final double crossoverRate;

    // -------------------------------------------------------------------------
    // Stopping criteria
    // -------------------------------------------------------------------------

    /** Hard limit on total black-box evaluations (population initialisation counts too). */
    public final int maxEvaluations;

    /**
     * Early-stop: if best profit has not improved by {@code earlyStopEpsilon} over
     * {@code earlyStopGenerations} consecutive generations, terminate.
     * Set {@code earlyStopGenerations = 0} to disable.
     */
    public final double earlyStopEpsilon;

    /** Number of stagnant generations before early stop triggers. 0 = disabled. */
    public final int earlyStopGenerations;

    // -------------------------------------------------------------------------
    // Objective / scalarization
    // -------------------------------------------------------------------------

    /**
     * Weight for the linear access-percentage reward term.
     * All four objective weights ({@code wAccess}, {@code wRate}, {@code wBalance},
     * {@code wPower}) are independent — they do not need to sum to 1.
     * Treat them as relative importance ratios.
     */
    public final double wAccess;

    /** Weight for the linear data-rate reward term. */
    public final double wRate;

    /**
     * Weight for the geometric-mean balance bonus: {@code wBalance * sqrt(a * r)}.
     * This term is zero when either objective is zero and maximal when both are at
     * their peak, strongly discouraging solutions that excel in one metric while
     * neglecting the other. Set to 0 to disable.
     */
    public final double wBalance;

    /**
     * Weight for the normalised TX-power penalty: {@code wPower * p}, where
     * {@code p ∈ [0, 1]} is the normalised transmission power.
     * Set to 0 to disable.
     */
    public final double wPower;

    /**
     * Lower bound of the TX-power normalisation range (same unit as {@code x[3]}).
     * At this power level the penalty contribution is 0.
     * <p><b>Placeholder:</b> set this to match {@code P_MIN} in your problem bounds.
     */
    public final double pTxMin;

    /**
     * Upper bound of the TX-power normalisation range (same unit as {@code x[3]}).
     * At this power level the penalty contribution is 1 (maximum penalty).
     * <p><b>Placeholder:</b> set this to match {@code P_MAX} in your problem bounds.
     */
    public final double pTxMax;

    // -------------------------------------------------------------------------
    // Logging / output
    // -------------------------------------------------------------------------

    /**
     * Optional path for a CSV log file written every generation.
     * Set to {@code null} to disable file output.
     */
    public final String csvOutputPath;

    /** Whether to compute and log population diversity each generation (slight overhead). */
    public final boolean logDiversity;

    // -------------------------------------------------------------------------
    // Reproducibility
    // -------------------------------------------------------------------------

    /**
     * Optional RNG seed for reproducible runs. Set to {@code null} to use a
     * time-based seed.
     */
    public final Long seed;

    // -------------------------------------------------------------------------
    // Private constructor — use Builder
    // -------------------------------------------------------------------------

    private DEConfig(Builder b) {
        this.populationSize       = b.populationSize;
        this.mutationFactor       = b.mutationFactor;
        this.crossoverRate        = b.crossoverRate;
        this.maxEvaluations       = b.maxEvaluations;
        this.earlyStopEpsilon     = b.earlyStopEpsilon;
        this.earlyStopGenerations = b.earlyStopGenerations;
        this.wAccess              = b.wAccess;
        this.wRate                = b.wRate;
        this.wBalance             = b.wBalance;
        this.wPower               = b.wPower;
        this.pTxMin               = b.pTxMin;
        this.pTxMax               = b.pTxMax;
        this.csvOutputPath        = b.csvOutputPath;
        this.logDiversity         = b.logDiversity;
        this.seed                 = b.seed;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
            "DEConfig{NP=%d, F=%.2f, CR=%.2f, maxEval=%d, earlyStop=%d gens (eps=%.4f), "
          + "w=[acc=%.2f, rate=%.2f, bal=%.2f, pow=%.2f], "
          + "pTx=[%.2f, %.2f], seed=%s, csv=%s}",
            populationSize, mutationFactor, crossoverRate, maxEvaluations,
            earlyStopGenerations, earlyStopEpsilon,
            wAccess, wRate, wBalance, wPower,
            pTxMin, pTxMax,
            seed == null ? "random" : seed.toString(),
            csvOutputPath == null ? "disabled" : csvOutputPath);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static final class Builder {

        // mandatory
        private final int maxEvaluations;

        // optional with defaults suited for dim=4, expensive black-box
        private int    populationSize       = 30;   // ~7.5 * dim; covers search space well
        private double mutationFactor       = 0.7;
        private double crossoverRate        = 0.9;
        private double earlyStopEpsilon     = 1e-4;
        private int    earlyStopGenerations = 20;   // 0 = disabled
        private double wAccess             = 0.4;
        private double wRate               = 0.4;
        private double wBalance            = 0.2;   // geometric-mean balance bonus
        private double wPower              = 0.0;   // power penalty (disabled by default)
        private double pTxMin              = 30.0;  // ← PLACEHOLDER: set to P_MIN (dBm)
        private double pTxMax              = 35.0;  // ← PLACEHOLDER: set to P_MAX (dBm)
        private String csvOutputPath       = null;
        private boolean logDiversity       = true;
        private Long   seed                = null;

        /**
         * Creates a builder with the only mandatory parameter.
         *
         * @param maxEvaluations hard limit on black-box calls (including initialisation).
         *                       Recommended: 150–300 for dim=4 with expensive simulation.
         */
        public Builder(int maxEvaluations) {
            if (maxEvaluations < 2) {
                throw new IllegalArgumentException("maxEvaluations must be >= 2");
            }
            this.maxEvaluations = maxEvaluations;
        }

        public Builder populationSize(int np) {
            if (np < 4) throw new IllegalArgumentException("populationSize must be >= 4 (need at least 3 distinct donors + target)");
            this.populationSize = np;
            return this;
        }

        public Builder mutationFactor(double f) {
            if (f <= 0 || f > 2) throw new IllegalArgumentException("F must be in (0, 2]");
            this.mutationFactor = f;
            return this;
        }

        public Builder crossoverRate(double cr) {
            if (cr < 0 || cr > 1) throw new IllegalArgumentException("CR must be in [0, 1]");
            this.crossoverRate = cr;
            return this;
        }

        public Builder earlyStop(int generations, double epsilon) {
            if (generations < 0) throw new IllegalArgumentException("earlyStopGenerations must be >= 0");
            if (epsilon < 0)     throw new IllegalArgumentException("earlyStopEpsilon must be >= 0");
            this.earlyStopGenerations = generations;
            this.earlyStopEpsilon     = epsilon;
            return this;
        }

        /**
         * Sets the four objective weights (all must be ≥ 0; no sum constraint).
         *
         * <p>The profit function is:
         * <pre>
         *   a = accessPct / 100                             ∈ [0, 1]
         *   r = dataRatePct / 100                           ∈ [0, 1]
         *   p = (pTx − pTxMin) / (pTxMax − pTxMin)         ∈ [0, 1]
         *
         *   profit = wAccess  * a
         *          + wRate    * r
         *          + wBalance * sqrt(a × r)    ← geometric-mean balance bonus
         *          − wPower   * p              ← power penalty
         * </pre>
         *
         * Best feasible case (a=r=1, p=0): {@code profit = wAccess + wRate + wBalance}.
         *
         * @param wAccess  reward for access percentage (≥ 0)
         * @param wRate    reward for data rate (≥ 0)
         * @param wBalance bonus for balanced solutions — use 0 to disable (≥ 0)
         * @param wPower   penalty for transmission power — use 0 to disable (≥ 0)
         */
        public Builder weights(double wAccess, double wRate, double wBalance, double wPower) {
            if (wAccess < 0 || wRate < 0 || wBalance < 0 || wPower < 0) {
                throw new IllegalArgumentException("All weights must be non-negative");
            }
            this.wAccess  = wAccess;
            this.wRate    = wRate;
            this.wBalance = wBalance;
            this.wPower   = wPower;
            return this;
        }

        /**
         * Sets the TX-power normalisation range for the power penalty term.
         * These should match the power bounds in your {@link Bounds} (i.e., P_MIN and P_MAX).
         *
         * @param pTxMin lower bound — penalty = 0 at this power level (← PLACEHOLDER)
         * @param pTxMax upper bound — penalty = 1 at this power level (← PLACEHOLDER)
         */
        public Builder powerBounds(double pTxMin, double pTxMax) {
            if (pTxMin >= pTxMax) {
                throw new IllegalArgumentException("pTxMin must be < pTxMax");
            }
            this.pTxMin = pTxMin;
            this.pTxMax = pTxMax;
            return this;
        }

        /** Enables CSV logging to the given file path. */
        public Builder csvOutput(String path) {
            this.csvOutputPath = Objects.requireNonNull(path);
            return this;
        }

        /**
         * Explicitly enables the per-generation diversity metric (it is on by default).
         * Shown here for symmetry with {@link #disableDiversity()}.
         */
        public Builder logDiversity() {
            this.logDiversity = true;
            return this;
        }

        /** Disables the optional per-generation diversity metric to save CPU. */
        public Builder disableDiversity() {
            this.logDiversity = false;
            return this;
        }

        /** Sets a fixed RNG seed so runs are reproducible. */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public DEConfig build() {
            return new DEConfig(this);
        }
    }
}
