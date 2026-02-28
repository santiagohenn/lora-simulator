package saoptimization;

/**
 * Utility for keeping decision variables inside their box constraints using
 * reflection (bounce off the wall instead of clamping).
 *
 * <p>Reflection is preferred over clamping because it preserves step-size
 * statistics near boundaries and avoids accumulation on the wall.
 */
public final class BoundsUtils {

    private BoundsUtils() {}

    /**
     * Reflects {@code value} into {@code [lo, hi]} by bouncing it off both walls
     * as many times as needed.
     *
     * <pre>
     * Example: lo=0, hi=10, value=12  →  8   (bounced off 10)
     *          lo=0, hi=10, value=-3  →  3   (bounced off 0)
     * </pre>
     *
     * @throws IllegalArgumentException if {@code lo >= hi}
     */
    public static double reflect(double value, double lo, double hi) {
        if (lo >= hi) throw new IllegalArgumentException("lo must be < hi");
        double range = hi - lo;
        // Shift so the interval is [0, range]
        double v = value - lo;
        // Fold into [0, 2*range] using modulo, then mirror upper half
        v = v % (2.0 * range);
        if (v < 0.0) v += 2.0 * range;
        if (v > range) v = 2.0 * range - v;
        return lo + v;
    }

    /** Reflects every element of {@code x} into its bounds, in-place. */
    public static void reflectAll(double[] x, double[] lo, double[] hi) {
        for (int i = 0; i < x.length; i++) {
            x[i] = reflect(x[i], lo[i], hi[i]);
        }
    }
}
