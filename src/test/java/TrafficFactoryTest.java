import org.apache.commons.math3.distribution.PoissonDistribution;
import org.junit.Test;

public class TrafficFactoryTest {

    @Test
    public void PoissonIntervalTest() {
        double lambda = 1;
        double probability = 0.99;

        PoissonDistribution poisson = new PoissonDistribution(lambda);

        double cdf = 0.0;
        int t = 0;
        while (cdf < probability) {
            cdf += poisson.probability(t);
            t += 1;
        }
        System.out.println("time [s]: " + (t / lambda));
        System.out.println("time [ms]: " + ((long) (1000.0 * t) / lambda));
    }

}
