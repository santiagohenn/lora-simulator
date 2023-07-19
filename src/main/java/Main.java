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

public class Main {

    public static void main(String[] args) {

        String outputPath = "C:\\Projects\\throughput_pipeline\\outputs_santi\\";
        String satellitePath = "C:\\Projects\\throughput_pipeline\\satellites.csv";

        double timeStepSeconds = 60.0;
        double timeOnAirMS = 20;
        double dutyCyclePercentage = 1;
        double frameGenerationRate = 10;

        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);
        Network network = new Network(timeStepSeconds, timeOnAirMS, dutyCyclePercentage, frameGenerationRate);

        Log.info("avg inter-arrival time (ms): " + (timeOnAirMS / frameGenerationRate));

        network.setSimulationInterval("2022-01-01T16:00:00.000", "2022-01-01T16:30:00.000");
        network.setSatellites(satellites);

        PositionsFactory positionsFactory = new PositionsFactory();
        NetworkAnalyzer networkAnalyzer = new NetworkAnalyzer(30);
        ReportFactory rf = new ReportFactory(outputPath);

        if (satellites.size() == 1) {
            Log.warn("N of Satellites = " + satellites.size() + " => Satellites == 1 => SG mode");
        } else {
            Log.warn("N of Satellites = " + satellites.size() + " => Satellites > 1 => MG mode");
        }

        int nOfRuns = 10;

        for (int nodes = 20; nodes <= 80; nodes += 20) {

            long simTime = 0;

            List<String> averageStats = new ArrayList<>();
            List<List<double[]>> analysisDataSets = new ArrayList<>();

            positionsFactory.setParams(nodes, -31.6, -65.9, 7.1957);

            long tic = System.currentTimeMillis();
            for (int run = 0; run < nOfRuns; run++) {

                Log.info("Run " + (run + 1) + "/" + nOfRuns + " for " + nodes + " nodes");

                ArrayList<Position> uniformRandomPositions = positionsFactory.generateUniformRandomPositions();
//                ArrayList<Position> uniformRandomPositions = positionsFactory.generatePoissonPointProcessPositions(nodes);
                network.setEarthPositions(uniformRandomPositions);
                // network.getCurrentIntervals().forEach(i -> System.out.println(i.getStart() + " , " + i.getEnd() + " , " + i.getFromAssets() + " to " + i.getToAssets()));
//                network.simulateTraffic();
                network.testContinuousCoverage();
                networkAnalyzer.analyzeTraffic(network);
                analysisDataSets.add(networkAnalyzer.getDataPoints());

            }

            simTime = System.currentTimeMillis() - tic;
            Log.debug("Time elapsed for " + nodes + " nodes: " + simTime/nOfRuns + " [ms]");
            Log.info("Saving data for " + nodes + " nodes");
            networkAnalyzer.saveData(network, outputPath, nodes + "N");
            rf.saveAsJSON(analysisDataSets, nodes + "_N");

        }

        rf.saveAsJSON(positionsFactory.generateUniformRandomPositions(), "PositionsTest");

    }


}
