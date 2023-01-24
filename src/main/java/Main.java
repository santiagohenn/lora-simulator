import generators.PositionsFactory;
import network.Network;
import network.NetworkAnalyzer;
import satellite.tools.utils.Log;

public class Main {

    public static void main(String[] args) {

        String outputPath = "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\";
        Network network = new Network();
        network.loadSatellites("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");

        PositionsFactory positionsFactory = new PositionsFactory();
        NetworkAnalyzer networkAnalyzer = new NetworkAnalyzer();

        for (int nodes = 10; nodes <= 100; nodes += 10) {
            Log.info("Simulating for " + nodes + " nodes");
            positionsFactory.setParams(nodes, -31.6, -65.9, 7.1946);
            network.setEarthPositions(positionsFactory.generateUniformRandomPositions());
            network.simulateTraffic();
            networkAnalyzer.analyzeTraffic(network);
            networkAnalyzer.saveData(network, outputPath, nodes + "N");
        }

    }

}
