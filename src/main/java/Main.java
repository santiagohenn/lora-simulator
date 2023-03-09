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

        String outputPath = "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\outputs_santi\\";
        String satellitePath = "C:\\Users\\Santi\\Desktop\\STARS\\throughput_pipeline\\satellites.csv";
        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);

        Network network = new Network(60.0, 1000, 1, 1.0 / (100.0 * 1000.0));
        network.setSimulationInterval("2022-01-01T16:00:00.000", "2022-01-01T16:30:00.000");
        network.setSatellites(satellites);

        PositionsFactory positionsFactory = new PositionsFactory();
        NetworkAnalyzer networkAnalyzer = new NetworkAnalyzer(50);

        ReportFactory rf = new ReportFactory(outputPath);

        for (int nodes = 10; nodes <= 10; nodes += 10) {
            Log.info("Simulating for " + nodes + " nodes");
            positionsFactory.setParams(nodes, -31.6, -65.9, 7.1946);
            ArrayList<Position> uniformRandomPositions = positionsFactory.generateUniformRandomPositions();
            rf.saveAsJSON(uniformRandomPositions, "positionsFromSimulator");
            network.setEarthPositions(uniformRandomPositions);
            network.computeAccessIntervals();
            network.getCurrentIntervals().forEach(i -> System.out.println(i.getStart() + " , " + i.getEnd() + " , " + i.getFromAssets() + " to " + i.getToAssets()));
            network.simulateTraffic();
            networkAnalyzer.analyzeTraffic(network);
            networkAnalyzer.saveData(network, outputPath, nodes + "N");
        }

    }

}
