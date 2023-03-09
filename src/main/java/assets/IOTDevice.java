package assets;

import satellite.tools.assets.entities.Position;

public class IOTDevice {

    Transmitter tx;
    Position position;

    public IOTDevice(Transmitter tx, Position position) {
        this.tx = tx;
        this.position = position;
    }

    public Transmitter getTx() {
        return tx;
    }

    public Position getPosition() {
        return position;
    }

}
