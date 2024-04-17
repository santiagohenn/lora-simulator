package structures;

public enum TransmissionStatus {

    SUCCESS("Packet successfully arrived at the server"),
    SUCCESS_PARTIAL("Packet partially arrived at the server"),
    LOST_COLLISION("Packet lost due to collision"),
    LOST_COLLISION_PARTIAL("Partial Packet lost due to collision"),
    LOST_NOT_HEARD("Packet lost due to not being heard");

    private final String description;

    TransmissionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
