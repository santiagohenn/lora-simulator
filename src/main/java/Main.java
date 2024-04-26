import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        String outputPath = "D:\\network_data";
        Simulator simulator = new Simulator(outputPath);

        simulator.setSaveActions(false, true, false, false);

        String satellitePath = "C:/Projects/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
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

        // Network parameters
        simulator.setNetworkParameters(
                500,
                1,
                60,
                250);

        simulator.setNumberOfRuns(10);
        Log.info(simulator.toString());

        int initialNodes = 20;
        int stepNodes = 40;
        int maxNodes = 100;

        double[] sampleSteps = new double[]{250, 500, 750, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 14000,
                16000, 18000, 20000, 22000, 24000, 26000, 28000, 30000, 32000, 34000, 36000, 38000, 40000};
        double[] toaSteps = new double[]{500, 750, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {
            simulator.setNumberOfNodes(nodes);
            Log.info("Running with " + nodes + " nodes | avg inter-arrival time (ms): " + simulator.getAverageInterArrivalTime());
            for (double sampleStepMS : sampleSteps) {
                for (double toaStepMS : toaSteps) {
                    simulator.setTimeOnAirAndSampleStep(toaStepMS, sampleStepMS);
                    simulator.run();
                }
            }
        }

    }

}
