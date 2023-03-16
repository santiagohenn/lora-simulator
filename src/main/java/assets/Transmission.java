package assets;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Transmission implements Comparable<Transmission> {

    private final int transmissionHash;
    private final long startTransmissionTimestamp;
    private final List<Integer> toAssets;
    private final int fromId;
    private final int channel;
    private long endTransmissionTimestamp;
    private long endDutyCycleSilentTime;
    private long timeOnAirMilliSeconds;
    private int status = 0;

    /**
     * Single gateway constructor
     **/
    public Transmission(long startTransmissionTimestamp, long endTransmissionTimestamp, long endDutyCycleSilentTime,
                        int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = startTransmissionTimestamp;
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endDutyCycleSilentTime;
        this.fromId = fromId;
        toAssets = new ArrayList<>(1);
        toAssets.add(toId);
        this.channel = channel;
        this.transmissionHash = ("" + startTransmissionTimestamp + fromId + channel).hashCode();
    }

    /**
     * Multi gateway constructor
     **/
    public Transmission(long startTransmissionTimestamp, long endTransmissionTimestamp, long endDutyCycleSilentTime,
                        int fromId, List<Integer> toAssets, int channel) {
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endDutyCycleSilentTime;
        this.startTransmissionTimestamp = startTransmissionTimestamp;
        this.fromId = fromId;
        this.toAssets = toAssets;
        this.channel = channel;
        this.transmissionHash = ("" + startTransmissionTimestamp + fromId + channel).hashCode();
    }

    public long getStartTransmissionTimestamp() {
        return startTransmissionTimestamp;
    }


    public List<Integer> getToAssets() {
        return toAssets;
    }

    public int getFromId() {
        return this.fromId;
    }

    public int getToId() {
        return toAssets.get(0);
    }

    public int getChannel() {
        return channel;
    }

    public long getEndTransmissionTimestamp() {
        return endTransmissionTimestamp;
    }

    public void setEndTransmissionTimestamp(long endTransmissionTimestamp) {
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.endDutyCycleSilentTime = endTransmissionTimestamp;
    }

    public long getTimeOnAirMilliSeconds() {
        return timeOnAirMilliSeconds;
    }

    public void setTimeOnAirMilliSeconds(long timeOnAirMilliSeconds) {
        this.timeOnAirMilliSeconds = timeOnAirMilliSeconds;
    }

    public long getEndDutyCycleSilentTime() {
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
        return Long.compare(this.startTransmissionTimestamp, o.startTransmissionTimestamp);
    }

    @Override
    public int hashCode() {
        return transmissionHash;
    }

}
