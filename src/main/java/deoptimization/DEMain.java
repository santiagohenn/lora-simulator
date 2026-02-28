package deoptimization;
import java.util.Locale;
/**
 * Example entry point showing how to wire the DE optimizer to a simulation.
 *
 * <p>Instructions:
 * <ol>
 *   <li>Replace {@code DummyEvaluator} with your real implementation of
 *       {@link FitnessEvaluator} (e.g., {@code ConstellationSimEvaluator}).</li>
 *   <li>Set the bound constants to match your problem's units.</li>
 *   <li>Tune {@code maxEvaluations} to your computation budget.</li>
 *   <li>Run and check the CSV file for convergence plotting.</li>
 * </ol>
 */
public class DEMain {

    // =========================================================================
    // Problem-specific constants — FILL IN YOUR VALUES
    // =========================================================================

    // Satellite height bounds (use the SAME unit your simulator expects)
    private static final double H_MIN  = 500.0;   // km 
    private static final double H_MAX  = 600.0;   // km

    // Inclination bounds (degrees)
    private static final double I_MIN  = 80.0;    // deg
    private static final double I_MAX  = 100.0;    // deg

    // Anomaly separation bounds (degrees)
    private static final double ANOM_SEP_MIN = 1.0;   // deg
    private static final double ANOM_SEP_MAX = 15.0; // deg

    // Transmission power bounds (use the SAME unit your ISL model expects)
    private static final double P_MIN  = 30.0;     // dbm
    private static final double P_MAX  = 35.0;    // dbm

    // Access towards N satellites
    private static final int TARGET_SATELLITE_COUNT = 1; // 1 = "≥1 sat", 2 = "≥2 sats", …

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args) {

        // ── 1. Define the search space ─────────────────────────────────────────
        Bounds space = Bounds.forSatelliteProblem(H_MIN, H_MAX, I_MIN, I_MAX, ANOM_SEP_MIN, ANOM_SEP_MAX, P_MIN, P_MAX);
        System.out.println("Search space: " + space);

        // ── 2. Build the DE configuration ─────────────────────────────────────
        // Budget choice: for dim = 4 with expensive simulation, aim for 100–200 evals.
        // NP = 30 gives about 4 full generations at 150 evals (plus init).
        // Increase NP to 40 for more exploration; decrease to 20 for tighter budgets.
        DEConfig cfg = new DEConfig.Builder(330)           // maxEvaluations
                .populationSize(20)                        // NP: 5–10 × dim
                .mutationFactor(0.7)                       // F ∈ (0, 2]
                .crossoverRate(0.9)                        // CR ∈ [0, 1]
                .earlyStop(15, 1e-4)                       // stop if no improvement in 15 gens
                .weights(0.35, 0.25, 0.15, 0.25)              // wAccess, wRate, wBalance, wPower ← CHANGE ME
                .powerBounds(P_MIN, P_MAX)                 // TX-power normalisation anchors
                .csvOutput("C:/projects/results/differential_evolution/de_history_4.csv")  // ← CHANGE ME or remove
                .logDiversity()                            // remove if CPU is tight
                .seed(38L)                               // remove for non-deterministic
                .build();

        System.out.println("Config: " + cfg);

        // ── 3. Plug in your evaluator (swap DummyEvaluator for your real one) ──
        //
        // REAL USAGE (uncomment and adapt):
        FitnessEvaluator evaluator = new ConstellationSimEvaluator(
                "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties", 
                "C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv",
                "C:/projects/results/differential_evolution/",
                -10.0,
                15.0,
                1.0,
                TARGET_SATELLITE_COUNT);

                /*
                    public ConstellationSimEvaluator(
                        String configPath,
                        String roiPath,
                        String outputDir,
                        double raanMin,
                        double raanMax,
                        double raanStep)                  // defaults targetSatelliteCount = 1

                    — or, to target a specific N-satellite access column:
                    new ConstellationSimEvaluator(configPath, roiPath, outputDir,
                        raanMin, raanMax, raanStep,
                        1)  // targetSatelliteCount: 1 = "≥1 sat", 2 = "≥2 sats", …
                */
        
        // FitnessEvaluator evaluator = new DummyEvaluator(); // For testing

        // ── 4. Create and run the optimizer ───────────────────────────────────
        DifferentialEvolutionOptimizer de = new DifferentialEvolutionOptimizer(cfg, space, evaluator);
        DifferentialEvolutionOptimizer.OptimizationResult result = de.run();

        // ── 5. Print summary ───────────────────────────────────────────────────
        result.printSummary();

        // ── 6. (Optional) Re-score with different weights without re-running ───
        //       Useful to see how the best solution changes under access-priority.
        System.out.println("\nRe-scored with access-priority (wAccess=0.8, wRate=0.2, no balance/power):");
        java.util.List<GenerationRecord> rescored = de.rescoreHistory(0.8, 0.2, 0.0, 0.0);
        GenerationRecord lastRescored = rescored.get(rescored.size() - 1);
        System.out.printf(Locale.US, "  Last-gen best profit (access-priority): %.4f%n",
                lastRescored.bestProfit);
    }

    // =========================================================================
    // Inner class: remove this once you have a real evaluator
    // =========================================================================

    /**
     * Stand-in evaluator that returns synthetic (but meaningful) values.
     *
     * <p><b>Remove this class</b> and replace with your real simulator adapter.
     * The dummy models two plausible trends:
     * <ul>
     *   <li>access improves with height (higher orbit → wider footprint) up to a peak,
     *       with a small inclination dependence.</li>
     *   <li>data rate decreases with height (free-space path loss grows with distance)
     *       and increases with TX power.</li>
     * </ul>
     * There is a deliberate trade-off: maximising both simultaneously is impossible,
     * which makes this a fair test for the scalarisation.
     */
    private static final class DummyEvaluator implements FitnessEvaluator {

        private static final double H_OPT    = 800.0;   // km — best access height
        private static final double MAX_RATE = 100.0;   // Mbps conceptually

        @Override
        public Evaluation evaluate(double[] x) {
            double h     = x[0];  // km
            double inc   = x[1];  // deg
            double anomalySep = x[2];  // deg
            double pTx   = x[3];  // W

            // ── Synthetic access model ────────────────────────────────────────
            // Peak at H_OPT, degrades quadratically away; inclination shifts peak slightly
            double hNorm   = (h - H_OPT) / H_OPT;
            double incBonus = Math.sin(Math.toRadians(inc)) * 5.0; // small inclination bonus
            double access  = 65.0 - 30.0 * hNorm * hNorm + incBonus;
            access = Math.min(100.0, Math.max(0.0, access));

            // ── Synthetic data-rate model ─────────────────────────────────────
            // Inversely proportional to distance (h approximates slant range for simplicity)
            // and linearly proportional to TX power (simplified link budget)
            double pathLoss   = Math.pow(h / H_OPT, 2.0);
            double rateLinear = (pTx / P_MAX) / pathLoss;    // 0..∞
            double ratePct    = 100.0 * Math.min(1.0, rateLinear);

            // Add a small anomalySep-dependent ripple to create a non-trivial landscape
            ratePct += 5.0 * Math.sin(Math.toRadians(anomalySep));
            ratePct = Math.min(100.0, Math.max(0.0, ratePct));

            // No need to compute profit here — the optimizer does it
            return new Evaluation(x, access, ratePct);
        }
    }

}

