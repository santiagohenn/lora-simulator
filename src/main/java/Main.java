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
import java.util.Locale;

public class Main {

    public static void main(String[] args) {

        String outputPath = "C:\\Projects\\throughput_pipeline\\outputs_santi\\";
        String satellitePath = "C:\\Projects\\throughput_pipeline\\satellites.csv";
        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);

        Network network = new Network(60.0, 10, 1, 1);
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

        int nOfRuns = 1;

        for (int nodes = 10; nodes <= 100; nodes += 10) {

            long simTime = 0;

            List<String> averageStats = new ArrayList<>();
            List<List<double[]>> analysisDataSets = new ArrayList<>();

            positionsFactory.setParams(nodes, -31.6, -65.9, 7.1946);

            for (int run = 0; run < nOfRuns; run++) {

                long tic = System.currentTimeMillis();
                Log.info("Run " + (run + 1) + "/" + nOfRuns + " for " + nodes + " nodes");

                ArrayList<Position> uniformRandomPositions = positionsFactory.generateUniformRandomPositions();
//                ArrayList<Position> uniformRandomPositions = positionsFactory.generatePoissonPointProcessPositions(nodes);

                network.setEarthPositions(uniformRandomPositions);
//                network.computeAccessIntervals();
                // network.getCurrentIntervals().forEach(i -> System.out.println(i.getStart() + " , " + i.getEnd() + " , " + i.getFromAssets() + " to " + i.getToAssets()));
                network.simulateTraffic();
                networkAnalyzer.analyzeTraffic(network);
                analysisDataSets.add(networkAnalyzer.getDataPoints());

            }

            Log.debug("Time elapsed for " + nodes + " nodes: " + simTime/nOfRuns + " [ms]");

            Log.info("Saving data for " + nodes + " nodes");
            networkAnalyzer.setPacketStatistics(averageStats);
            networkAnalyzer.saveData(network, outputPath, nodes + "N");
            rf.saveAsJSON(analysisDataSets, nodes + "_N");

        }

    }

    private static void averageSimulationResult(int nOfRuns, List<String> averageStats, List<String> newMeasure) {

        if (averageStats.isEmpty()) {

            for (int i = 0; i < newMeasure.size(); i++) {

                StringBuilder sb = new StringBuilder();
                String[] newDataPoints = newMeasure.get(i).split(",");

                sb.append(newDataPoints[0]); // Time
                for (int j = 1; j <= newDataPoints.length - 1; j++) {
                    sb.append(",");
                    double metric = Double.parseDouble(newDataPoints[j]) / nOfRuns;
                    sb.append(roundDouble(metric, 2));
                }

                averageStats.add(sb.toString());

            }

        } else {

            for (int i = 0; i < newMeasure.size(); i++) {

                StringBuilder sb = new StringBuilder();
                String[] newDataPoints = newMeasure.get(i).split(",");
                String[] currentAverage = averageStats.get(i).split(",");

                sb.append(newDataPoints[0]); // Time
                for (int j = 1; j <= newDataPoints.length - 1; j++) {
                    sb.append(",");
                    double metric = Double.parseDouble(currentAverage[j]) + Double.parseDouble(newDataPoints[j]) / nOfRuns;
                    sb.append(roundDouble(metric, 2));
                }

                averageStats.set(i, sb.toString());

            }

        }

    }

    public static String roundDouble(double number, int decimalPlaces) {
        double roundedNumber = Math.round(number * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
        return String.format(Locale.ROOT, "%." + decimalPlaces + "f", roundedNumber);
    }

}
