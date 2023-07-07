package network;

public record DataPoint(double time, double successfulPackets, double collidedPackets, double totalPackets, double partialFrames) {
}
