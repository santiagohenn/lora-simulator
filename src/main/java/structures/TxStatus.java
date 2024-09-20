package structures;

public enum TxStatus {

    SUCCESS("Packet successfully arrived at the server"),
    SUCCESS_PARTIAL("Partial packet successfully arrived at the server"),
    REACHED_GW("Packet arrived at the gateway"),
    REACHED_GW_PARTIAL("Part of the packet arrived at the gateway"),
    LOST_COLLISION("Packet lost due to collision"),
    LOST_COLLISION_PARTIAL("Partial Packet lost due to collision"),
    LOST_NOT_HEARD("Packet lost due to not reaching any GW");

    private final String description;

    TxStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
