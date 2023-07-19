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
        HashSet<Transmission> successful = new HashSet<>();

        // For each gateway
        for (int gwId = 0; gwId < network.getSatellites().size(); gwId++) {
//            Log.warn("Analyzing for gw ID " + gwId);

            int finalNgw = gwId;
            Iterator<Transmission> transmissionIterator = transmissionsHeard.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingDouble(Transmission::getStartTransmissionTimestamp))
                    .iterator();

            // TODO: Check this
            if (!transmissionIterator.hasNext()) {
                continue;
            }

            // Take the first transmission
            Transmission transmissionInGateway = transmissionIterator.next();

            if (!transmissionIterator.hasNext()) {
                successful.add(transmissionInGateway);
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
                    collisions.add(transmissionInGateway);
                    collisions.add(nextTransmission);
                }

                transmissionInGateway = nextTransmission;

            }

            successful.addAll(transmissionsHeard.stream()
                    .filter(t -> (t.getStatus() != 1 && t.getStatus() != 4)).collect(Collectors.toList()));

            Log.debug("debug total TX in GW: {}", transmissionsHeard.size());
            Log.debug("debug successful: {}", successful.size());
            Log.debug("debug collisions: {}", collisions.size());
            Log.debug("debug partial and successful: {}", transmissionsHeard.stream().filter(t -> t.getStatus() == 3).count());
            Log.debug("debug partial and collided: {}", transmissionsHeard.stream().filter(t -> t.getStatus() == 4).count());

        }

        sampledAnalysis(network, collisions, successful);

    }

    private void sampledAnalysis(Network network, HashSet<Transmission> collisions, HashSet<Transmission> successful) {

        long sampleStepMS = (long) (sampleStepSeconds * 1000); // To ms
        long epochTimeMS = 0;
        long successfulPackets = 0;
        long collidedPackets = 0;
        long partialFrames = 0;
        long totalPackets = 0;

        for (long time = Utils.stamp2unix(network.getSimulationStartTime()) + sampleStepMS;
             time <= Utils.stamp2unix(network.getSimulationEndTime()); time += sampleStepMS) {

            long currentTime = time;

            successfulPackets = successful.stream()
                    .filter(t -> (t.getStartTransmissionTimestamp() >= (currentTime - sampleStepMS)
                            && t.getStartTransmissionTimestamp() < currentTime))
                    .distinct()
                    .count();

            dataPoints.add(new double[]{epochTimeMS, successfulPackets, collidedPackets, totalPackets, partialFrames});

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
