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

        // Filter transmissions that where sent to the void // TODO add conditional depending on policy
        List<Transmission> transmissionsSent = transmissions.stream().filter(t -> t.getStatus() == 0)
                .collect(Collectors.toList());

        TreeSet<Transmission> collisions = new TreeSet<>();

        // Do this for each gateway
        for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {
//        for (int gwId = 0; gwId <= 0; gwId++) {
//            Log.warn("Analyzing for gw ID " + gwId);

            int finalNgw = gwId;
            Iterator<Transmission> transmissionIterator = transmissionsSent.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp))
                    .iterator();

//            Iterator<Transmission> transmissionIterator = transmissionsSent.stream()
//                    .sorted(Comparator.comparingLong(Transmission::getStartTransmissionTimestamp))
//                    .iterator();

            // Take the first transmission
            if (!transmissionIterator.hasNext()) {
                continue;
            }

            Transmission transmissionInGateway = transmissionIterator.next();

            // While there are other transmissions
            while (transmissionIterator.hasNext()) {

                // Take the next transmission
                Transmission nextTransmission = transmissionIterator.next();

                // If this transmission collides with the previous one, both are lost
                if (nextTransmission.getStartTransmissionTimestamp() < transmissionInGateway.getEndTransmissionTimestamp()
                        && !nextTransmission.equals(transmissionInGateway)) {
                    transmissionInGateway.setStatus(1);
                    nextTransmission.setStatus(1);
                    collisions.add(transmissionInGateway);
                    collisions.add(nextTransmission);
                }

                transmissionInGateway = nextTransmission;

            }

//            Log.debug("debugCollisionCount: {}", transmissionsSent.stream().filter(t -> t.getLostReason() == 1).count());
//            Log.debug("debugSuccessCount: {}", transmissionsSent.stream().filter(t -> t.getLostReason() == 0).count());

        }

//        Log.debug("All");
//        transmissionsSent.forEach(System.out::println);
//        Log.debug("Distinct");
//        transmissionsSent.stream().distinct().collect(Collectors.toList()).forEach(System.out::println);

//        Log.debug("[entire scenario] Total transmissions: {}", transmissionsSent.size());
//        Log.debug("[entire scenario] Collided transmissions (TreeSet): {}", collisions.size());


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

        long sampleStepMS = (long) (sampleStepSeconds * 1000); // To ms
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
            // TODO: Try distinct here

            successfulPackets = network.getTransmissions().stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && t.getStatus() == 0))
                    .distinct()
                    .count();

//            if (epochTimeMS == 820000) {
//                Log.warn("NOT DISTINCT");
//                network.getTransmissions().stream().filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
//                        && t.getStartTransmissionTimestamp() < currentTime)).forEach(System.out::println);
//                Log.warn("DISTINCT");
//                network.getTransmissions().stream().filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
//                        && t.getStartTransmissionTimestamp() < currentTime)).distinct().forEach(System.out::println);
//            }

            collidedPackets = collisions.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime
                            && t.getStatus() == 1)).count();
            // TODO: Try distinct here too

            packetStatistics.add(epochTimeMS / 1000.0 + "," + successfulPackets + ","
                    + collidedPackets + ","
                    + totalPackets);

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
        rf.saveAsJSON(network.getAccessIntervals()
                .stream().map(interval -> new long[]{interval.getFirstFrom(), interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                .collect(Collectors.toList()), "intervals_" + name);

    }

    public List<String> getPacketStatistics() {
        return packetStatistics;
    }

    public void setPacketStatistics(List<String> packetStatistics) {
        this.packetStatistics = packetStatistics;
    }

    private String roundDouble(double number, int decimalPlaces) {
        double roundedNumber = Math.round(number * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
        return String.format("%." + decimalPlaces + "f", roundedNumber);
    }

}
