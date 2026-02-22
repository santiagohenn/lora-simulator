package assets;

public class Transmitter {

    private final int id;
    private Transmission lastTransmission;
    private boolean ready;
    private int transmissionsCount;

    public Transmitter(int id) {
        this.id = id;
        ready = true;
        transmissionsCount = 0;
    }

    public Transmission getLastTransmission() {
        return lastTransmission;
    }

    public void transmit(Transmission lastTransmission) {
        this.transmissionsCount++;
        this.lastTransmission = lastTransmission;
        block();
    }

    public boolean updateStatus(double time) {

        if (this.lastTransmission == null) {
            this.ready = true;
            return isReady();
        }

        if (this.lastTransmission.getEndDutyCycleSilentTimestamp() <= time) {
            this.unblock();
        }
        return isReady();
    }

    public boolean isReady() {
        return ready;
    }

    public void unblock() {
        setReady(true);
    }

    public void block() {
        setReady(false);
    }

    private void setReady(boolean ready) {
        this.ready = ready;
    }

    public int getId() {
        return id;
    }

    public int getTransmissionsCount() {
        return transmissionsCount;
    }

}
