import satellite.tools.assets.entities.Position;

public class IOTDevice {

    Transmisor tx;
    Position position;

    public IOTDevice(Transmisor tx, Position position) {
        this.tx = tx;
        this.position = position;
    }

    public Transmisor getTx() {
        return tx;
    }

    public Position getPosition() {
        return position;
    }

}
