package assets;

import org.jetbrains.annotations.NotNull;
import structures.TxStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Status reference:
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
    private double endDutyCycleSilentTimestamp;
    private double timeOnAirMilliSeconds;
    private double startReceptionTimestamp;
    private double endReceptionTimestamp;
    private int status = 0;
    private TxStatus txStatus;

    /**
     * Single gateway constructor
     **/
    public Transmission(double startTransmissionTimestamp, double endTransmissionTimestamp, double endDutyCycleSilentTime,
                        int fromId, int toId, int channel) {
        this.startTransmissionTimestamp = startTransmissionTimestamp;
        this.endTransmissionTimestamp = endTransmissionTimestamp;
        this.startReceptionTimestamp = startTransmissionTimestamp;
        this.endReceptionTimestamp = endTransmissionTimestamp;
        this.timeOnAirMilliSeconds = endTransmissionTimestamp - startTransmissionTimestamp;
        this.endDutyCycleSilentTimestamp = endDutyCycleSilentTime;
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
        this.endDutyCycleSilentTimestamp = endDutyCycleSilentTime;
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
        this.endDutyCycleSilentTimestamp = endTransmissionTimestamp;
    }

    public double getTimeOnAirMilliSeconds() {
        return timeOnAirMilliSeconds;
    }

    public void setTimeOnAirMilliSeconds(long timeOnAirMilliSeconds) {
        this.timeOnAirMilliSeconds = timeOnAirMilliSeconds;
    }

    public double getEndDutyCycleSilentTimestamp() {
        return endDutyCycleSilentTimestamp;
    }

    public void setEndDutyCycleSilentTimestamp(long endDutyCycleSilentTimestamp) {
        this.endDutyCycleSilentTimestamp = endDutyCycleSilentTimestamp;
    }

    public double getStartReceptionTimestamp() {
        return startReceptionTimestamp;
    }

    public void setStartReceptionTimestamp(double startReceptionTimestamp) {
        this.startReceptionTimestamp = startReceptionTimestamp;
    }

    public double getEndReceptionTimestamp() {
        return endReceptionTimestamp;
    }

    public void setEndReceptionTimestamp(double endReceptionTimestamp) {
        this.endReceptionTimestamp = endReceptionTimestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public TxStatus getTransmissionStatus() {
        return txStatus;
    }

    public void setTransmissionStatus(TxStatus txStatus) {
        this.txStatus = txStatus;
    }

    public int getTransmissionHash() {
        return transmissionHash;
    }

    public boolean reachedDestination() {
        return  this.txStatus == TxStatus.REACHED_GW || this.txStatus == TxStatus.REACHED_GW_PARTIAL;
    }

    @Override
    public String toString() {
        return "Start Transmission Timestamp: " + startTransmissionTimestamp + "\n" +
                "End Transmission Timestamp: " + endTransmissionTimestamp + "\n" +
                "Start Reception Timestamp: " + startReceptionTimestamp + "\n" +
                "End Reception Timestamp: " + endReceptionTimestamp + "\n" +
                "From ID: " + fromId + "\n" +
                "To Assets: " + toAssets + "\n" +
                "Channel: " + channel + "\n" +
                "Status: " + status + "\n" +
                "Transmission Hash: #" + transmissionHash;
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
        return Double.compare(this.startTransmissionTimestamp, o.startTransmissionTimestamp);
    }

    @Override
    public int hashCode() {
        return ("" + startTransmissionTimestamp + fromId + channel).hashCode();
    }

}
