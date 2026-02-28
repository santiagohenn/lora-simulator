package deoptimization;
import java.util.Arrays;
import java.util.Locale;

/**
 * Defines the search space (lower/upper bounds) for each decision variable,
 * with optional per-dimension "circular" flag for variables that wrap around.
 *
 * <p><b>Decision vector layout for the satellite problem:</b>
 * <pre>
 *   index 0 — h      (satellite height, km)
 *   index 1 — inc    (inclination, degrees)
 *   index 2 — anomalySep  (inter-satellite anomaly spacing, degrees)  ← linear
 *   index 3 — pTx    (transmission power, dBm)
 * </pre>
 *
 * <p><b>Boundary handling strategy:</b>
 * <ul>
 *   <li>Linear dimensions (h, inc, anomalySep, pTx): <em>reflection</em> — the mutant value bounces off
 *       the violated boundary, keeping the search near valid regions and avoiding clustering
 *       on edges (which clamping would cause).</li>
 * </ul>
 */
public final class Bounds {

    public final int    dim;
    public final double[] lower;
    public final double[] upper;
    /** True for dimensions that wrap around (circular topology). */
    public final boolean[] circular;

    /**
     * Creates bounds.
     *
     * @param lower    lower bound per dimension (inclusive)
     * @param upper    upper bound per dimension (exclusive for circular, inclusive otherwise)
     * @param circular true for dimensions that should wrap modulo (upper - lower)
     */
    public Bounds(double[] lower, double[] upper, boolean[] circular) {
        if (lower.length != upper.length || lower.length != circular.length) {
            throw new IllegalArgumentException("lower, upper, circular must have identical length");
        }
        for (int i = 0; i < lower.length; i++) {
            if (lower[i] >= upper[i]) {
                throw new IllegalArgumentException(
                    "lower[" + i + "]=" + lower[i] + " must be < upper[" + i + "]=" + upper[i]);
            }
        }
        this.dim      = lower.length;
        this.lower    = Arrays.copyOf(lower, lower.length);
        this.upper    = Arrays.copyOf(upper, upper.length);
        this.circular = Arrays.copyOf(circular, circular.length);
    }

    /** Convenience constructor: all dimensions are linear (no wrapping). */
    public Bounds(double[] lower, double[] upper) {
        this(lower, upper, new boolean[lower.length]); // all false
    }

    // -------------------------------------------------------------------------
    // Factory for the satellite problem
    // -------------------------------------------------------------------------

    /**
     * Creates the parameter space for the 4-D satellite optimisation problem.
     *
     * <p>Fill in the constant values that match your simulation units.
     *
     * @param hMin   minimum satellite height  (km)
     * @param hMax   maximum satellite height  (km)
     * @param iMin   minimum inclination (degrees)
     * @param iMax   maximum inclination (degrees)
     * @param anomalySepMin minimum anomaly separation between satellites (degrees)
     * @param anomalySepMax maximum anomaly separation between satellites (degrees)
     * @param pMin   minimum TX power (dBm)
     * @param pMax   maximum TX power (dBm)
     * @return a Bounds instance ready for use with {@link DifferentialEvolutionOptimizer}
     */
    public static Bounds forSatelliteProblem(
            double hMin, double hMax,
            double iMin, double iMax,
            double anomalySepMin, double anomalySepMax,
            double pMin, double pMax) {

        // x = [h, inc, anomalySep, pTx]
        double[]  lo  = { hMin,  iMin,   anomalySepMin, pMin };
        double[]  hi  = { hMax,  iMax,   anomalySepMax, pMax };
        // anomalySep is an inter-satellite spacing (a delta, not a position),
        // so it is never circular: 0° and 360° separation are distinct configurations.
        boolean[] circ = { false, false, false, false };

        return new Bounds(lo, hi, circ);
    }

    // -------------------------------------------------------------------------
    // Bound enforcement
    // -------------------------------------------------------------------------

    /**
     * Projects {@code value} into [lower[dim], upper[dim]] using the correct strategy
     * for that dimension.
     *
     * <p>For <em>linear</em> dimensions, reflection is applied once per boundary violation.
     * If a single reflection is still out of bounds (can happen with very large F) the
     * value is clamped to the boundary. This keeps the search distribution smooth without
     * allowing unbounded drift.
     *
     * <p>For <em>circular</em> dimensions, modulo wrapping is applied (full periodicity).
     *
     * @param d     dimension index
     * @param value raw (possibly out-of-bounds) value
     * @return enforced value guaranteed to lie in [lower[d], upper[d]]
     */
    public double enforce(int d, double value) {
        if (circular[d]) {
            return wrapCircular(value, lower[d], upper[d]);
        } else {
            return reflectLinear(value, lower[d], upper[d]);
        }
    }

    /**
     * Applies {@link #enforce(int, double)} to every element of {@code x} in-place.
     * Returns the same array for chaining.
     */
    public double[] enforceAll(double[] x) {
        for (int d = 0; d < dim; d++) {
            x[d] = enforce(d, x[d]);
        }
        return x;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Modulo wrap: maps {@code v} into [lo, hi) with full periodicity.
     * Works correctly for negative values.
     */
    private static double wrapCircular(double v, double lo, double hi) {
        double range = hi - lo;
        // shift so that lo maps to 0, apply Java-safe modulo, then shift back
        double shifted = v - lo;
        shifted = shifted - Math.floor(shifted / range) * range;
        return lo + shifted;
    }

    /**
     * Reflection: if {@code v} violates the boundary, reflect it back once.
     * After reflection, clamp to guarantee feasibility (protects against very large F).
     */
    private static double reflectLinear(double v, double lo, double hi) {
        if (v < lo) {
            v = 2.0 * lo - v;   // reflect off lower wall
        }
        if (v > hi) {
            v = 2.0 * hi - v;   // reflect off upper wall
        }
        // Final clamp — handles the rare case where a single reflection overshoots the far wall
        return Math.min(hi, Math.max(lo, v));
    }

    // -------------------------------------------------------------------------
    // Sampling
    // -------------------------------------------------------------------------

    /**
     * Samples a random point uniformly within the bounds.
     *
     * @param rng seeded Random instance
     * @return fresh array of length {@code dim}
     */
    public double[] sample(java.util.Random rng) {
        double[] x = new double[dim];
        for (int d = 0; d < dim; d++) {
            x[d] = lower[d] + rng.nextDouble() * (upper[d] - lower[d]);
        }
        return x;
    }

    /** Returns the range (width) of dimension {@code d}. */
    public double range(int d) {
        return upper[d] - lower[d];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Bounds{dim=").append(dim).append(", ");
        for (int d = 0; d < dim; d++) {
            sb.append(String.format(Locale.US, "[%.4g, %.4g]%s%s ",
                lower[d], upper[d],
                circular[d] ? "(circ)" : "",
                d < dim - 1 ? "," : ""));
        }
        return sb.append('}').toString();
    }
}
