package network;

import assets.Transmission;
import generators.ReportFactory;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class NetworkAnalyzer {

    private double sampleStepSeconds;
    private final List<String> packetStatistics;

    public NetworkAnalyzer(double sampleStepSeconds) {
        packetStatistics = new ArrayList<>();
        this.sampleStepSeconds = sampleStepSeconds;
    }

    public void analyzeTraffic(Network network) {

        packetStatistics.clear();
        List<Transmission> transmissions = network.getTransmissions();

        // Filter transmissions that where sent to space and sort them by time
        List<Transmission> transmissionsSent = transmissions.stream().filter(t -> !t.isLost())
                .sorted(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp))
                .collect(Collectors.toList());

        Deque<Transmission> transmissionsSuccessful = new ArrayDeque<>();
        TreeSet<Transmission> collisions = new TreeSet<>();
        Iterator<Transmission> transmissionIterator = transmissionsSent.iterator();

        // Take the first transmission
        Transmission transmissionInGateway = transmissionIterator.next();

        // While there are other transmissions
        while (transmissionIterator.hasNext()) {

            // Take the next transmission
            Transmission nextTransmission = transmissionIterator.next();

            // If this transmission does not collide with the previous one
            if (nextTransmission.getStartTransmissionTimestamp() >= transmissionInGateway.getEndTransmissionTimestamp()) {

                // It means it successfully reached the server
                if (!transmissionInGateway.isLost()) {
                    transmissionsSuccessful.addLast(transmissionInGateway);
                }

                // If this was the last transmission and does not collide, it reaches the server
                if (!transmissionIterator.hasNext()) {
                    transmissionsSuccessful.addLast(nextTransmission);
                }

            } else {    // If transmissions collide both are discarded
                transmissionInGateway.setLostReason(1);
                nextTransmission.setLostReason(1);
                collisions.add(transmissionInGateway);
                collisions.add(nextTransmission);
            }

            transmissionInGateway = nextTransmission;

        }

        long debugCollisionCount = transmissions.stream().filter(t -> t.getLostReason() == 1).count();
        long debugSuccessCount = transmissions.stream().filter(t -> !t.isLost()).count();

        Log.debug("debugCollisionCount: " + debugCollisionCount);
        Log.debug("debugSuccessCount: " + debugSuccessCount);
        Log.debug("Total transmissions: " + transmissions.size());
        Log.debug("Successful transmissions: " + transmissionsSuccessful.size());
        Log.debug("Collided transmissions (tx - successful): " + (transmissions.size() - transmissionsSuccessful.size()));
        Log.debug("Collided transmissions (TreeSet): " + collisions.size());

        long sampleStepMS = (long) (sampleStepSeconds * 1000);
        long epochTimeMS = 0;
        long successfulPackets;
        long collidedPackets;
        long totalPackets;

        for (long time = Utils.stamp2unix(network.getSimulationStartTime()) + sampleStepMS;
             time <= Utils.stamp2unix(network.getSimulationEndTime()); time += sampleStepMS) {

            long currentTime = time;

            totalPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime)).count();

            successfulPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && !t.isLost())).count();

            collidedPackets = transmissions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && t.getLostReason() == 1)).count();

            Log.debug(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);
            packetStatistics.add(epochTimeMS / 1000 + "," + successfulPackets + "," + collidedPackets + "," + totalPackets);

            epochTimeMS += sampleStepMS;

        }
    }

    public void setSampleStepSeconds(double sampleStepSeconds) {
        this.sampleStepSeconds = sampleStepSeconds;
    }

    public void saveData(Network network, String path, String name) {

        ReportFactory rg = new ReportFactory(path);
        rg.saveAsCSV(packetStatistics, name);
        rg.saveAsJSON(network.getTransmissions(), "TX_" + name);
        rg.saveAsJSON(network.getAccessIntervals()
                .stream().map(interval -> new long[]{interval.getFirstFrom(), interval.getStart(), interval.getEnd()})
                .collect(Collectors.toList()), "intervals_" + name);

    }

    public List<String> getPacketStatistics() {
        return packetStatistics;
    }
}
