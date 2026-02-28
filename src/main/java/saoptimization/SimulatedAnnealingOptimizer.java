package saoptimization;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SimulatedAnnealingOptimizer {

    private final double[]          lo;
    private final double[]          hi;
    private final FitnessEvaluator  evaluator;

    // SA hyper-parameters (tune T0, alpha, stepFraction first)
    private double T0               = 5.0;
    private double alpha            = 0.995;
    private double stepFraction     = 0.10;
    private int    maxEvaluations   = 2_000;
    private int    earlyStopPatience = 300;
    private double earlyStopEpsilon = 1e-4;

    // Profit weights
    private double wAccess  = 1.0;
    private double wRate    = 1.0;
    private double wPower   = 1.0;

    private static final double EPSILON = 1e-9;

    private String  csvOutputPath = null;
    private Long    rngSeed       = null;
    private double[] initialX     = null;

    // ── Constructor ──────────────────────────────────────────────────────────

    public SimulatedAnnealingOptimizer(double[] lo, double[] hi, FitnessEvaluator evaluator) {
        if (lo.length != 4 || hi.length != 4)
            throw new IllegalArgumentException("Bounds arrays must have length 4.");
        this.lo        = Arrays.copyOf(lo, 4);
        this.hi        = Arrays.copyOf(hi, 4);
        this.evaluator = evaluator;
    }

    // ── Fluent setters ───────────────────────────────────────────────────────

    public SimulatedAnnealingOptimizer withT0(double v)                       { T0 = v; return this; }
    public SimulatedAnnealingOptimizer withAlpha(double v)                    { alpha = v; return this; }
    public SimulatedAnnealingOptimizer withStepFraction(double v)             { stepFraction = v; return this; }
    public SimulatedAnnealingOptimizer withMaxEvaluations(int v)              { maxEvaluations = v; return this; }
    public SimulatedAnnealingOptimizer withEarlyStopPatience(int v)           { earlyStopPatience = v; return this; }
    public SimulatedAnnealingOptimizer withEarlyStopEpsilon(double v)         { earlyStopEpsilon = v; return this; }
    public SimulatedAnnealingOptimizer withWeights(double wA, double wR, double wP) {
        wAccess = wA; wRate = wR; wPower = wP; return this;
    }
    public SimulatedAnnealingOptimizer withCsvOutput(String path)             { csvOutputPath = path; return this; }
    public SimulatedAnnealingOptimizer withSeed(long seed)                    { rngSeed = seed; return this; }
    public SimulatedAnnealingOptimizer withInitialX(double[] x0)              { initialX = Arrays.copyOf(x0, x0.length); return this; }

    // ── Profit formula ───────────────────────────────────────────────────────

    /**
     * profit = exp( (wAccess·ln(accessPct+ε) + wRate·ln(dataRatePct+ε)) / (wAccess+wRate) )
     *          − wPower · (pTx − lo[3]) / (hi[3] − lo[3])
     */
    private double computeProfit(Evaluation eval, double pTx) {
        double wSum   = wAccess + wRate;
        double core   = Math.exp(
                (wAccess * Math.log(eval.accessPct()   + EPSILON)
               + wRate   * Math.log(eval.dataRatePct() + EPSILON)) / wSum);
        double pNorm  = (pTx - lo[3]) / (hi[3] - lo[3]);
        return core - wPower * pNorm;
    }

    // ── Main SA loop ─────────────────────────────────────────────────────────

    public Result run() {
        Random            rng     = (rngSeed != null) ? new Random(rngSeed) : new Random();
        List<HistoryRecord> history = new ArrayList<>();

        double[] current = (initialX != null) ? clampedCopy(initialX) : randomFeasible(rng);

        Evaluation currentEval   = safeEvaluate(current);
        double     currentProfit = computeProfit(currentEval, current[3]);

        double[]   bestX      = Arrays.copyOf(current, 4);
        Evaluation bestEval   = currentEval;
        double     bestProfit = currentProfit;

        double T             = T0;
        int    evalCount     = 1;
        int    noImprovCount = 0;

        double[] candidate = new double[4];

        BufferedWriter csv = openCsvWriter();
        try {
            writeRow(csv, history, 0, T, currentProfit, currentEval, current, bestProfit, bestEval, bestX);

            while (evalCount < maxEvaluations && noImprovCount < earlyStopPatience) {

                double tRatio = T / T0;
                for (int i = 0; i < 4; i++) {
                    double sigma = stepFraction * tRatio * (hi[i] - lo[i]);
                    candidate[i] = current[i] + sigma * rng.nextGaussian();
                }
                BoundsUtils.reflectAll(candidate, lo, hi);

                Evaluation candEval   = safeEvaluate(candidate);
                double     candProfit = computeProfit(candEval, candidate[3]);
                evalCount++;

                double  delta  = candProfit - currentProfit;
                boolean accept = (delta >= 0.0) || (rng.nextDouble() < Math.exp(delta / T));

                if (accept) {
                    System.arraycopy(candidate, 0, current, 0, 4);
                    currentEval   = candEval;
                    currentProfit = candProfit;
                }

                if (currentProfit > bestProfit + earlyStopEpsilon) {
                    bestProfit    = currentProfit;
                    bestEval      = currentEval;
                    bestX         = Arrays.copyOf(current, 4);
                    noImprovCount = 0;
                } else {
                    noImprovCount++;
                }

                T *= alpha;

                writeRow(csv, history, evalCount, T, currentProfit, currentEval, current,
                         bestProfit, bestEval, bestX);
            }
        } finally {
            closeCsv(csv);
        }

        return new Result(bestX, bestEval, evalCount, history);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private double[] randomFeasible(Random rng) {
        double[] x = new double[4];
        for (int i = 0; i < 4; i++) x[i] = lo[i] + rng.nextDouble() * (hi[i] - lo[i]);
        return x;
    }

    private double[] clampedCopy(double[] src) {
        double[] x = Arrays.copyOf(src, 4);
        BoundsUtils.reflectAll(x, lo, hi);
        return x;
    }

    private Evaluation safeEvaluate(double[] x) {
        try {
            Evaluation e = evaluator.evaluate(x);
            return new Evaluation(e.accessPct(), e.dataRatePct(), computeProfit(e, x[3]));
        } catch (Exception ex) {
            System.err.println("[SA] Evaluation failed: " + ex.getMessage());
            return Evaluation.FAILED;
        }
    }

    private void writeRow(BufferedWriter w, List<HistoryRecord> history,
            int iter, double T,
            double curProfit, Evaluation curEval, double[] curVec,
            double bstProfit, Evaluation bstEval, double[] bstVec) {

        HistoryRecord rec = new HistoryRecord(
                iter, T,
                curProfit, curEval.accessPct(), curEval.dataRatePct(), Arrays.copyOf(curVec, 4),
                bstProfit, bstEval.accessPct(), bstEval.dataRatePct(), Arrays.copyOf(bstVec, 4));
        history.add(rec);

        if (w != null) {
            try { w.write(rec.toCsvRow()); w.newLine(); }
            catch (IOException e) { System.err.println("[SA] CSV write error: " + e.getMessage()); }
        }
    }

    private BufferedWriter openCsvWriter() {
        if (csvOutputPath == null) return null;
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputPath));
            bw.write(HistoryRecord.CSV_HEADER);
            bw.newLine();
            return bw;
        } catch (IOException e) {
            System.err.println("[SA] Cannot open CSV: " + e.getMessage());
            return null;
        }
    }

    private void closeCsv(BufferedWriter w) {
        if (w == null) return;
        try { w.close(); } catch (IOException ignored) {}
    }
}
