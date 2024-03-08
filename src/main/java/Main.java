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
        String outputPath = "C:\\Projects\\throughput_pipeline\\outputs_santi\\";
        String satellitePath = "C:\\Projects\\throughput_pipeline\\satellites.csv";

        // Scenario parameters
        String startTime = "2022-01-01T16:00:00.000";
        String endTime = "2022-01-01T16:30:00.000";
        long startTimestamp = Utils.stamp2unix(startTime);
        long endTimestamp = Utils.stamp2unix(endTime);
        double propagatorTimeStepSeconds = 5.0;
        double visibilityThresholdDegrees = 20;
        double timeOnAirMS = 100;
        double dutyCyclePercentage = 1;
        double frameGenerationRate = 5;

        int nOfRuns = 10;
        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;
        double centerLatDegrees = -21.0;
        double centerLongitudeDegrees = -58.0;
        double radiusDegrees = 7.1946;
        double sampleStepSeconds = 30;

        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);
        Network network = new Network(propagatorTimeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);
        network.setSimulationInterval(startTime, endTime);
        network.setSatellites(satellites);
        network.setVisibilityThresholdDegrees(visibilityThresholdDegrees);
        PositionsFactory positionsFactory = new PositionsFactory(initialNodes, centerLatDegrees, centerLongitudeDegrees, radiusDegrees);
        NetworkAnalyzer networkAnalyzer = new NetworkAnalyzer(sampleStepSeconds);
        ReportFactory rf = new ReportFactory(outputPath);

        Log.info("avg inter-arrival time (ms): " + (timeOnAirMS / frameGenerationRate));

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            List<List<double[]>> analysisDataSets = new ArrayList<>();

            positionsFactory.setNOfNodes(nodes);

            long tic = System.currentTimeMillis();
            for (int run = 0; run < nOfRuns; run++) {

                Log.info("Run " + (run + 1) + "/" + nOfRuns + " for " + nodes + " nodes");

                ArrayList<Position> uniformRandomPositions = positionsFactory.generateUniformRandomPositions();
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

            Log.debug("Avg. computation time for " + nodes + " nodes: "
                    + (System.currentTimeMillis() - tic) / nOfRuns + " [ms]");

            Log.info("Saving data for " + nodes + " nodes");
            rf.saveAsJSON(network.getTransmissions(), "TX_" + nodes + "N");
            rf.saveAsJSON(network.getAccessIntervals()
                    .stream().map(interval -> new long[]{interval.getFirstFrom(),
                            interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                    .collect(Collectors.toList()), "intervals_" + nodes + "N");
            rf.saveAsJSON(analysisDataSets, nodes + "_N");
            rf.saveAsJSON(networkAnalyzer.getDestinations(), "destinations_" + nodes + "N");

        }

        rf.saveAsJSON(positionsFactory.generateUniformRandomPositions(), "PositionsTest");

    }

    public static long tic() {
        timer = System.currentTimeMillis();
        return timer;
    }

    public static long toc() {
        return System.currentTimeMillis() - timer;
    }


}
