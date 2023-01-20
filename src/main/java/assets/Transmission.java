package assets;

import org.jetbrains.annotations.NotNull;

public class Transmission implements Comparable<Transmission> {

    private final int transmissionID;
    private final long startTransmissionTimestamp;
    private final int fromId;
    private final int toId;
    private final int channel;
    private long endTransmissionTimestamp;
    private long endDutyCycleSilentTime;
    private long timeOnAirMilliSeconds;
    private boolean corrupt;

    public Transmission(long starTransmissionTimestamp, int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = starTransmissionTimestamp;
        this.fromId = fromId;
        this.toId = toId;
        this.channel = channel;
        this.transmissionID = ("" + starTransmissionTimestamp + fromId + toId + channel).hashCode();
    }

    public long getStartTransmissionTimestamp() {
        return startTransmissionTimestamp;
    }

    public int getFromId() {
        return fromId;
    }

    public int getToId() {
        return toId;
    }

    public int getChannel() {
        return channel;
    }

    public long getEndTransmissionTimestamp() {
        return endTransmissionTimestamp;
    }

    public void setEndTransmissionTimestamp(long endTransmissionTimestamp) {
        this.endTransmissionTimestamp = endTransmissionTimestamp;
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

    public boolean isCorrupt() {
        return corrupt;
    }

    public void setCorrupt(boolean corrupt) {
        this.corrupt = corrupt;
    }

    public int getTransmissionID() {
        return transmissionID;
    }

    @Override
    public String toString() {
        return startTransmissionTimestamp + "," + fromId + "," + toId + "," + channel;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Transmission) {
            return ((Transmission) obj).transmissionID == this.transmissionID;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(@NotNull Transmission o) {
        return Long.compare(this.startTransmissionTimestamp, o.startTransmissionTimestamp);
    }
}
