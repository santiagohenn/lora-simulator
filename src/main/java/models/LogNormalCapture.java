package models;

/**
 * LogNormalCapture.java
 *
 * Ready-to-run example that computes the probability that node A's packet
 * is "captured" (decoded) in presence of node B, assuming:
 *   - Path loss: FSPL @ d0 + 10 * n * log10(d/d0)
 *   - Shadowing: log-normal with std dev sigma (dB)
 *   - Capture rule: P_r(A) - P_r(B) > Delta (dB)
 *
 * It also supports correlated shadowing: rho(d_AB) = exp(-d_AB / d_corr).
 *
 * Compile:
 *   javac LogNormalCapture.java
 * Run:
 *   java LogNormalCapture
 */

public class LogNormalCapture {

    // Speed of light (m/s)
    private static final double C = 299792458.0;

    public static void main(String[] args) {
        // Example parameters (Europe LoRa-ish defaults)
        double freqHz = 868e6;         // 868 MHz
        double ptDbm = 14.0;           // transmit power (dBm)
        double n = 2.7;                // path-loss exponent
        double sigmaDb = 7.0;          // shadowing std dev (dB)
        double captureDeltaDb = 6.0;   // required margin (dB) for capture

        double dA = 100.0;             // distance node A -> receiver (m)
        double dB = 200.0;             // distance node B -> receiver (m)

        // Basic (independent shadowing) probability
        double pIndependent = captureProbability(
            dA, dB, ptDbm, freqHz, n, sigmaDb, captureDeltaDb);

        // Example with correlated shadowing:
        double dAB = 10.0;    // distance between A and B (m)
        double dCorr = 50.0;  // decorrelation distance (m) used in rho = exp(-dAB/dCorr)
        double pCorrelated = captureProbability(
            dA, dB, ptDbm, freqHz, n, sigmaDb, captureDeltaDb, dAB, dCorr);

        // Print some useful diagnostic values and results
        double pl1 = pathLoss(freqHz, dA, n, 1.0);
        double pl2 = pathLoss(freqHz, dB, n, 1.0);
        double mu1 = ptDbm - pl1;
        double mu2 = ptDbm - pl2;
        double deltaMu = mu1 - mu2; // mean (dB) of (PrA - PrB)

        System.out.printf("Parameters: freq=%.0f Hz, Pt=%.2f dBm, n=%.2f, sigma=%.2f dB, captureDelta=%.2f dB%n",
                          freqHz, ptDbm, n, sigmaDb, captureDeltaDb);
        System.out.printf("Distances: dA=%.1f m, dB=%.1f m%n", dA, dB);
        System.out.printf("Pathloss @ dA = %.3f dB, @ dB = %.3f dB%n", pl1, pl2);
        System.out.printf("Mean received: muA=%.3f dBm, muB=%.3f dBm, deltaMu(muA-muB)=%.3f dB%n",
                          mu1, mu2, deltaMu);
        System.out.printf("Capture probability (independent shadowing): %.6f%n", pIndependent);
        System.out.printf("Assuming dAB=%.1f m, d_corr=%.1f m -> P(capture) (correlated): %.6f%n",
                          dAB, dCorr, pCorrelated);
    }

    /**
     * Compute capture probability assuming independent shadowing (default).
     *
     * P(capture) = Q( (Delta - (muA - muB)) / sqrt(2 * sigma^2) )
     *
     * where Q is the Gaussian tail function.
     */
    public static double captureProbability(double dA, double dB,
                                            double txPowerDbm, double freqHz,
                                            double pathLossExponent,
                                            double sigmaDb,
                                            double captureThresholdDb) {
        // call the more general method with no correlation information
        return captureProbability(dA, dB, txPowerDbm, freqHz, pathLossExponent,
                                  sigmaDb, captureThresholdDb, -1.0, -1.0);
    }

    /**
     * General capture probability with optional correlated shadowing.
     *
     * If dAB > 0 and decorrelationDistance > 0 then we compute:
     *   rho = exp(-dAB / decorrelationDistance)
     * and variance of (X1 - X2) becomes 2*sigma^2*(1 - rho).
     *
     * If no correlation info provided (dAB <= 0 or decorrelationDistance <= 0)
     * we assume rho = 0 (independent shadowing).
     */
    public static double captureProbability(double dA, double dB,
                                            double txPowerDbm, double freqHz,
                                            double pathLossExponent,
                                            double sigmaDb,
                                            double captureThresholdDb,
                                            double dAB, double decorrelationDistance) {
        if (dA <= 0 || dB <= 0) {
            throw new IllegalArgumentException("Distances must be > 0");
        }

        // 1) compute path loss at both distances (d0=1 m)
        double plA = pathLoss(freqHz, dA, pathLossExponent, 1.0);
        double plB = pathLoss(freqHz, dB, pathLossExponent, 1.0);

        // 2) mean received powers (dBm), assuming same tx power for both nodes
        double muA = txPowerDbm - plA;
        double muB = txPowerDbm - plB;
        double deltaMu = muA - muB; // mean of (PrA - PrB) in dB

        // 3) shadowing correlation -> rho in [0,1)
        double rho = 0.0;
        if (dAB > 0.0 && decorrelationDistance > 0.0) {
            rho = Math.exp(-dAB / decorrelationDistance); // simple exponential model
            if (rho < 0.0) rho = 0.0;
            if (rho > 1.0) rho = 1.0;
        }

        // 4) variance of (X_A - X_B): var = 2 * sigma^2 * (1 - rho)
        double var = 2.0 * (sigmaDb * sigmaDb) * (1.0 - rho);

        // handle degenerate (very small variance) case
        if (var < 1e-12) {
            // essentially deterministic: capture only if mean margin exceeds threshold
            return (deltaMu > captureThresholdDb) ? 1.0 : 0.0;
        }

        // 5) argument for Q-function
        double arg = (captureThresholdDb - deltaMu) / Math.sqrt(var);

        // 6) probability = Q(arg)
        return qFunction(arg);
    }

    /**
     * Free-space path loss at reference d0 plus log-distance extension:
     *   PL(d) = FSPL(d0) + 10 * n * log10(d / d0)
     *
     * We compute FSPL(d0) = 20 * log10(4 * pi * d0 * f / c)
     */
    public static double pathLoss(double freqHz, double d, double n, double d0) {
        if (d <= 0) throw new IllegalArgumentException("d must be > 0");
        if (d0 <= 0) throw new IllegalArgumentException("d0 must be > 0");
        // if d < d0 we still compute using FSPL(d) to avoid negative log10
        if (d < d0) {
            return fspl(freqHz, d);
        }
        double pl_d0 = fspl(freqHz, d0);
        return pl_d0 + 10.0 * n * Math.log10(d / d0);
    }

    /** Free-space path loss (dB): FSPL(d) = 20 log10(4*pi*d*f / c) */
    public static double fspl(double freqHz, double d) {
        return 20.0 * Math.log10((4.0 * Math.PI * d * freqHz) / C);
    }

    /** Q-function (Gaussian tail): Q(x) = 1 - Phi(x) */
    public static double qFunction(double x) {
        return 1.0 - normalCdf(x);
    }

    /** Normal CDF using erf approximation: Phi(x) = 0.5 * (1 + erf(x / sqrt(2))) */
    public static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /**
     * Approximation of the error function erf(x).
     * Uses the widely used numerical approximation (Abramowitz & Stegun / Numerical Recipes).
     */
    public static double erf(double x) {
        // Save the sign of x
        int sign = (x >= 0) ? 1 : -1;
        x = Math.abs(x);

        // Constants
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p  = 0.3275911;

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return sign * y;
    }
}

