package deoptimization;
/**
 * Contract between the Differential Evolution engine and the simulation black-box.
 *
 * <h3>How to implement this for your project</h3>
 * <ol>
 *   <li>Create a class that {@code implements FitnessEvaluator}.</li>
 *   <li>In its constructor, load any resources that are shared across evaluations
 *       (e.g., Orekit data, ROI CSV file) so they are not reloaded on every call.</li>
 *   <li>In {@link #evaluate(double[])}, unpack {@code x} and call your simulation
 *       pipeline.  Then return an {@link Evaluation} with the raw metric values.</li>
 *   <li><strong>Do NOT compute the combined profit inside the evaluator.</strong>
 *       The optimizer will apply the configured weights and attach the correct profit
 *       via {@link Evaluation#withProfit(double)}. You just return accessPct and
 *       dataRatePct.</li>
 *   <li>If the simulation throws an exception or produces clearly invalid output,
 *       return {@code Evaluation.failed(x, reason)} — the optimizer will treat it
 *       as very low fitness and continue gracefully.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * The optimizer calls {@link #evaluate(double[])} sequentially (single-threaded) as
 * written. If you later parallelise it, ensure your implementation is thread-safe or use
 * one evaluator instance per thread.
 *
 * <h3>Minimal implementation template</h3>
 * <pre>{@code
 * public class MySimulatorEvaluator implements FitnessEvaluator {
 *
 *     // ── HOOK 1: inject any shared resources here ─────────────────────────
 *     private final String configPath;
 *
 *     public MySimulatorEvaluator(String configPath) {
 *         this.configPath = configPath;
 *         // e.g. initialise Orekit, load ROI file, etc.
 *     }
 *
 *     @Override
 *     public Evaluation evaluate(double[] x) {
 *         // ── HOOK 2: unpack decision vector ──────────────────────────────
 *         double h     = x[0];   // satellite height
 *         double inc   = x[1];   // inclination (degrees)
 *         double anomalySep = x[2];   // anomaly separation (degrees)
 *         double pTx   = x[3];   // TX power
 *
 *         try {
 *             // ── HOOK 3: run your simulation pipeline ─────────────────────
 *             // Example (fill in with your actual classes):
 *             //   MySatelliteSimulation sim = new MySatelliteSimulation(h, inc, anomalySep, pTx, configPath);
 *             //   sim.run();
 *             //   double accessPct   = sim.getAverageAccessPct();
 *             //   double dataRatePct = computeDataRatePct(h, anomalySep, pTx, inc);
 *
 *             double accessPct   = 0.0;  // ← replace with your simulator call
 *             double dataRatePct = computeDataRatePct(h, anomalySep, pTx, inc);
 *
 *             return new Evaluation(x, accessPct, dataRatePct);
 *
 *         } catch (Exception e) {
 *             // Simulation failed: return a sentinel so DE discards this individual
 *             return Evaluation.failed(x, e.getMessage());
 *         }
 *     }
 *
 *     // ── HOOK 4: implement your data-rate model ────────────────────────────
 *     // Replace the body with your actual formula / file-read result.
 *     // Parameters are passed explicitly so you can see what the optimizer sends.
 *     private double computeDataRatePct(double h, double anomalySep, double pTx, double inc) {
 *         // TODO: implement using your ISL link-budget model or read from output file.
 *         // Return a value in [0, 100] where 100 = max_data_rate.
 *         return 50.0; // placeholder
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface FitnessEvaluator {

    /**
     * Evaluates the decision vector {@code x} by running (or querying) the simulation.
     *
     * <p>The optimizer guarantees that all elements of {@code x} lie within the configured
     * {@link Bounds} before this method is called. You do not need to validate bounds.
     *
     * <p>The returned {@link Evaluation}'s {@code profit} field need not be set (it can be
     * {@code NaN}); the optimizer overwrites it using the configured weights. Only
     * {@code accessPct} and {@code dataRatePct} are read by the scalarisation logic.
     *
     * <p><strong>Contract:</strong>
     * <ul>
     *   <li>Never return {@code null} — return {@link Evaluation#failed(double[], String)}
     *       instead.</li>
     *   <li>{@code accessPct} and {@code dataRatePct} must be in [0, 100] for sensible
     *       results; they are clamped internally but values outside this range indicate
     *       a modelling error.</li>
     *   <li>The array {@code x} may be re-used by the caller after this method returns,
     *       so copy it if you need to store it asynchronously.</li>
     * </ul>
     *
     * @param x decision vector [h, inc, anomalySep, pTx] with all values within bounds
     * @return an {@link Evaluation} with at least {@code accessPct} and {@code dataRatePct}
     *         filled in, or {@link Evaluation#failed(double[], String)} on error
     */
    Evaluation evaluate(double[] x);
}
