import generators.PositionsFactory;
import network.Network;
import network.NetworkAnalyzer;
import satellite.tools.utils.Log;

public class Main {

    public static void main(String[] args) {

        String outputPath = "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\";
        String satellitePath = "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv";

        Network network = new Network(60.0, 1000, 1, 1.0 / (100.0 * 1000.0));
        network.SetSimulationInterval("2022-01-01T16:00:00.000", "2022-01-01T16:30:00.000");
        network.loadSatellites(satellitePath);

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
