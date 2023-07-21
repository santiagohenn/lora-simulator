package network;

import assets.Transmission;
import generators.ReportFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkAnalyzer {

    private double sampleStepSeconds;
    private List<double[]> dataPoints;
    static final Logger Log = LogManager.getLogger(NetworkAnalyzer.class);

    public NetworkAnalyzer(double sampleStepSeconds) {
        setSampleStepSeconds(sampleStepSeconds);
    }

    public void analyzeTraffic(Network network) {

        dataPoints = new ArrayList<>();
        List<Transmission> transmissions = network.getTransmissions();

        // Filter transmissions that where sent to the void // TODO add conditional depending on policy
        List<Transmission> transmissionsHeard = transmissions.stream()
                .filter(t -> t.getStatus() != 2) // TODO WARNING HERE
                .collect(Collectors.toList());

        HashSet<Transmission> collisions = new HashSet<>();

        // For each gateway
        for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {
//            Log.warn("Analyzing for gw ID " + gwId);

            int finalNgw = gwId;
            Iterator<Transmission> transmissionIterator = transmissionsHeard.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingDouble(Transmission::getStartTransmissionTimestamp))
                    .iterator();
            // TODO: up here replace Comparator.comparing double with lambda since intervals have the compareTo method overwritten

            // TODO: Check this
            if (!transmissionIterator.hasNext()) {
                Log.warn("No transmissions heard by gw id " + gwId);
                continue;
            }

            // Take the first transmission
            Transmission transmissionInGateway = transmissionIterator.next();

            if (!transmissionIterator.hasNext()) {
                Log.warn("Only one transmission heard by gw id " + gwId);
                continue;
            }

            // While there are other transmissions
            while (transmissionIterator.hasNext()) {

                // Take the next transmission
                Transmission nextTransmission = transmissionIterator.next();

                // If this transmission collides with the previous one, both are lost
                if (checkCollision(transmissionInGateway, nextTransmission)) {
                    if (transmissionInGateway.getStatus() == 3) {
                        transmissionInGateway.setStatus(4);
                    } else {
                        transmissionInGateway.setStatus(1);
                    }
                    if (nextTransmission.getStatus() == 3) {
                        nextTransmission.setStatus(4);
                    } else {
                        nextTransmission.setStatus(1);
                    }
//                    collisions.add(transmissionInGateway);
//                    collisions.add(nextTransmission);
                }

                transmissionInGateway = nextTransmission;

            }

            Log.debug("debug total TX in GW {}: {}", gwId, transmissionsHeard.stream()
                    .filter(t -> t.getToId() == finalNgw).count());
            Log.debug("debug successful in GW {}: {}", gwId, transmissionsHeard.stream()
                    .filter(t -> t.getToId() == finalNgw).count());
            Log.debug("debug successful partial size: {}", transmissionsHeard.size());
//            Log.debug("debug collisions: {}", collisions.size());
            Log.debug("debug partial and successful: {}", transmissionsHeard.stream().filter(t -> t.getStatus() == 3).count());
            Log.debug("debug partial and collided: {}", transmissionsHeard.stream().filter(t -> t.getStatus() == 4).count());

        }

        Log.debug("debug successful final size: {}", transmissionsHeard.stream()
                .filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).count());
        Log.debug("debug successful final distinct size: {}", transmissionsHeard.stream()
                .distinct()
                .filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).count());
        sampledAnalysis(network);

    }

    private void sampledAnalysis(Network network) {

        long sampleStepMS = (long) (sampleStepSeconds * 1000); // To ms
        long epochTimeMS = 0;
        long successfulPackets = 0;
        long collidedPackets = 0;
        long partialFrames = 0;
        long totalPackets = 0;
        long distinctPackets = 0;

        List<Transmission> transmissionsOnServer = network.getTransmissions();

        for (long time = network.getSimulationStartTimestamp() + sampleStepMS;
             time <= network.getSimulationEndTimestamp(); time += sampleStepMS) {

            long currentTime = time;

            Supplier<Stream<Transmission>> transmissionsOnSample
                    = () -> transmissionsOnServer.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime));

            totalPackets = transmissionsOnSample.get().count();
            distinctPackets = transmissionsOnSample.get().distinct().count();
            successfulPackets = transmissionsOnSample.get().filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).count();
            collidedPackets = totalPackets - successfulPackets;
            partialFrames = transmissionsOnSample.get().filter(t -> (t.getStatus() == 3 || t.getStatus() == 4)).count();

            dataPoints.add(new double[]{epochTimeMS, successfulPackets, collidedPackets, totalPackets, distinctPackets, partialFrames});
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
        rf.saveAsJSON(network.getTransmissions(), "TX_" + name);
        rf.saveAsJSON(network.getAccessIntervals()
                .stream().map(interval -> new long[]{interval.getFirstFrom(),
                        interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                .collect(Collectors.toList()), "intervals_" + name);

    }

    public List<double[]> getDataPoints() {
        return dataPoints;
    }

    /**
     * Checked cases:
     * t1 |------------|                   t1 |------------|
     * _____ t2 |------------|       t2 |------------|
     **/
    private boolean checkCollision(Transmission t1, Transmission t2) {

        if (t2.getStartTransmissionTimestamp() >= t1.getStartTransmissionTimestamp()
                && t2.getStartTransmissionTimestamp() < t1.getEndTransmissionTimestamp()) { // TODO: the less or equal here might not be necessary
            return true;
        }

        if (t1.getStartTransmissionTimestamp() >= t2.getStartTransmissionTimestamp()
                && t1.getStartTransmissionTimestamp() < t2.getEndTransmissionTimestamp()) {
            return true;
        }

        return false;

    }

}
