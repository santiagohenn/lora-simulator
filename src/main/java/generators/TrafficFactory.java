package generators;

import assets.Transmission;
import org.jetbrains.annotations.NotNull;
import satellite.tools.structures.Interval;
import satellite.tools.utils.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrafficFactory {

    public TrafficFactory() {
        // Default constructor
    }

    public List<Transmission> generateTrafficPolicy1(@NotNull List<Interval> intervals, double frameGenerationRate,
                                                     long timeOnAirMS, long dutyCycleSilentTimeMS) {

        List<Transmission> transmissions = new ArrayList<>();

        Log.debug("duty cycle silent time (ms) - " + dutyCycleSilentTimeMS);
        Log.debug("time on air (ms) - " + timeOnAirMS);

        for (Interval i : intervals) {

            long timeMS = i.getStart();

            while (timeMS < i.getEnd()) {

                long nextTransmissionAfterMS = generatePoissonEvent(frameGenerationRate);
                long startTransmissionTimestamp = timeMS + nextTransmissionAfterMS;
                long endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;

                // If the satellite will be out of visibility for the next transmission, do not transmit
                if (endTransmissionTimestamp > i.getEnd()) {
                    break;
                }

                // Transmit
                Transmission tx = new Transmission(startTransmissionTimestamp, i.getFirstFrom(), i.getFirstTo(), 1);
                tx.setEndTransmissionTimestamp(endTransmissionTimestamp);
                tx.setEndDutyCycleSilentTime(endTransmissionTimestamp + dutyCycleSilentTimeMS);
                transmissions.add(tx);

                // Advance time: end transmission timestamp + silent time (duty cycle)
                timeMS = endTransmissionTimestamp + dutyCycleSilentTimeMS;

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

            while (timeMS < i.getEnd()) {

                long nextTransmissionAfterMS = generatePoissonEvent(frameGenerationRate);
                long startTransmissionTimestamp = timeMS + nextTransmissionAfterMS;
                long endTransmissionTimestamp = startTransmissionTimestamp + timeOnAirMS;

                // If the satellite will be out of visibility for the next transmission, do not transmit
                if (endTransmissionTimestamp > i.getEnd()) {
                    break;
                }

                Transmission tx;

                if (lastSuccessfulTransmission != null && timeMS < lastSuccessfulTransmission.getEndDutyCycleSilentTime()) {

                    // Transmission lost due to duty cycle
                    tx = new Transmission(startTransmissionTimestamp, i.getFirstFrom(), -1, 1);
                    tx.setEndTransmissionTimestamp(endTransmissionTimestamp);
                    tx.setEndDutyCycleSilentTime(endTransmissionTimestamp + dutyCycleSilentTimeMS);
                    tx.setLostReason(2);

                } else {

                    // Transmit successfully
                    tx = new Transmission(startTransmissionTimestamp, i.getFirstFrom(), i.getFirstTo(), 1);
                    tx.setEndTransmissionTimestamp(endTransmissionTimestamp);
                    tx.setEndDutyCycleSilentTime(endTransmissionTimestamp + dutyCycleSilentTimeMS);
                    lastSuccessfulTransmission = tx;

                }

                transmissions.add(tx);

                // Advance time to next transmission
                timeMS = endTransmissionTimestamp;

            }

        }

        return transmissions;

    }

    public long generatePoissonEvent(double rate) {

        Random r;
        try {
            r = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.error("Random NoSuchAlgorithmException error");
            e.printStackTrace();
            return -1L;
        }
        double nextEvent = -Math.log(1.0 - r.nextDouble()) / rate;
        return (long) nextEvent;

    }

}
