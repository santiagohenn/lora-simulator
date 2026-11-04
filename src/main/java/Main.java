import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Convenience launcher that runs the throughput experiment for the three channel-access policies
 * (pure ALOHA, coverage-aware, coverage-aware without duty-cycle restriction) with one shared
 * parameter set, writing to {@code results/<policy>/}. The layout matches what
 * {@code scripts/plot_avg_sim_vs_model.py} expects.
 *
 * <p>Usage (from the repository root):
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=Main                         # all three policies
 *   mvn -q compile exec:java -Dexec.mainClass=Main -Dexec.args="pure-aloha" # a single policy
 * </pre>
 *
 * <p>Required inputs: {@code inputs/satellites.csv} (not versioned, see README.md), the coverage
 * configuration {@code config/throughput_experiment.properties} and Orekit data in {@code orekit-data/}.
 * For direct control over every parameter use
 * {@link LoraConstellationSimulator} instead.
 *
 * <p>NOTE FOR AUTHORS: the parameter values below are the ones that were active in this launcher when
 * the repository was prepared for publication. Confirm that they match the configuration reported in
 * the paper.
 */
public class Main {

    static final String RESULTS_ROOT = "results/";
    static final String SATELLITES_FILE = "inputs/satellites.csv";
    static final String COVERAGE_CONFIG = "config/throughput_experiment.properties";

    /** Parameters shared by the three policies. */
    static final String[] SHARED_OPTIONS = {
            "--save-analysis-datasets",
            "-s", SATELLITES_FILE,
            "-cov-cfg", COVERAGE_CONFIG,
            "-time-step", "15.0",                 // propagator time step [s]
            "-time-on-air", "0.5",                // LoRa frame time on air [s]
            "-duty-cycle-percentage", "1.0",      // regulatory duty cycle [%]
            "-nodes", "50,80,10",                 // start,end,step number of end devices
            "-roi", "-21.0,-58.0,7.1946,150",     // spherical cap: lat, lon, radius [deg], polygon segments
            "-vt", "20.0",                        // minimum elevation [deg]
            "-runs", "20"                         // Monte Carlo repetitions per node count
    };

    /** Policy folder name -> CLI flag. */
    static final String[][] POLICIES = {
            {"pure-aloha", "-simulate-pure-aloha"},
            {"coverage-aware", "-simulate-with-ca"},
            {"coverage-aware-nr", "-simulate-with-ca-nr"}
    };

    public static void main(String[] args) {
        List<String> selected = Arrays.asList(args);
        for (String[] policy : POLICIES) {
            if (!selected.isEmpty() && !selected.contains(policy[0])) {
                continue;
            }
            List<String> options = new ArrayList<>();
            options.add(policy[1]);
            options.add("-o");
            options.add(RESULTS_ROOT + policy[0] + "/");
            options.addAll(Arrays.asList(SHARED_OPTIONS));
            LoraConstellationSimulator.main(options.toArray(new String[0]));
        }
    }

}
