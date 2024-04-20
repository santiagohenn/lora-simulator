import generators.PositionsFactory;
import generators.ReportFactory;
import network.Network;
import network.NetworkAnalyzer;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static long timer;
    static boolean saveTransmissions = false;
    static boolean saveAccessIntervals = false;
    static boolean saveAnalysisDatasets = true;
    static boolean saveDestinations = false;

    public static void main(String[] args) {

        String simulationParamsJSON = """
                {
                  "outputPath": "C:\\Projects\\throughput_pipeline\\outputs_santi\\",
                  "satellitePath": "C:\\Projects\\throughput_pipeline\\satellites.csv",
                  "startTime": "2022-01-01T16:00:00.000",
                  "endTime": "2022-01-01T16:30:00.000",
                  "startTimestamp": 1641052800000,
                  "endTimestamp": 1641051000000,
                  "propagatorTimeStepSeconds": 60.0,
                  "timeOnAirMS": 100,
                  "dutyCyclePercentage": 1,
                  "frameGenerationRate": 5,
                  "nOfRuns": 10,
                  "initialNodes": 10,
                  "maxNodes": 80,
                  "stepNodes": 10,
                  "centerLatDegrees": -31.6,
                  "centerLongitudeDegrees": -65.9,
                  "radiusDegrees": 7.1957,
                  "sampleStepSeconds": 30
                }
                """;

//        Gson gson = new Gson();
//        JsonObject properties = gson.fromJson(simulationParamsJSON, JsonObject.class);

        // Paths
        String outputPath = "C:\\Projects\\throughput_pipeline\\case_study_south_america\\network_data";
        String satellitePath = "C:/Projects/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";

        // Scenario parameters
        String startTime = "2025-01-01T16:00:00.000";
        String endTime = "2025-01-01T16:30:00.000";
        long startTimestamp = Utils.stamp2unix(startTime);
        long endTimestamp = Utils.stamp2unix(endTime);
        double propagatorTimeStepSeconds = 5.0;
        double visibilityThresholdDegrees = 20;
        double timeOnAirMS = 250;
        double dutyCyclePercentage = 1;
        double frameGenerationRate = 0.1;

        int nOfRuns = 100;
        int initialNodes = 60;
        int stepNodes = 60;
        int maxNodes = 60;
        double centerLatDegrees = -21.0;
        double centerLongitudeDegrees = -58.0;
        double radiusDegrees = 7.1946;
        double sampleStepMilliseconds = 250;
        double sampleStepSeconds = sampleStepMilliseconds / 1000.0;

        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);
        Network network = new Network(propagatorTimeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);
        network.setSimulationInterval(startTime, endTime);
        network.setSatellites(satellites);
        network.setVisibilityThresholdDegrees(visibilityThresholdDegrees);
        PositionsFactory positionsFactory = new PositionsFactory(initialNodes, centerLatDegrees, centerLongitudeDegrees, radiusDegrees);
        NetworkAnalyzer networkAnalyzer = new NetworkAnalyzer(sampleStepSeconds);
        ReportFactory rf = new ReportFactory(outputPath);

        Log.info("avg inter-arrival time (ms): " + (timeOnAirMS / frameGenerationRate)
                + " | " + "avg inter-arrival time Poisson (ms):" + (1 / frameGenerationRate));

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            List<List<double[]>> analysisDataSets = new ArrayList<>();

            positionsFactory.setNOfNodes(nodes);

            long tic = System.currentTimeMillis();
            System.out.print("[SIMULATING] ");
            printProgress(0);
            for (int run = 0; run < nOfRuns; run++) {
                printProgress(((double) run) / nOfRuns * 100.0);
                Log.debug("Run " + String.format("%03d", (run + 1)) + "/" + nOfRuns);

                ArrayList<Position> uniformRandomPositions = positionsFactory.generateUniformRandomPositions();
//                ArrayList<Position> uniformRandomPositions = positionsFactory.generatePoissonPointProcessPositions(nodes);
                network.setEarthPositions(uniformRandomPositions);

                Log.debug("Simulating traffic ! " + tic());
                network.simulateTraffic();
//                network.simulateContinuousCoverage();
                Log.debug("Traffic simulation took " + toc() + " [ms.]");
                Log.debug("Analyzing traffic ! " + tic());
                networkAnalyzer.analyzeTraffic(network);
                analysisDataSets.add(networkAnalyzer.getDataPoints());
                Log.debug("Traffic Analysis took " + toc() + " [ms.]");
            }
            printProgress(100);

            Log.debug("Avg. computation time for " + nodes + " nodes: "
                    + (System.currentTimeMillis() - tic) / nOfRuns + " [ms]");

            if (saveTransmissions) {
                Log.info("Saving transmissions dataset for " + nodes + " nodes");
                rf.saveAsJSON(network.getTransmissions(), "TX_" + nodes + "N");
            }

            if (saveAccessIntervals) {
                Log.info("Saving access intervals dataset for " + nodes + " nodes");
                rf.saveAsJSON(network.getAccessIntervals()
                        .stream().map(interval -> new long[]{interval.getFirstFrom(),
                                interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                        .collect(Collectors.toList()), "intervals_" + nodes + "N");
            }

            if (saveAnalysisDatasets) {
                Log.info("Saving analysis datasets for " + nodes + " nodes");
                rf.saveAsJSON(analysisDataSets, nodes + "ED_" + ((int) sampleStepMilliseconds) + "ms");
            }

            if (saveDestinations) {
                Log.info("Saving destinations datasets for " + nodes + " nodes");
                rf.saveAsJSON(networkAnalyzer.getDestinations(), "destinations_" + nodes + "N");
            }

        }

    }

    public static long tic() {
        timer = System.currentTimeMillis();
        return timer;
    }

    public static long toc() {
        return System.currentTimeMillis() - timer;
    }

    public static void printProgress(double percentage) {

        int progress = (int) percentage;

        if (percentage == 0) {
            System.out.print("   ");
            return;
        }
        System.out.print("\b\b\b\b\b\b");
        System.out.print("█");
        System.out.printf(" %03d", progress);
        System.out.print(" %");
        if (percentage == 100) {
            System.out.println();
        }

    }

}
