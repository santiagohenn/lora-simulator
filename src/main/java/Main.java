import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.List;

public class Main {

    public static final double EARTH_RADIUS_M = 6371000.0;

    public static void main(String[] args) {

//        String outputPath = "C:/Projects/throughput_pipeline/case_study_south_america/network_data/";
        String outputPath = "C:/mg_data_test_550/";
        Simulator simulator = new Simulator(outputPath);

        simulator.setSaveActions(false, true, false, false);

//        String satellitePath = "C:/Projects/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        String satellitePath = "C:/Projects/matlab-snippets/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        // Scenario parameters
        simulator.setScenarioParameters(
                "2025-01-01T16:00:00.000",
                "2025-01-01T16:30:00.000",
                5.0,
                20
        );

        simulator.setSatellites(satelliteList);
        simulator.setDeploymentArea(-21.0, -58.0, 7.1946);
//        simulator.setDeploymentArea(50, 5, 3.1442);

        // Network parameters
        simulator.setNetworkParameters(
                500,
                1,
                0.1,
                15000);

        simulator.setNumberOfRuns(5);
        Log.info(simulator.toString());

        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;

//        double[] sampleSteps = new double[]{250, 500, 750, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 14000,
//                16000, 18000, 20000, 22000, 24000, 26000, 28000, 30000, 32000, 34000, 36000, 38000, 40000};

//        double[] toaSteps = new double[]{250, 500, 750, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
        double[] toaSteps = new double[]{500};

//        double[] toaSteps = new double[]{500};
        double[] sampleSteps = new double[]{15000};
//        double[] toaSteps = new double[]{500};
//        double[] sampleSteps = new double[]{250, 500, 750, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 14000,
//                16000, 18000, 20000, 40000, 100000, 200000, 400000, 800000};

//        double[] heightStepsKm = new double[]{400, 450, 500, 550, 600, 650, 700, 800, 900, 1000, 1500, 2000};

//        double[] heightStepsKm = new double[]{400, 450, 500, 550, 600, 650, 700, 800, 900, 1000, 1500, 2000};
        double[] heightStepsKm = new double[]{1000};

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {
            simulator.setNumberOfNodes(nodes);
            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + simulator.getAverageInterArrivalTime());
            for (double height : heightStepsKm) {
                for (double sampleStepMS : sampleSteps) {
                    for (double timeOnAirMS : toaSteps) {
                        satelliteList.get(0).getElements().setSemiMajorAxis(EARTH_RADIUS_M + (1000.0 * height));
                        simulator.setSatellites(satelliteList);
                        simulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);
                        simulator.run();
                    }
                }
            }
        }

    }

}
