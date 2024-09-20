import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.List;

public class Main {

    public static final double EARTH_RADIUS_M = 6371000.0;

    public static void main(String[] args) {

//        String outputPath = "C:/Projects/throughput_pipeline/case_study_south_america/network_data/";
        String outputPath = "C:/mg_data_test_550/";
        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath);

        networkSimulator.setSaveActions(false, true, false, false);

//        String satellitePath = "C:/Projects/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        String satellitePath = "C:/Projects/matlab-snippets/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        // Scenario parameters
        networkSimulator.setScenarioParameters(
                "2025-01-01T16:00:00.000",
                "2025-01-01T16:30:00.000",
                5.0,
                20
        );

        networkSimulator.setSatellites(satelliteList);
        networkSimulator.setDeploymentArea(-21.0, -58.0, 7.1946);
//        simulator.setDeploymentArea(50, 5, 3.1442);

        // Network parameters
        networkSimulator.setNetworkParameters(
                500,
                1,
                0.1,
                15000);

        networkSimulator.setNumberOfRuns(5);
        Log.info(networkSimulator.toString());

        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;

        double[] toaSteps = new double[]{500};
        double[] sampleSteps = new double[]{15000};
        double[] heightStepsKm = new double[]{1000};

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {
            networkSimulator.setNumberOfNodes(nodes);
            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + networkSimulator.getAverageInterArrivalTime());
            for (double height : heightStepsKm) {
                for (double sampleStepMS : sampleSteps) {
                    for (double timeOnAirMS : toaSteps) {
                        satelliteList.get(0).getElements().setSemiMajorAxis(EARTH_RADIUS_M + (1000.0 * height));
                        networkSimulator.setSatellites(satelliteList);
                        networkSimulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);
                        networkSimulator.run();
                    }
                }
            }
        }

    }

}
