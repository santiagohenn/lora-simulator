package generators;

import assets.Transmission;
import assets.Transmitter;
import network.Network;
import org.jetbrains.annotations.NotNull;
import satellite.tools.structures.Ephemeris;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;
import structures.TxStatus;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public class TrafficFactory {

    Map<Integer, Transmitter> transmitters;
    Network network;
    private Random r;
    private boolean usePropagationDelay = true;

    public TrafficFactory(Network network) {

        try {
            r = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.error("Random NoSuchAlgorithmException error");
            e.printStackTrace();
        }


        this.network = network;
        transmitters = new HashMap<>();
        populateTransmitters();
    }

    private void populateTransmitters() {
        network.getEarthPositions().forEach(ep -> transmitters.put(ep.getId(), new Transmitter(ep.getId())));
    }

    public List<Transmission> generateTrafficPolicy3(@NotNull List<Interval> intervals, double frameGenerationRate,
                                                     double timeOnAirMS, double dutyCycleSilentTimeMS) {

        List<Transmission> transmissions = new ArrayList<>();

        // TODO: use variable for channel

        long timeShiftBackMS = (long) (1000.0 * poissonInterval(frameGenerationRate, 0.99));
        //        Log.debug("Time shift before interval: " + timeShiftBackMS + " [ms]");
        if (intervals.isEmpty()) {
            Log.warn("No access intervals to generate traffic");
            return transmissions;
        }

        // Each interval corresponds to an end device to 1(+) GWs
        for (Interval i : intervals.stream()
                .sorted(Comparator.comparingLong(Interval::getStart))
                .collect(Collectors.toList())) {

            Transmitter transmitter = transmitters.get(i.getFirstFrom());

            double timeMS;

            // Check whether the transmitter was already transmitting
            if (transmitter.getLastTransmission() != null) {
                timeMS = transmitter.getLastTransmission().getEndTransmissionTimestamp();
            } else { // TODO: Add condition to test how long ago the transmission happened and transmit timeShiftBack seconds if sufficiently long
                timeMS = getRandomDouble(i.getStart() - timeShiftBackMS - timeOnAirMS - dutyCycleSilentTimeMS, i.getStart());
            }

            while (timeMS <= i.getEnd()) {

                while (!transmitter.updateStatus(timeMS)) {
                    timeMS += (1000.0 * generatePoissonEvent(frameGenerationRate));
                }

                // Prepare new transmission
                double startTransmissionTimestamp = timeMS;
                double endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;
                double endDutyCycleTimestamp = endTransmissionTimestamp + dutyCycleSilentTimeMS;
                int packetStatus = 0;
                TxStatus txStatus = TxStatus.REACHED_GW;

                Transmission transmissionInDevice = new Transmission(startTransmissionTimestamp,
                        endTransmissionTimestamp, endDutyCycleTimestamp, i.getFirstFrom(),
                        i.getToAssets(), 1);

                // If the transmission stops before the start of the satellite access interval
                // transmit and block the device. no frame is received by any GW
                // TODO: add the border condition for true visibility of the device
                if (endTransmissionTimestamp <= i.getStart()) {
                    packetStatus = 2; // 2 = transmitted to the void
                    txStatus = TxStatus.LOST_NOT_HEARD;
                    // Else if access to the GW is gained in the middle of the transmission, create
                    // a partial packet to be heard by the GW(s)
                } else if (startTransmissionTimestamp < i.getStart() && endTransmissionTimestamp >= i.getStart()) {
                    packetStatus = 3; // 3 = partial frame
                    txStatus = TxStatus.REACHED_GW_PARTIAL;
                    // Set parameters for the transmission in the GW
                    startTransmissionTimestamp = i.getStart();
                    // Else if access to the GW is lost in the middle of the transmission, create a partial packet
                    // to be heard by the GW(s)
                } else if (startTransmissionTimestamp < i.getEnd() && endTransmissionTimestamp >= i.getEnd()) {
                    packetStatus = 3; // 3 = partial frame
                    txStatus = TxStatus.REACHED_GW_PARTIAL;
                    // Set parameters for the transmission in the GW
                    endTransmissionTimestamp = i.getEnd();
                    // If the next transmission happens after the end of this interval
                } else if (startTransmissionTimestamp >= i.getEnd()) {
                    txStatus = TxStatus.LOST_NOT_HEARD;
                    packetStatus = 2;
                    transmissionInDevice.setStatus(packetStatus);
                    break;
                }

                // MG Transmission, used to store last transmission in the transmitter
                transmissionInDevice.setTransmissionStatus(txStatus);
                transmissionInDevice.setStatus(packetStatus);
                transmitter.transmit(transmissionInDevice);

                if (packetStatus == 2) {
                    continue;
                }

                // Set the device's position on Earth in the simulator
                network.getSimulation().setDevice(network.getEarthPositions().get(i.getFirstFrom()));

                // MG Transmissions to each gateway, used for collision analysis // TODO: replace with lambda
                for (Integer gwId : i.getToAssets()) {
                    Transmission transmissionInGateway = new Transmission(startTransmissionTimestamp,
                            endTransmissionTimestamp, endDutyCycleTimestamp, i.getFirstFrom(), gwId, 1);
                    transmissionInGateway.setStatus(packetStatus);
                    transmissionInGateway.setTransmissionStatus(txStatus); // TODO: CHECK THIS IN BRANCH ***

                    if (usePropagationDelay) {
                        network.getSimulation().setSatellite(network.getSatellites().get(gwId));
                        Ephemeris e = network.getSimulation()
                                .computeTopocentricEphemeris(Utils.unix2stamp((long) startTransmissionTimestamp));
                        transmissionInGateway.setStartReceptionTimestamp(startTransmissionTimestamp + (e.getRange() / Utils.C_VACUUM) * 1000.0);
                        e = network.getSimulation()
                                .computeTopocentricEphemeris(Utils.unix2stamp((long) endTransmissionTimestamp));
                        transmissionInGateway.setEndReceptionTimestamp(endTransmissionTimestamp + (e.getRange() / Utils.C_VACUUM) * 1000.0);
                    }

                    transmissions.add(transmissionInGateway);
                }

//                 Random gw
//                int gwIdx = i.getToAssets().get(r.nextInt(i.getToAssets().size()));
//                Transmission transmissionInGateway = new Transmission(startTransmissionTimestamp,
//                        endTransmissionTimestamp, endDutyCycleTimestamp, i.getFirstFrom(), gwIdx, 1);
//                transmissionInGateway.setStatus(packetStatus);
//                transmissions.add(transmissionInGateway);

            }
        }

        return transmissions;

    }

    /**
     * Rate is in events per second
     */
    public double generatePoissonEvent(double rate) {
        return -Math.log(1.0 - r.nextDouble()) / rate;
    }

    /**
     * Compute the elapsed time with an X% chance of containing an event given a certain rate
     * using the CDF (Cumulative Distribution Function)
     **/
    public double poissonInterval(double lambda, double probability) {
        return -Math.log(1 - probability) / lambda;
    }

    private double getRandomDouble(double min, double max) {

        if (min >= max) {
            throw new IllegalArgumentException("Invalid range: min must be less than max");
        }

        double range = max - min + 1D;
        return min + (r.nextDouble() * range);

    }

    private int getRandomInteger(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("Invalid interval. 'min' should be less than 'max'.");
        }
        return r.nextInt(max - min + 1) + min;
    }


}
