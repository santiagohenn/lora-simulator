import java.util.List;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.geometry.GeographicTools;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

public class AreaOptimization {

    public static void main(String[] args) {

        // String[] options = { "--simulate-pure-aloha", "--save-analysis-datasets",
        // "-o", "C:/projects/results/pure-aloha/",
        // "-s", "./inputs/constellation_caribbean.csv",
        // "-cov-cfg", "./inputs/config.d3co.properties",
        // "-time-step", "15.0",
        // "-time-on-air", "0.5",
        // "-duty-cycle-percentage", "1.0",
        // "-nodes", "50,80,10",
        // "-roi", "-21.0,-58.0,7.1946,150",
        // "-vt", "20.0",
        // "-runs", "20" };

        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(
                "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties");

        // List<double[]> ROI = GeographicTools.computeSphericalCap(-54.0, -58.0,
        // 7.1946, 150);

        // Load from file
        List<double[]> ROI = FileUtils.file2DoubleList("C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv");

        // GeographicTools geographicTools = new GeographicTools();
        // double roiSurface = geographicTools.computeNonEuclideanSurface(ROI);
        // System.out.println("ROI surface: " + roiSurface + " km^2");

        constellationCoverageComputer.setOutputPath("C:/projects/results/optimization/");
        constellationCoverageComputer.setROI(ROI);

        List<Satellite> satelliteList = Utils
                .satellitesFromFile("C:/Projects/lora-simulator/inputs/satellites/constellation_baseline.csv");

        double anomalySep = 2;
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

        // LoraConstellationSimulator.main(options);
        // System.out.println("Simulation completed. " + "Hash: " +
        // LoraConstellationSimulator.simulationHash);

    }

}
