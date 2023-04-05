package generators;

import assets.Transmission;
import assets.Transmitter;
import network.Network;
import org.jetbrains.annotations.NotNull;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class TrafficFactory {

    Map<Integer, Transmitter> transmitters;
    Network network;

    public TrafficFactory(Network network) {
        this.network = network;
        transmitters = new HashMap<>();
        populateTransmitters();
    }

    private void populateTransmitters() {
        network.getEarthPositions().forEach(ep -> transmitters.put(ep.getId(), new Transmitter(ep.getId())));
    }

    public List<Transmission> generateTrafficPolicy1(@NotNull List<Interval> intervals, double frameGenerationRate,
                                                     long timeOnAirMS, long dutyCycleSilentTimeMS) {

        List<Transmission> transmissions = new ArrayList<>();

        for (Interval i : intervals) {

            long timeMS = i.getStart();

            while (timeMS < i.getEnd()) {

//                long nextTransmissionAfterMS = Math.round(1000.0 * generatePoissonEvent(frameGenerationRate));
                long nextTransmissionAfterMS = (long) (1000.0 * generatePoissonEvent(frameGenerationRate));
//                Log.debug("-> " + nextTransmissionAfterMS);

                // Update status of transmitter
                transmitters.get(i.getFirstFrom()).updateStatus(timeMS);

                // Check if transmitter is blocked
                if (transmitters.get(i.getFirstFrom()).isBlocked()) {
                    timeMS += nextTransmissionAfterMS;
                    continue;
                }

                long startTransmissionTimestamp = timeMS + nextTransmissionAfterMS;
                long endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;
                long endDutyCycleTimestamp = endTransmissionTimestamp + dutyCycleSilentTimeMS;

                // If the satellite will be out of visibility for the next transmission, do not transmit
                if (endTransmissionTimestamp > i.getEnd()) { // TODO: Transmit anyways and analyze later
                    break;
                }

                // MG Transmission, used to store last transmission in the transmitter
                transmitters.get(i.getFirstFrom()).transmit(new Transmission(startTransmissionTimestamp,
                        endTransmissionTimestamp, endDutyCycleTimestamp, i.getFromAssets().get(0),
                        i.getToAssets(), 1)); // TODO: Replace fromAssets for int


                // SG Transmissions to each gateway, used for analysis // TODO: replace with lambda
                for (Integer gwIdx : i.getToAssets()) {
                    Transmission tx = new Transmission(startTransmissionTimestamp, endTransmissionTimestamp,
                            endDutyCycleTimestamp, i.getFirstFrom(), gwIdx, 1);

                    transmissions.add(tx);
                }

                // Advance time: end transmission timestamp + silent time (duty cycle)
                timeMS = endDutyCycleTimestamp;

            }

        }

        return transmissions;

    }

    public List<Transmission> generateTrafficPolicy3(@NotNull List<Interval> intervals, double frameGenerationRate,
                                                     long timeOnAirMS, long dutyCycleSilentTimeMS) {

        List<Transmission> transmissions = new ArrayList<>();

        // TODO: use variable for channel

        // Time interval with a 99% chance of containing a transmission
        long timeShiftBackMS = (long) (1000.0 * poissonInterval(frameGenerationRate, 0.99));
        Log.debug("Time shift before interval: " + timeShiftBackMS + " [ms]");
        if (intervals.isEmpty()) {
            Log.warn("No access intervals to generate traffic");
            return transmissions;
        }

        ListIterator<Interval> intervalIterator = intervals.listIterator();
        Transmission transmissionPending = null;

        while (intervalIterator.hasNext()) {

            Interval i = intervalIterator.next();
            long timeMS;

            // Check for pending packets
            if (transmissionPending != null && transmissionPending.getFromId() == i.getFirstFrom()) {

                // TODO

                Log.warn("PENDING PACKET");

                timeMS = transmissionPending.getEndTransmissionTimestamp();

            } else {
                transmissionPending = null;
//                timeMS = i.getStart() - timeShiftBackMS;
                timeMS = generateRandomLong(i.getStart() - timeShiftBackMS, i.getStart());

            }

            while (timeMS <= i.getEnd()) {

                long nextTransmissionAfterMS = (long) (1000.0 * generatePoissonEvent(frameGenerationRate));
                timeMS += nextTransmissionAfterMS;
//                Log.debug("-> " + nextTransmissionAfterMS);

                // Update status of transmitter
                transmitters.get(i.getFirstFrom()).updateStatus(timeMS);

                // Check if transmitter is blocked
                if (transmitters.get(i.getFirstFrom()).isBlocked()) {
                    continue;
                }

                // Prepare new transmission
                long startTransmissionTimestamp = timeMS;
                long endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;
                long endDutyCycleTimestamp = endTransmissionTimestamp + dutyCycleSilentTimeMS;
                int packetStatus = 0;

                Transmission transmissionInDevice = new Transmission(startTransmissionTimestamp,
                        endTransmissionTimestamp, endDutyCycleTimestamp, i.getFirstFrom(),
                        i.getToAssets(), 1);

                // If the transmission stops before the start of the satellite access interval
                // transmit and block the device, but no frame is received by any GW
                if (endTransmissionTimestamp <= i.getStart()) {
                    packetStatus = 2; // 2 = transmitted to the void
                    // Else if access to the GW is gained in the middle of the transmission, create
                    // a partial packet to be heard by the GW(s)
                } else if (startTransmissionTimestamp < i.getStart() && endTransmissionTimestamp >= i.getStart()) {
                    packetStatus = 3; // 3 = partial frame
                    // Set parameters for the transmission in the GW
                    startTransmissionTimestamp = i.getStart();
                    // Else if access to the GW is lost in the middle of the transmission, create a partial packet
                    // to be head by the GW(s)
                } else if (startTransmissionTimestamp < i.getEnd() && endTransmissionTimestamp >= i.getEnd()) {
                    packetStatus = 3; // 3 = partial frame
                    // Set parameters for the transmission in the GW
                    endTransmissionTimestamp = i.getEnd();
                    // If the next transmission happens after the end of this interval
                } else if (startTransmissionTimestamp >= i.getEnd()) {
                    // Insert a pending packet
                    packetStatus = 2;
                    transmissionInDevice.setStatus(packetStatus);
                    transmissionPending = transmissionInDevice;
                    break;
                }

                // MG Transmission, used to store last transmission in the transmitter
                transmissionInDevice.setStatus(packetStatus);
                transmitters.get(i.getFirstFrom()).transmit(transmissionInDevice);

                if (packetStatus == 2) {
                    //transmissions.add(transmissionInDevice);
                    //timeMS = endDutyCycleTimestamp;
                    continue;
                }

                // SG Transmissions to each gateway, used for collision analysis // TODO: replace with lambda
                for (Integer gwIdx : i.getToAssets()) {
                    Transmission transmissionInGateway = new Transmission(startTransmissionTimestamp,
                            endTransmissionTimestamp, endDutyCycleTimestamp, i.getFirstFrom(), gwIdx, 1);
                    transmissionInGateway.setStatus(packetStatus);
                    transmissions.add(transmissionInGateway);
                }

                // Advance time: end transmission timestamp + silent time (duty cycle)

            }

        }

        return transmissions;

    }


    public List<Transmission> generateTrafficPolicy2(@NotNull List<Interval> intervals, double frameGenerationRate,
                                                     long timeOnAirMS, long dutyCycleSilentTimeMS) {

        List<Transmission> transmissions = new ArrayList<>();

        Log.debug("duty cycle silent time (ms) - " + dutyCycleSilentTimeMS);
        Log.debug("time on air (ms) - " + timeOnAirMS);

        for (Interval i : intervals) {

            long timeMS = i.getStart();

            Transmission lastSuccessfulTransmission = null;

            // Advance time to next transmission
            long nextTransmissionAfterMS = (long) (1000.0 * generatePoissonEvent(frameGenerationRate));

            timeMS += nextTransmissionAfterMS;

            while (timeMS < i.getEnd()) {

                long startTransmissionTimestamp = timeMS;
                long endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;

                Transmission tx = new Transmission(startTransmissionTimestamp, endTransmissionTimestamp,
                        endTransmissionTimestamp + dutyCycleSilentTimeMS,
                        i.getFirstFrom(), -1, 1);

                // If the satellite will be out of visibility for the next transmission, do not transmit
                if (endTransmissionTimestamp > i.getEnd()) {
                    break;
                }

                if (lastSuccessfulTransmission != null && startTransmissionTimestamp < lastSuccessfulTransmission.getEndDutyCycleSilentTime()) {
                    // Transmission lost due to duty cycle
                    tx.setStatus(2);
                } else {
                    // Transmit successfully
                    lastSuccessfulTransmission = tx;
                }

                transmissions.add(tx);

                // Advance time to next transmission
                nextTransmissionAfterMS = (long) (1000.0 * generatePoissonEvent(frameGenerationRate));
                timeMS += nextTransmissionAfterMS;

            }

        }

        return transmissions;

    }

    /**
     * Rate is in events per second
     */
    public double generatePoissonEvent(double rate) {

        Random r;
        try {
            r = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.error("Random NoSuchAlgorithmException error");
            e.printStackTrace();
            return -1L;
        }
        return -Math.log(1.0 - r.nextDouble()) / rate;

    }

    /**
     * Compute the elapsed time with an X% chance of containing an event given a certain rate
     * using the CDF (Cumulative Distribution Function)
     * **/
//    public double poissonInterval(double lambda, double probability) {
//        PoissonDistribution poisson = new PoissonDistribution(lambda);
//        double cdf = 0.0;
//        int t = 0;
//        while (cdf < probability) {
//            cdf += poisson.probability(t);
//            t++;
//        }
//        return t / lambda;
//    }

    /**
     * Compute the elapsed time with an X% chance of containing an event given a certain rate
     * using the CDF (Cumulative Distribution Function)
     * **/
    public double poissonInterval(double lambda, double probability) {
        return - Math.log(1 - probability) / lambda;
    }

    private long generateRandomLong(long min, long max) {

        return min + (long) (getRandomDouble() * (max - min));

    }

    private double getRandomDouble() {
        Random r;
        try {
            r = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.error("Random NoSuchAlgorithmException error");
            e.printStackTrace();
            return 0D;
        }
        return r.nextDouble();
    }

}
