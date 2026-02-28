package deoptimization;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Differential Evolution optimizer wired to a black-box {@link FitnessEvaluator}.
 *
 * <h3>Algorithm: DE/current-to-best/1/bin</h3>
 * <pre>
 *   For each individual i at generation g:
 *     // Mutation
 *     v_i = x_i + F * (x_best - x_i) + F * (x_r1 - x_r2)
 *         where r1, r2 drawn without replacement from pop \ {i}
 *
 *     // Bound enforcement (reflection for linear dims, modulo for circular)
 *     v_i ← bounds.enforceAll(v_i)
 *
 *     // Crossover (binomial)
 *     jrand ← random in [0, dim)          // ensures ≥ 1 dimension from mutant
 *     trial_j = v_j  if rand() < CR or j == jrand
 *             = x_ij otherwise
 *
 *     // Greedy selection (maximisation)
 *     if profit(trial) >= profit(x_i):
 *         x_i ← trial
 * </pre>
 *
 * <h3>Scalarisation</h3>
 * <pre>
 *   profit = wAccess * (accessPct / 100) + wRate * (dataRatePct / 100)  ∈ [0, 1]
 * </pre>
 *
 * <h3>Multi-objective note</h3>
 * Weighted scalarisation (Option A) is chosen over a Pareto archive because:
 * <ol>
 *   <li>Every evaluation produces a single comparable scalar — the full budget is
 *       useful even with NP = 20.</li>
 *   <li>A Pareto front needs a large population to be well-sampled; with ≤ 200
 *       evaluations and dim = 4 this is infeasible.</li>
 *   <li>Weights can be changed post-hoc to re-rank the history without re-running
 *       any simulations (see {@link #rescoreHistory(double, double, double, double)}).</li>
 * </ol>
 */
public final class DifferentialEvolutionOptimizer {

    private static final Logger LOG = Logger.getLogger(DifferentialEvolutionOptimizer.class.getName());

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private final DEConfig       cfg;
    private final Bounds         bounds;
    private final FitnessEvaluator evaluator;
    private final Random         rng;

    // -------------------------------------------------------------------------
    // State (valid after run())
    // -------------------------------------------------------------------------

    /** Full generation-by-generation log. */
    private final List<GenerationRecord> history = new ArrayList<>();

    /** Best evaluation found across the entire run. */
    private Evaluation bestOverall = null;

    /** Total black-box calls consumed (includes population initialisation). */
    private int evalCount = 0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a ready-to-run optimizer.
     *
     * @param cfg       tuning parameters — build via {@link DEConfig.Builder}
     * @param bounds    search space — build via {@link Bounds#forSatelliteProblem}
     * @param evaluator your black-box simulation adapter
     */
    public DifferentialEvolutionOptimizer(DEConfig cfg, Bounds bounds, FitnessEvaluator evaluator) {
        if (cfg.populationSize < bounds.dim + 1) {
            throw new IllegalArgumentException(
                "populationSize (" + cfg.populationSize + ") must be > dim (" + bounds.dim + "). "
              + "Recommended: 5–10 * dim.");
        }
        this.cfg       = cfg;
        this.bounds    = bounds;
        this.evaluator = evaluator;
        this.rng       = cfg.seed != null ? new Random(cfg.seed) : new Random();

        // ── Heuristic sanity checks ────────────────────────────────────────────
        int recommendedMinNP = 5 * bounds.dim;
        if (cfg.populationSize < recommendedMinNP) {
            LOG.warning(String.format(
                "populationSize=%d is below the recommended minimum of 5×dim=%d. "
              + "With small populations DE/current-to-best converges prematurely — "
              + "consider increasing NP to at least %d for production runs.",
                cfg.populationSize, recommendedMinNP, recommendedMinNP));
        }
        int maxGenerations = (cfg.maxEvaluations - cfg.populationSize) / cfg.populationSize;
        if (cfg.earlyStopGenerations > 0 && cfg.earlyStopGenerations > maxGenerations) {
            LOG.warning(String.format(
                "earlyStop patience=%d generations will never trigger: budget allows only ~%d "
              + "generations (maxEvals=%d, NP=%d). Either raise maxEvaluations or lower earlyStop.",
                cfg.earlyStopGenerations, maxGenerations,
                cfg.maxEvaluations, cfg.populationSize));
        }

        LOG.info("DE optimizer created. " + cfg);
        LOG.info("Search space: " + bounds);
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Runs the full DE optimisation and returns the result.
     *
     * <p>Calling {@code run()} twice re-runs from scratch (state is reset).
     *
     * @return an {@link OptimizationResult} containing the best solution, history,
     *         and stopping reason.
     */
    public OptimizationResult run() {
        // ── Reset state ────────────────────────────────────────────────────────
        history.clear();
        evalCount   = 0;
        bestOverall = null;

        final int NP  = cfg.populationSize;
        final int dim = bounds.dim;

        // ── Optional CSV writer ────────────────────────────────────────────────
        BufferedWriter csvWriter = openCsvWriter();

        try {
            // ══════════════════════════════════════════════════════════════════
            // STEP 1: Initialise population
            // ══════════════════════════════════════════════════════════════════
            double[][]   pop  = new double[NP][dim];
            Evaluation[] evals = new Evaluation[NP];

            LOG.info("Initialising population of " + NP + " individuals…");
            for (int i = 0; i < NP; i++) {
                pop[i]  = bounds.sample(rng);
                evals[i] = evaluateAndScore(pop[i]);
                if (isBetter(evals[i], bestOverall)) bestOverall = evals[i];
                if (evalCount >= cfg.maxEvaluations) break;  // tiny budget guard
            }

            // Record generation 0 (after initialisation)
            GenerationRecord gen0 = makeRecord(0, pop, evals);
            history.add(gen0);
            writeCsvRow(csvWriter, gen0);
            LOG.info(gen0.toString());

            // ══════════════════════════════════════════════════════════════════
            // STEP 2: Main DE loop
            // ══════════════════════════════════════════════════════════════════

            // Reusable buffers — avoid allocation per-individual
            double[] mutant = new double[dim];
            double[] trial  = new double[dim];

            // Early-stop tracking
            double lastBestForEarlyStop   = bestOverall != null ? bestOverall.profit : Double.NEGATIVE_INFINITY;
            int    stagnantGenerationCount = 0;

            int generation = 1;
            String stopReason = "maxEvaluations";

            outer:
            while (evalCount < cfg.maxEvaluations) {

                // ── Find current global best index ─────────────────────────────
                int bestIdx = findBestIndex(evals, NP);

                // ── Iterate over population ────────────────────────────────────
                for (int i = 0; i < NP; i++) {

                    if (evalCount >= cfg.maxEvaluations) break outer;

                    // ── Mutation: DE/current-to-best/1/bin ─────────────────────
                    //
                    //   Pick two donors r1, r2 ∈ {0..NP-1} \ {i}
                    //   (they may equal bestIdx; that is fine and common in literature)
                    int r1 = pickRandom(NP, i, -1);
                    int r2 = pickRandom(NP, i, r1);

                    for (int d = 0; d < dim; d++) {
                        mutant[d] = pop[i][d]
                                  + cfg.mutationFactor * (pop[bestIdx][d] - pop[i][d])
                                  + cfg.mutationFactor * (pop[r1][d]     - pop[r2][d]);
                    }

                    // ── Enforce bounds on mutant ────────────────────────────────
                    bounds.enforceAll(mutant);

                    // ── Crossover (binomial) ────────────────────────────────────
                    //
                    //   jrand guarantees trial differs from x[i] in at least one dimension
                    int jrand = rng.nextInt(dim);

                    for (int d = 0; d < dim; d++) {
                        trial[d] = (rng.nextDouble() < cfg.crossoverRate || d == jrand)
                                 ? mutant[d]
                                 : pop[i][d];
                    }

                    // ── Evaluate trial ──────────────────────────────────────────
                    Evaluation trialEval = evaluateAndScore(trial);

                    // ── Greedy selection (maximisation) ─────────────────────────
                    if (isBetter(trialEval, evals[i])) {
                        pop[i]   = Arrays.copyOf(trial, dim);   // update population slot
                        evals[i] = trialEval;

                        // Update global best
                        if (isBetter(trialEval, bestOverall)) {
                            bestOverall = trialEval;
                        }
                    }
                }

                // ── Record this generation ─────────────────────────────────────
                GenerationRecord rec = makeRecord(generation, pop, evals);
                history.add(rec);
                writeCsvRow(csvWriter, rec);
                LOG.info(rec.toString());

                // ── Early-stop check ───────────────────────────────────────────
                if (cfg.earlyStopGenerations > 0) {
                    double improvement = rec.bestProfit - lastBestForEarlyStop;
                    if (improvement > cfg.earlyStopEpsilon) {
                        lastBestForEarlyStop    = rec.bestProfit;
                        stagnantGenerationCount = 0;
                    } else {
                        stagnantGenerationCount++;
                        if (stagnantGenerationCount >= cfg.earlyStopGenerations) {
                            stopReason = "earlyStop (no improvement > " + cfg.earlyStopEpsilon
                                       + " for " + cfg.earlyStopGenerations + " generations)";
                            break;
                        }
                    }
                }

                generation++;
            }

            // ══════════════════════════════════════════════════════════════════
            // STEP 3: Package result
            // ══════════════════════════════════════════════════════════════════
            LOG.info("DE finished. Reason: " + stopReason
                   + " | totalEvals=" + evalCount
                   + " | best=" + bestOverall);

            return new OptimizationResult(
                    bestOverall,
                    Collections.unmodifiableList(new ArrayList<>(history)),
                    evalCount,
                    stopReason);

        } finally {
            closeCsvWriter(csvWriter);
        }
    }

    // =========================================================================
    // Inner algorithm helpers
    // =========================================================================

    /**
     * Calls the evaluator, clamps the returned metrics, and attaches the weighted profit.
     * If the evaluator throws, returns a failed {@link Evaluation} so DE can continue.
     *
     * <p><strong>This is the ONLY place the black-box is called.</strong>
     */
    private Evaluation evaluateAndScore(double[] x) {
        evalCount++;
        Evaluation raw;
        try {
            raw = evaluator.evaluate(x);
            if (raw == null) {
                raw = Evaluation.failed(x, "evaluator returned null");
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Evaluator threw exception at eval #" + evalCount
                    + ": " + ex.getMessage(), ex);
            raw = Evaluation.failed(x, ex.getMessage());
        }

        if (raw.failed) {
            return raw; // profit is already PENALTY
        }

        // ── Weighted scalarisation with balance and power penalty ──────────────
        //
        //   a = accessPct  / 100                             ∈ [0, 1]
        //   r = dataRatePct / 100                            ∈ [0, 1]
        //   p = (pTx - pTxMin) / (pTxMax - pTxMin)          ∈ [0, 1]  (0=best, 1=worst)
        //
        //   profit = wAccess  * a                 ← linear access reward
        //          + wRate    * r                 ← linear data-rate reward
        //          + wBalance * sqrt(a * r)       ← geometric-mean balance bonus:
        //                                            0 when either metric is 0,
        //                                            maximal when both are 1
        //          - wPower   * p                 ← power penalty
        //
        //   Best feasible case (a=r=1, p=0): profit = wAccess + wRate + wBalance
        //   Worst case         (a=r=0, p=1): profit = -wPower
        //   The weights are independent and do not need to sum to 1.
        //
        double a = raw.accessPct  / 100.0;
        double r = raw.dataRatePct / 100.0;
        double p = (cfg.pTxMax > cfg.pTxMin)
                 ? (raw.x[3] - cfg.pTxMin) / (cfg.pTxMax - cfg.pTxMin)
                 : 0.0;
        p = Math.min(1.0, Math.max(0.0, p));   // clamp to [0,1]

        double profit = cfg.wAccess  * a
                      + cfg.wRate    * r
                      + cfg.wBalance * Math.sqrt(a * r)
                      - cfg.wPower   * p;

        // ── Zero-access penalty ────────────────────────────────────────────────
        //
        // A constellation with 0 % coverage is physically useless regardless of
        // data rate.  We shift its profit down by a constant large enough to
        // guarantee it falls below the worst feasible profit, which is bounded
        // from below by  -wPower  (when a=0⁺, r=0, p=1).
        //
        //   infeasible profit range:  [wRate·r  - wPower·p - 2]  ≈  [-2.25, -1.75]
        //   feasible   profit range:  [-wPower,  wAccess+wRate+wBalance]
        //
        // The data-rate and power terms are kept inside the shift so the
        // optimizer can still rank zero-access individuals against each other
        // and climb toward configurations that eventually produce coverage.
        if (raw.accessPct <= 0.0) {
            profit -= 2.0;
        }

        return raw.withProfit(profit);
    }

    /** Maximisation comparison: returns true if {@code candidate} is strictly better. */
    private static boolean isBetter(Evaluation candidate, Evaluation incumbent) {
        if (candidate == null) return false;
        if (incumbent == null) return true;
        return candidate.profit > incumbent.profit;
    }

    /** Returns the index of the individual with the highest profit. */
    private static int findBestIndex(Evaluation[] evals, int NP) {
        int best = 0;
        for (int i = 1; i < NP; i++) {
            if (isBetter(evals[i], evals[best])) best = i;
        }
        return best;
    }

    /**
     * Picks a random index in [0, NP) that is different from both {@code exclude1}
     * and {@code exclude2}.  Pass {@code exclude2 = -1} to exclude only one index.
     *
     * <p>Uses rejection sampling; worst-case iterations = NP / (NP - 2) ≈ 1 for NP ≥ 10.
     */
    private int pickRandom(int NP, int exclude1, int exclude2) {
        int idx;
        do {
            idx = rng.nextInt(NP);
        } while (idx == exclude1 || idx == exclude2);
        return idx;
    }

    // =========================================================================
    // Diversity metric
    // =========================================================================

    /**
     * Computes the mean and standard deviation of pairwise Euclidean distances
     * between population members, normalised by the space diagonal.
     *
     * <p>Complexity: O(NP² * dim). For NP = 30, dim = 4 this is ~3 600 multiplications
     * per generation — negligible compared to a satellite simulation.
     *
     * @return double[2] = {mean, std}; returns {NaN, NaN} if diversity logging is off.
     */
    private double[] computeDiversity(double[][] pop, int NP) {
        if (!cfg.logDiversity) return new double[]{Double.NaN, Double.NaN};

        // Normalisation factor: Euclidean length of the bounding box diagonal
        double diagonal = 0.0;
        for (int d = 0; d < bounds.dim; d++) {
            double r = bounds.range(d);
            diagonal += r * r;
        }
        diagonal = Math.sqrt(diagonal);
        if (diagonal == 0) return new double[]{0, 0};

        // Collect all pairwise distances (upper triangle only)
        int pairs = NP * (NP - 1) / 2;
        double[] distances = new double[pairs];
        int idx = 0;

        for (int i = 0; i < NP - 1; i++) {
            for (int j = i + 1; j < NP; j++) {
                double sumSq = 0.0;
                for (int d = 0; d < bounds.dim; d++) {
                    double diff = pop[i][d] - pop[j][d];
                    sumSq += diff * diff;
                }
                distances[idx++] = Math.sqrt(sumSq) / diagonal;
            }
        }

        // Mean
        double sum = 0.0;
        for (double v : distances) sum += v;
        double mean = sum / pairs;

        // Std
        double sumSq = 0.0;
        for (double v : distances) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        double std = pairs > 1 ? Math.sqrt(sumSq / (pairs - 1)) : 0.0;

        return new double[]{mean, std};
    }

    // =========================================================================
    // Record construction
    // =========================================================================

    private GenerationRecord makeRecord(int generation, double[][] pop, Evaluation[] evals) {
        int    bestIdx = findBestIndex(evals, cfg.populationSize);
        double[] div   = computeDiversity(pop, cfg.populationSize);

        return new GenerationRecord(
                generation,
                evalCount,
                evals[bestIdx].profit,
                evals[bestIdx].accessPct,
                evals[bestIdx].dataRatePct,
                evals[bestIdx].x,
                div[0],
                div[1]);
    }

    // =========================================================================
    // Post-hoc rescoring (useful for sensitivity analysis)
    // =========================================================================

    /**
     * Re-applies new weights to the full history <em>without</em> calling the
     * black-box again.  Useful to explore weight sensitivity after a run.
     *
     * <p>Applies the same profit formula as the optimizer:
     * <pre>
     *   profit = wAccess * a + wRate * r + wBalance * sqrt(a*r) - wPower * p
     * </pre>
     * where {@code p} is the normalised TX power read from the stored decision vector,
     * using the same {@code pTxMin}/{@code pTxMax} as the original config.
     *
     * @param wAccess  new weight for access reward (≥ 0)
     * @param wRate    new weight for data-rate reward (≥ 0)
     * @param wBalance new weight for balance bonus (≥ 0; 0 to disable)
     * @param wPower   new weight for power penalty (≥ 0; 0 to disable)
     * @return rescored history with updated bestProfit values (unmodifiable)
     */
    public List<GenerationRecord> rescoreHistory(
            double wAccess, double wRate, double wBalance, double wPower) {
        if (wAccess < 0 || wRate < 0 || wBalance < 0 || wPower < 0) {
            throw new IllegalArgumentException("All weights must be non-negative");
        }
        List<GenerationRecord> rescored = new ArrayList<>(history.size());
        for (GenerationRecord rec : history) {
            double a = rec.bestAccessPct  / 100.0;
            double r = rec.bestDataRatePct / 100.0;
            double p = (cfg.pTxMax > cfg.pTxMin && rec.bestVector.length > 3)
                     ? (rec.bestVector[3] - cfg.pTxMin) / (cfg.pTxMax - cfg.pTxMin)
                     : 0.0;
            p = Math.min(1.0, Math.max(0.0, p));
            double newProfit = wAccess  * a
                             + wRate    * r
                             + wBalance * Math.sqrt(a * r)
                             - wPower   * p;
            if (rec.bestAccessPct <= 0.0) {
                newProfit -= 2.0;   // keep consistent with evaluateAndScore()
            }
            rescored.add(new GenerationRecord(
                    rec.generation, rec.evaluations,
                    newProfit, rec.bestAccessPct, rec.bestDataRatePct,
                    rec.bestVector, rec.diversityMean, rec.diversityStd));
        }
        return Collections.unmodifiableList(rescored);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** Returns the unmodifiable generation history (valid after {@link #run()}). */
    public List<GenerationRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Returns the best evaluation found (valid after {@link #run()}). */
    public Evaluation getBestOverall() {
        return bestOverall;
    }

    // =========================================================================
    // CSV helpers (buffered, safe)
    // =========================================================================

    private BufferedWriter openCsvWriter() {
        if (cfg.csvOutputPath == null) return null;
        try {
            BufferedWriter w = new BufferedWriter(new FileWriter(cfg.csvOutputPath, false));
            w.write(GenerationRecord.csvHeader());
            w.newLine();
            w.flush();
            LOG.info("CSV log opened: " + cfg.csvOutputPath);
            return w;
        } catch (IOException ex) {
            LOG.warning("Could not open CSV file '" + cfg.csvOutputPath + "': " + ex.getMessage()
                      + ". File logging disabled.");
            return null;
        }
    }

    private void writeCsvRow(BufferedWriter w, GenerationRecord rec) {
        if (w == null) return;
        try {
            w.write(rec.toCsvRow());
            w.newLine();
            w.flush();   // flush every row so partial runs are readable
        } catch (IOException ex) {
            LOG.warning("Failed to write CSV row (gen=" + rec.generation + "): " + ex.getMessage());
        }
    }

    private void closeCsvWriter(BufferedWriter w) {
        if (w == null) return;
        try {
            w.close();
        } catch (IOException ex) {
            LOG.warning("Failed to close CSV writer: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Result container
    // =========================================================================

    /**
     * Immutable container returned by {@link DifferentialEvolutionOptimizer#run()}.
     */
    public static final class OptimizationResult {

        /** Best solution found across the entire run. May be {@code null} if budget = 0. */
        public final Evaluation best;

        /** Full per-generation history for convergence plotting. */
        public final List<GenerationRecord> history;

        /** Total black-box evaluations consumed (including population init). */
        public final int totalEvaluations;

        /** Human-readable explanation of why the optimiser stopped. */
        public final String stopReason;

        private OptimizationResult(Evaluation best, List<GenerationRecord> history,
                                   int totalEvaluations, String stopReason) {
            this.best             = best;
            this.history          = history;
            this.totalEvaluations = totalEvaluations;
            this.stopReason       = stopReason;
        }

        /** Prints a concise summary of the result to stdout. */
        public void printSummary() {
            System.out.println("═══════════════════════════════════════════════════");
            System.out.println("  Differential Evolution — Result Summary");
            System.out.println("═══════════════════════════════════════════════════");
            System.out.println("  Stop reason     : " + stopReason);
            System.out.println("  Total evals     : " + totalEvaluations);
            System.out.println("  Generations log : " + history.size());
            if (best != null) {
                System.out.printf(Locale.US, "  Best profit     : %.6f%n", best.profit);
                System.out.printf(Locale.US, "  Best accessPct  : %.4f %%%n", best.accessPct);
                System.out.printf(Locale.US, "  Best dataRatePct: %.4f %%%n", best.dataRatePct);
                System.out.printf(Locale.US, "  Best x (h, inc, anomalySep, pTx):%n");
                System.out.printf(Locale.US, "    h         = %.4f%n", best.x[0]);
                System.out.printf(Locale.US, "    inc       = %.4f deg%n", best.x[1]);
                System.out.printf(Locale.US, "    anomalySep = %.4f deg%n", best.x[2]);
                System.out.printf(Locale.US, "    pTx   = %.4f%n", best.x[3]);
            } else {
                System.out.println("  No feasible solution found.");
            }
            System.out.println("═══════════════════════════════════════════════════");
        }
    }
}
