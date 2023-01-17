import org.jetbrains.annotations.NotNull;

public class Transmission implements Comparable<Transmission> {

    private final int transmissionID;
    private final long startTransmissionTimestamp;
    private final int fromId;
    private final int toId;
    private final int channel;
    private long endTransmissionTimestamp;
    private long timeOnAirMilliSeconds;

    public Transmission(long timestamp, int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = timestamp;
        this.fromId = fromId;
        this.toId = toId;
        this.channel = channel;
        this.transmissionID = ("" + timestamp + fromId + toId + channel).hashCode();
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
