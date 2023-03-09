package assets;

public class Transmitter {

    private final int id;
    private Transmission lastTransmission;
    private boolean blocked;

    public Transmitter(int id) {
        this.id = id;
        blocked = false;
    }

    public Transmission getLastTransmission() {
        return lastTransmission;
    }

    public void transmit(Transmission lastTransmission) {
        this.lastTransmission = lastTransmission;
        block();
    }

    public void updateStatus(long timeMS) {
        if (this.isBlocked() && this.lastTransmission.getEndDutyCycleSilentTime() <= timeMS) {
            this.unblock();
        }
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void unblock() {
        setBlocked(false);
    }

    public void block() {
        setBlocked(true);
    }

    private void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public int getId() {
        return id;
    }

}
