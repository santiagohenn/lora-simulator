public class Transmission {

    private final long timestamp;
    private final int fromId;
    private final int toId;
    private final int channel;

    public Transmission(long timestamp, int fromId, int toId, int channel) {
        this.timestamp = timestamp;
        this.fromId = fromId;
        this.toId = toId;
        this.channel = channel;
    }

    public long getTimestamp() {
        return timestamp;
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

    @Override
    public String toString() {
        return timestamp + "," + fromId + "," + toId + "," + channel;
    }
}
