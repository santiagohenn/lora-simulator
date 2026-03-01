import java.util.List;

import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.utilities.FileUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

public class CoverageDataGenerator {

    public static void main(String[] args) {

        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(
                "C:/Projects/lora-simulator/src/test/resources/config.d3co.properties");

        // Load from file
        List<double[]> ROI = FileUtils.file2DoubleList("C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv");
        constellationCoverageComputer.setOutputPath("C:/projects/results/pareto/");
        constellationCoverageComputer.setROI(ROI);

        List<Satellite> satelliteList = Utils
                .satellitesFromFile("C:/Projects/lora-simulator/inputs/satellites/constellation_baseline.csv");

        double anomalySep = 6;
        double raan = 10;
        double startingAnomaly = satelliteList.get(0).getElements().getAnomaly();

        for (int satIdx = 0; satIdx < satelliteList.size(); satIdx++) {
            satelliteList.get(satIdx).getElements()
                    .setAnomaly(startingAnomaly + satIdx * anomalySep);
            satelliteList.get(satIdx).getElements().setRightAscension(raan);
        }

        constellationCoverageComputer.setSatelliteList(satelliteList);

        Log.info("Running constellation coverage ... ");
        constellationCoverageComputer.run();
        System.out.println("Simulation completed. " + "Hash: " + constellationCoverageComputer.getSimulationHash());
        Log.info("... done!");

    }

}
