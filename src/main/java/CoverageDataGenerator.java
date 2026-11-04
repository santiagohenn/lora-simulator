import java.io.File;
import java.util.List;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

/**
 * Single coverage computation of a baseline constellation whose satellites are re-phased with a fixed
 * in-plane anomaly separation.
 *
 * <p>Writes {@code coverage_<hash>.csv}, {@code surface_metrics_<hash>.json} and, if enabled in the
 * configuration, snapshot/geographic files to the output directory. These are the inputs of
 * {@code scripts/plot_coverage.py}, {@code scripts/plot_snapshot.py} and {@code scripts/plot_snapshot_polar.py}.
 *
 * <p>Usage: {@code CoverageDataGenerator [config.properties] [roi.csv] [satellites.csv] [outputDir] [anomalySepDeg]}
 */
public class CoverageDataGenerator {

    static final String DEFAULT_CONFIG = "config/optimization_argentina_eez.properties";
    static final String DEFAULT_ROI = "inputs/rois/argentina_eez.csv";
    static final String DEFAULT_SATELLITES = "inputs/satellites/constellation_baseline.csv";
    static final String DEFAULT_OUTPUT_DIR = "results/optimization/";
    static final double DEFAULT_ANOMALY_SEPARATION_DEG = 2.0;

    public static void main(String[] args) {

        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG;
        String roiPath = args.length > 1 ? args[1] : DEFAULT_ROI;
        String satellitesPath = args.length > 2 ? args[2] : DEFAULT_SATELLITES;
        String outputDir = AccessVsRAANAndPhase.withTrailingSlash(args.length > 3 ? args[3] : DEFAULT_OUTPUT_DIR);
        double anomalySep = args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_ANOMALY_SEPARATION_DEG;
        new File(outputDir).mkdirs();

        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(configPath);

        List<double[]> ROI = FileUtils.file2DoubleList(roiPath);
        constellationCoverageComputer.setOutputPath(outputDir);
        constellationCoverageComputer.setROI(ROI);

        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitesPath);

        // Satellite k gets anomaly a_0 + k * anomalySep [deg]
        double startingAnomaly = satelliteList.get(0).getElements().getAnomaly();
        for (int satIdx = 0; satIdx < satelliteList.size(); satIdx++) {
            satelliteList.get(satIdx).getElements()
                    .setAnomaly(startingAnomaly + satIdx * anomalySep);
        }

        constellationCoverageComputer.setSatelliteList(satelliteList);

        Log.info("Running constellation coverage ... ");
        constellationCoverageComputer.run();
        System.out.println("Simulation completed. " + "Hash: " + constellationCoverageComputer.getSimulationHash());
        Log.info("... done!");

    }

}
