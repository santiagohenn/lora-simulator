public class Transmission {

    private final long startTransmissionTimestamp;
    private final int fromId;
    private final int toId;
    private final int channel;
    private long endTransmissionTimestamp;

    public Transmission(long timestamp, int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = timestamp;
        this.fromId = fromId;
        this.toId = toId;
        this.channel = channel;
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

    @Override
    public String toString() {
        return startTransmissionTimestamp + "," + fromId + "," + toId + "," + channel;
    }
}
