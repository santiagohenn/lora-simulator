import generators.PositionsFactory;
import network.Network;
import network.NetworkAnalyzer;
import satellite.tools.utils.Log;

public class Main {

    public static void main(String[] args) {

        Network network = new Network();
        network.loadSatellites("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv");

        PositionsFactory pg = new PositionsFactory();
        NetworkAnalyzer na = new NetworkAnalyzer();

        for (int nodes = 10; nodes <= 100; nodes += 10) {
            Log.info("Simulating for " + nodes + " nodes");
            pg.setParams(nodes, -31.6, -65.9, 7.1946);
            network.setEarthPositions(pg.generateUniformRandomPositions());
            network.computeAccessIntervals();
            network.generateTraffic();

            na.analyzeTraffic(network);
//            network.analyzeTraffic();
            network.saveData("C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\", nodes + "N");
        }

    }

}
