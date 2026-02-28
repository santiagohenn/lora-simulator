package saoptimization;

/**
 * Result of a single black-box evaluation.
 *
 * @param accessPct    coverage access percentage in [0, 100]
 * @param dataRatePct  normalised ISL data-rate in [0, 100]
 * @param profit       scalar profit (higher is better)
 */
public record Evaluation(double accessPct, double dataRatePct, double profit) {

    /** Sentinel used when the simulation throws an exception. */
    public static final Evaluation FAILED = new Evaluation(0.0, 0.0, Double.NEGATIVE_INFINITY);
}
