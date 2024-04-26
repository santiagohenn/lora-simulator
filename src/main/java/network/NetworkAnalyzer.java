package network;

import assets.Transmission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkAnalyzer {

    private double sampleStepSeconds;
    private List<double[]> dataPoints;
    private List<long[]> destinations;
    static final Logger Log = LogManager.getLogger(NetworkAnalyzer.class);

    public NetworkAnalyzer() {

    }

    public NetworkAnalyzer(double sampleStepSeconds) {
        setSampleStepSeconds(sampleStepSeconds);
    }

    public void analyzeTraffic(Network network) {

        dataPoints = new ArrayList<>();
        destinations = new ArrayList<>();
        List<Transmission> transmissions = network.getTransmissions();

        // Exclude transmissions that where sent to the void
        List<Transmission> transmissionsHeard = transmissions.stream()
                .filter(Transmission::reachedDestination)
                .sorted()
                .collect(Collectors.toList());

        // For each gateway
        for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {

            Log.debug("Analyzing for gw ID " + gwId);

            int finalNgw = gwId;
            Iterator<Transmission> transmissionsOnGateway = transmissionsHeard.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted()
                    .iterator();

            if (!transmissionsOnGateway.hasNext()) {
                Log.warn("No transmissions heard by gw ID " + gwId);
                continue;
            }

            // Take the first transmission
            Transmission transmissionInGateway = transmissionsOnGateway.next();

            if (!transmissionsOnGateway.hasNext()) {
                Log.warn("Only one transmission heard by gw ID " + gwId);
                continue;
            }

            // While there are other transmissions
            while (transmissionsOnGateway.hasNext()) {
                Transmission nextTransmission = transmissionsOnGateway.next(); // Take the next transmission
                checkCollision(transmissionInGateway, nextTransmission);
                // DEBUG
//                if (checkCollision(transmissionInGateway, nextTransmission)) {
//                    Transmission finalTransmissionInGateway = transmissionInGateway;
//                    transmissionsHeard.stream().filter(t -> t.equals(finalTransmissionInGateway)).forEach(transmission -> transmission.setStatus(1));
//                } // Check collisions and set corresponding status
                transmissionInGateway = nextTransmission;
            }

        }

//        Log.debug("debug successful final size: {}", transmissionsHeard.stream()
//                .filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).count());
//        Log.debug("debug successful final distinct size: {}", transmissionsHeard.stream()
//                .distinct()
//                .filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).count());
        sampledAnalysis(network);

    }

    private void sampledAnalysis(Network network) {

        double sampleStepMS = this.sampleStepSeconds * 1000; // To ms
        long epochTimeMS = 0;
        long successfulPackets = 0;
        long partialFrames = 0;
        long totalPackets = 0;
        long totalDistinct = 0;
        long successfulDistinct = 0;
        long sentToTheVoid = 0;

        List<Transmission> transmissionsOnServer = network.getTransmissions();

        for (double time = network.getSimulationStartTimestamp() + sampleStepMS;
             time <= network.getSimulationEndTimestamp(); time += sampleStepMS) {

            double currentTime = time;

            Supplier<Stream<Transmission>> transmissionsOnSample
                    = () -> transmissionsOnServer.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime));

            totalPackets = transmissionsOnSample.get().count();
            totalDistinct = transmissionsOnSample.get().distinct().count();
            successfulPackets = transmissionsOnSample.get().filter(Transmission::reachedDestination).count();
            successfulDistinct = transmissionsOnSample.get().distinct().filter(Transmission::reachedDestination).count();
            partialFrames = transmissionsOnSample.get().filter(t -> (t.getStatus() == 3 || t.getStatus() == 4)).count();
            sentToTheVoid = transmissionsOnSample.get().filter(t -> (t.getStatus() == 2)).count();

            dataPoints.add(new double[]{epochTimeMS, totalPackets, totalDistinct, successfulPackets,
                    successfulDistinct, partialFrames, sentToTheVoid});

            // For each gateway
            long[] toGwCount = new long[network.getSatellites().size()];
            for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {
                int finalGwId = gwId;
                toGwCount[gwId] = transmissionsOnSample.get().filter(t -> t.getToId() == finalGwId).count();
            }

            destinations.add(toGwCount);

            epochTimeMS += sampleStepMS;

        }

    }

    public void setSampleStepMS(double sampleStepMS) {
        setSampleStepSeconds(sampleStepMS / 1000.0);
    }

    public void setSampleStepSeconds(double sampleStepSeconds) {
        this.sampleStepSeconds = sampleStepSeconds;
        if (sampleStepSeconds <= 0) {
            this.sampleStepSeconds = 1;
            Log.error("Sample time must be greater than 0. Defaulting to 1 second.");
        }
    }

    public List<double[]> getDataPoints() {
        return dataPoints;
    }

    public List<long[]> getDestinations() {
        return destinations;
    }

    /**
     * Checked cases:
     * t1 |------------|                   t1 |------------|
     * _____ t2 |------------|       t2 |------------|
     **/
    private boolean checkCollision(Transmission transmissionInGateway, Transmission nextTransmission) {

        if (nextTransmission.getStartReceptionTimestamp() >= transmissionInGateway.getStartReceptionTimestamp()
                && nextTransmission.getStartReceptionTimestamp() < transmissionInGateway.getEndReceptionTimestamp()) { // TODO: the less or equal here might not be necessary

            transmissionInGateway.setStatus(transmissionInGateway.getStatus() == 3 ? 4 : 1);
            nextTransmission.setStatus(nextTransmission.getStatus() == 3 ? 4 : 1);

            return true;

        }

        if (transmissionInGateway.getStartTransmissionTimestamp() >= nextTransmission.getStartTransmissionTimestamp()
                && transmissionInGateway.getStartTransmissionTimestamp() < nextTransmission.getEndTransmissionTimestamp()) {
            Log.error("This should not happen: | "
                    + transmissionInGateway.getStartTransmissionTimestamp() + " -> " + transmissionInGateway.getEndTransmissionTimestamp()
                    + " | " + nextTransmission.getStartTransmissionTimestamp() + " -> " + nextTransmission.getEndTransmissionTimestamp() + " | ");



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

            return true;

        }

        return false;

    }

}
