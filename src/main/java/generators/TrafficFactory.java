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

public class TrafficFactory {

    Map<Integer, Transmitter> transmitters;
    Network network;
    private Random r;
    private boolean usePropagationDelay = true;
    private boolean useRandomGW = false;

    public TrafficFactory(Network network) {

        try {
            r = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.error("Random NoSuchAlgorithmException error " + e.getMessage());
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
        if (intervals.isEmpty()) {
            Log.warn("No access intervals to generate traffic");
            return transmissions;
        }

        intervals.sort(Comparator.comparingLong(Interval::getStart));

        // Each interval corresponds to an end device to 1(+) GWs
        for (Interval interval : intervals) {

            Transmitter transmitter = transmitters.get(interval.getFirstFrom());

            double timeMS;

            // Check whether the transmitter was already transmitting
            if (transmitter.getLastTransmission() != null) {
                timeMS = transmitter.getLastTransmission().getEndTransmissionTimestamp();
            } else { // TODO: Add condition to test how long ago the transmission happened and transmit timeShiftBack seconds if sufficiently long
                timeMS = getRandomDouble(interval.getStart() - timeShiftBackMS - timeOnAirMS - dutyCycleSilentTimeMS, interval.getStart());
            }

            while (timeMS <= interval.getEnd()) {

                while (!transmitter.updateStatus(timeMS)) {
                    timeMS += (1000.0 * generatePoissonEvent(frameGenerationRate));
                }

                // Prepare new transmission
                double startTransmissionTimestamp = timeMS;
                double endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;
                double endDutyCycleTimestamp = endTransmissionTimestamp + dutyCycleSilentTimeMS;
                TxStatus txStatus = TxStatus.REACHED_GW;

                Transmission transmissionInDevice = new Transmission(startTransmissionTimestamp,
                        endTransmissionTimestamp, endDutyCycleTimestamp, interval.getFirstFrom(),
                        interval.getToAssets(), 1);

                // If the transmission stops before the start of the satellite access interval
                // transmit and block the device. no frame is received by any GW
                if (endTransmissionTimestamp <= interval.getStart()) {
                    txStatus = TxStatus.LOST_NOT_HEARD;
                    // Else if access to the GW is gained in the middle of the transmission, create
                    // a partial packet to be heard by the GW(s)
                } else if (startTransmissionTimestamp < interval.getStart() && endTransmissionTimestamp >= interval.getStart()) {
                    txStatus = TxStatus.REACHED_GW_PARTIAL;
                    // Set parameters for the transmission in the GW
                    startTransmissionTimestamp = interval.getStart();
                    // Else if access to the GW is lost in the middle of the transmission, create a partial packet
                    // to be heard by the GW(s)
                } else if (startTransmissionTimestamp < interval.getEnd() && endTransmissionTimestamp >= interval.getEnd()) {
                    txStatus = TxStatus.REACHED_GW_PARTIAL;
                    // Set parameters for the transmission in the GW
                    endTransmissionTimestamp = interval.getEnd();
                    // If the next transmission happens after the end of this interval
                } else if (startTransmissionTimestamp >= interval.getEnd()) {
                    txStatus = TxStatus.LOST_NOT_HEARD;
                    transmissionInDevice.setTransmissionStatus(txStatus);
                    break;
                }

                // MG Transmission, used to store last transmission in the transmitter
                transmissionInDevice.setTransmissionStatus(txStatus);
                transmitter.transmit(transmissionInDevice);

                if (txStatus == TxStatus.LOST_NOT_HEARD) {
                    continue;
                }

                if (useRandomGW) {
                    int gwIdx = interval.getToAssets().get(r.nextInt(interval.getToAssets().size()));
                    Transmission transmissionInGateway = new Transmission(startTransmissionTimestamp,
                            endTransmissionTimestamp, endDutyCycleTimestamp, interval.getFirstFrom(), gwIdx, 1);
                    transmissionInGateway.setTransmissionStatus(txStatus);

                    if (usePropagationDelay) {
                        computePropagationDelay(interval, gwIdx, startTransmissionTimestamp, transmissionInGateway, endTransmissionTimestamp);
                    }

                    transmissions.add(transmissionInGateway);
                }

                // MG Transmissions to each gateway
                for (Integer gwId : interval.getToAssets()) {
                    Transmission transmissionInGateway = new Transmission(startTransmissionTimestamp,
                            endTransmissionTimestamp, endDutyCycleTimestamp, interval.getFirstFrom(), gwId, 1);
                    transmissionInGateway.setTransmissionStatus(txStatus);

                    if (usePropagationDelay) {
                        computePropagationDelay(interval, gwId, startTransmissionTimestamp, transmissionInGateway, endTransmissionTimestamp);
                    }

                    transmissions.add(transmissionInGateway);
                }

            }
        }

        return transmissions;

    }

    /**
     * Computes relative positions and adjusts start and end reception time on the gateway with respect to the signal's propagation time
     * **/
    private void computePropagationDelay(Interval interval, Integer gwId, double startTransmissionTimestamp, Transmission transmissionInGateway, double endTransmissionTimestamp) {
        network.getSimulation().setDevice(network.getEarthPositions().get(interval.getFirstFrom()));
        network.getSimulation().setSatellite(network.getSatellites().get(gwId));
        Ephemeris e = network.getSimulation()
                .computeTopocentricEphemeris(Utils.unix2stamp((long) startTransmissionTimestamp));
        transmissionInGateway.setStartReceptionTimestamp(startTransmissionTimestamp + (e.getRange() / Utils.C_VACUUM) * 1000.0);
        e = network.getSimulation()
                .computeTopocentricEphemeris(Utils.unix2stamp((long) endTransmissionTimestamp));
        transmissionInGateway.setEndReceptionTimestamp(endTransmissionTimestamp + (e.getRange() / Utils.C_VACUUM) * 1000.0);
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

    public void setUsePropagationDelay(boolean usePropagationDelay) {
        this.usePropagationDelay = usePropagationDelay;
    }

    public void setUseRandomGW(boolean useRandomGW) {
        this.useRandomGW = useRandomGW;
    }

}
