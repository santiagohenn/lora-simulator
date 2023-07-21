package assets;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Status table:
 * 0 = Complete packet reached the gateway
 * 1 = Complete packet reached the gateway and collided
 * 2 = Complete packet unheard by the gateway
 * 3 = Partial packet reached the gateway
 * 4 = Partial packet reached the gateway and collided
 * */
public class Transmission implements Comparable<Transmission> {

    private final int transmissionHash;
    private final double startTransmissionTimestamp;
    private final List<Integer> toAssets;
    private final int fromId;
    private final int toId;
    private final int channel;
    private double endTransmissionTimestamp;
    private double endDutyCycleSilentTime;
    private double timeOnAirMilliSeconds;
    private int status = 0;

    /**
     * Single gateway constructor
     **/
    public Transmission(double startTransmissionTimestamp, double endTransmissionTimestamp, double endDutyCycleSilentTime,
                        int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = startTransmissionTimestamp;
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endDutyCycleSilentTime;
        this.fromId = fromId;
        toAssets = new ArrayList<>(1);
        toAssets.add(toId);
        this.toId = toId;
        this.channel = channel;
        this.transmissionHash = hashCode();
    }

    /**
     * Multi gateway constructor
     **/
    public Transmission(double startTransmissionTimestamp, double endTransmissionTimestamp, double endDutyCycleSilentTime,
                        int fromId, List<Integer> toAssets, int channel) {
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endDutyCycleSilentTime;
        this.startTransmissionTimestamp = startTransmissionTimestamp;
        this.fromId = fromId;
        this.toAssets = toAssets;
        if (toAssets.size() == 1) {
            this.toId = toAssets.get(0);
        } else {
            this.toId = -1;
        }
        this.channel = channel;
        this.transmissionHash = hashCode();
    }

    public double getStartTransmissionTimestamp() {
        return startTransmissionTimestamp;
    }

    public List<Integer> getToAssets() {
        return toAssets;
    }

    public int getFromId() {
        return this.fromId;
    }

    public int getToId() {
        return this.toId;
    }

    public int getChannel() {
        return channel;
    }

    public double getEndTransmissionTimestamp() {
        return endTransmissionTimestamp;
    }

    public void setEndTransmissionTimestamp(long endTransmissionTimestamp) {
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endTransmissionTimestamp;
    }

    public double getTimeOnAirMilliSeconds() {
        return timeOnAirMilliSeconds;
    }

    public void setTimeOnAirMilliSeconds(long timeOnAirMilliSeconds) {
        this.timeOnAirMilliSeconds = timeOnAirMilliSeconds;
    }

    public double getEndDutyCycleSilentTime() {
        return endDutyCycleSilentTime;
    }

    public void setEndDutyCycleSilentTime(long endDutyCycleSilentTime) {
        this.endDutyCycleSilentTime = endDutyCycleSilentTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getTransmissionHash() {
        return transmissionHash;
    }

    public boolean reachedDestination() {
        return status == 0 || status == 3;
    }

    @Override
    public String toString() {
        return startTransmissionTimestamp + "," + fromId + "," + toAssets + "," + channel
                + "," + status + ",#" + transmissionHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Transmission) {
            return ((Transmission) obj).transmissionHash == this.transmissionHash;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(@NotNull Transmission o) {
        if (o.equals(this)) {
            return 0;
        }
        return Double.compare(this.startTransmissionTimestamp, o.startTransmissionTimestamp);
    }

    @Override
    public int hashCode() {
        return ("" + startTransmissionTimestamp + fromId + channel).hashCode();
    }

}
