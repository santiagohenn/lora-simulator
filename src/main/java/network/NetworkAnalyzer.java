package network;

import assets.Transmission;
import generators.ReportFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import satellite.tools.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class NetworkAnalyzer {

    private double sampleStepSeconds;
    private List<String> packetStatistics;
    static final Logger Log = LogManager.getLogger(NetworkAnalyzer.class);

    public NetworkAnalyzer(double sampleStepSeconds) {
        setSampleStepSeconds(sampleStepSeconds);
    }

    public void analyzeTraffic(Network network) {

        packetStatistics = new ArrayList<>();
        List<Transmission> transmissions = network.getTransmissions();

        // Filter transmissions that where sent to the void
        List<Transmission> transmissionsSent = transmissions.stream().filter(t -> !t.isLost())
                .collect(Collectors.toList());

        TreeSet<Transmission> collisions = new TreeSet<>();

        // Do this for each gateway
        for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {

            Log.warn("Analyzing for gw ID " + gwId);

            int finalNgw = gwId;
            Iterator<Transmission> transmissionIterator = transmissionsSent.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp))
                    .iterator();

            // Take the first transmission
            Transmission transmissionInGateway = transmissionIterator.next();

            // While there are other transmissions
            while (transmissionIterator.hasNext()) {

                // Take the next transmission
                Transmission nextTransmission = transmissionIterator.next();

                // If this transmission collides with the previous one, both are lost
                if (nextTransmission.getStartTransmissionTimestamp() < transmissionInGateway.getEndTransmissionTimestamp()
                        && !nextTransmission.equals(transmissionInGateway)) {
                    transmissionInGateway.setLostReason(1);
                    nextTransmission.setLostReason(1);
                    collisions.add(transmissionInGateway);
                    collisions.add(nextTransmission);
                }

                transmissionInGateway = nextTransmission;

            }

            Log.debug("debugCollisionCount: {}", transmissions.stream().filter(t -> t.getLostReason() == 1).count());
            Log.debug("debugSuccessCount: {}", transmissions.stream().filter(t -> t.getLostReason() == 0).count());

        }

        Log.debug("[entire scenario] Total transmissions: {}", transmissions.size());
        Log.debug("[entire scenario] Collided transmissions (TreeSet): {}", collisions.size());


//        long sampleStepMS = (long) (sampleStepSeconds * 1000);
//        long epochTimeMS = 0;
//        long successfulPackets;
//        long collidedPackets;
//        long totalPackets;
//
//        for (long time = Utils.stamp2unix(network.getSimulationStartTime()) + sampleStepMS;
//             time <= Utils.stamp2unix(network.getSimulationEndTime()); time += sampleStepMS) {
//
//            long currentTime = time;
//
//            totalPackets = transmissions.stream()
//                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
//                            && t.getStartTransmissionTimestamp() < currentTime)).count();
//
//            successfulPackets = transmissions.stream()
//                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
//                            && t.getStartTransmissionTimestamp() < currentTime
//                            && !t.isLost())).count();
//
//            collidedPackets = collisions.stream()
//                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
//                            && t.getStartTransmissionTimestamp() < currentTime
//                            && t.getLostReason() == 1)).count();
//
//            packetStatistics.add(epochTimeMS / 1000.0 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);
//
//            epochTimeMS += sampleStepMS;
//
//        }

        sampledAnalysis(network, collisions);

    }

    private void sampledAnalysis(Network network, TreeSet<Transmission> collisions) {

        long sampleStepMS = (long) (sampleStepSeconds * 1000);
        long epochTimeMS = 0;
        long successfulPackets;
        long collidedPackets;
        long totalPackets;

        for (long time = Utils.stamp2unix(network.getSimulationStartTime()) + sampleStepMS;
             time <= Utils.stamp2unix(network.getSimulationEndTime()); time += sampleStepMS) {

            long currentTime = time;

            totalPackets = network.getTransmissions().stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime)).count();

            successfulPackets = network.getTransmissions().stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && !t.isLost())).count();

            collidedPackets = collisions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && t.getLostReason() == 1)).count();

            packetStatistics.add(epochTimeMS / 1000.0 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);

            epochTimeMS += sampleStepMS;

        }

    }

    public void setSampleStepSeconds(double sampleStepSeconds) {
        if (sampleStepSeconds <= 0) {
            Log.error("Sample time must be greater than 0");
        } else {
            this.sampleStepSeconds = sampleStepSeconds;
        }
    }

    public void saveData(Network network, String path, String name) {

        ReportFactory rf = new ReportFactory(path);
        rf.saveAsCSV(packetStatistics, name);
        rf.saveAsJSON(network.getTransmissions(), "TX_" + name);
        rf.saveAsJSON(network.getCurrentIntervals()
                .stream().map(interval -> new long[]{interval.getFirstFrom(), interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                .collect(Collectors.toList()), "intervals_" + name);

    }

    public List<String> getPacketStatistics() {
        return packetStatistics;
    }
}
