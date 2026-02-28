package saoptimization;

import java.util.List;

/**
 * Final output of a {@link SimulatedAnnealingOptimizer} run.
 *
 * @param bestVector      [h, inc, phase, pTx] at the global best
 * @param bestEvaluation  metrics at the best solution
 * @param totalEvaluations  wall count of evaluator calls made
 * @param history         full per-iteration history (may be empty if disabled)
 */
public record Result(
        double[]          bestVector,
        Evaluation        bestEvaluation,
        int               totalEvaluations,
        List<HistoryRecord> history
) {}
