package assets;

public class Transmitter {

    private int id;
    private Transmission lastTransmission;
    private boolean blocked;

    public Transmitter(int id) {
        this.id = id;
    }

    public Transmission getLastTransmission() {
        return lastTransmission;
    }

    public void setLastTransmission(Transmission lastTransmission) {
        this.lastTransmission = lastTransmission;
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
