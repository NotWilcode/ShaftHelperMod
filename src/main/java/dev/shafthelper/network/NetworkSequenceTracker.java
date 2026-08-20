package dev.shafthelper.network;

public interface NetworkSequenceTracker {
    void trackSent(int sequence);
    void trackAck(int sequence);
}