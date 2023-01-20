package assets;

public class Transmisor {

    private int id;
    private long lastTxTime;
    private boolean blocked;

    public Transmisor(int id) {
        this.id = id;
    }

    public long getLastTxTime() {
        return lastTxTime;
    }

    public void setLastTxTime(long lastTxTime) {
        this.lastTxTime = lastTxTime;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void block() {

    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

}
