package saoptimization;

/**
 * Snapshot of one SA iteration, stored for post-run analysis / plotting.
 *
 * CSV columns (in order):
 *   iteration, temperature,
 *   currentProfit, currentAccessPct, currentDataRatePct,
 *   currentH, currentInc, currentPhase, currentPTx,
 *   bestProfit, bestAccessPct, bestDataRatePct,
 *   bestH, bestInc, bestPhase, bestPTx
 */
public record HistoryRecord(
        int    iteration,
        double temperature,
        double currentProfit,
        double currentAccessPct,
        double currentDataRatePct,
        double[] currentVector,   // [h, inc, phase, pTx]
        double bestProfit,
        double bestAccessPct,
        double bestDataRatePct,
        double[] bestVector       // [h, inc, phase, pTx]
) {

    public static final String CSV_HEADER =
            "iteration,temperature," +
            "currentProfit,currentAccessPct,currentDataRatePct," +
            "currentH,currentInc,currentPhase,currentPTx," +
            "bestProfit,bestAccessPct,bestDataRatePct," +
            "bestH,bestInc,bestPhase,bestPTx";

    public String toCsvRow() {
        return String.format(java.util.Locale.US,
                "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                iteration, temperature,
                currentProfit, currentAccessPct, currentDataRatePct,
                currentVector[0], currentVector[1], currentVector[2], currentVector[3],
                bestProfit, bestAccessPct, bestDataRatePct,
                bestVector[0], bestVector[1], bestVector[2], bestVector[3]);
    }
}
