package saoptimization;

/**
 * Black-box evaluator contract.
 *
 * <p>Implementations must:
 * <ol>
 *   <li>Run the constellation simulation for the given parameter vector.</li>
 *   <li>Parse the output files to obtain {@code accessPct}.</li>
 *   <li>Call {@code computeDataRatePct} to obtain {@code dataRatePct}.</li>
 *   <li>Compute a scalar {@code profit} and return an {@link Evaluation}.</li>
 * </ol>
 *
 * <p>If the simulation fails for any reason, throw any {@link Exception};
 * the optimizer will catch it and treat the candidate as {@link Evaluation#FAILED}.
 *
 * @param x  decision vector [h (km), inc (deg), phase (deg), pTx (dBm)]
 */
@FunctionalInterface
public interface FitnessEvaluator {
    Evaluation evaluate(double[] x) throws Exception;
}
