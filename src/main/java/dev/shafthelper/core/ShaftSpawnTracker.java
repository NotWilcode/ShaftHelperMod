package dev.shafthelper.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks the amount of time spent spawning a new Mineshaft.
 *
 * A spawn cycle is:
 *
 *     Leave previous shaft
 *              ↓
 *     Mine Jade / Glacite / Pickobulus
 *              ↓
 *     New shaft appears
 *
 * The measured time is then supplied to MiningCalc.
 */
public final class ShaftSpawnTracker {

    /*
     * Number of previous spawn times kept for the rolling average.
     *
     * 10 means the optimizer won't immediately swing wildly because
     * one shaft took longer than normal.
     */
    private static final int MAX_SAMPLES = 10;

    /*
     * Ignore extremely short / extremely long measurements.
     *
     * These protect against things like:
     * - teleporting
     * - reconnecting
     * - accidentally triggering a shaft immediately
     * - AFKing
     */
    private static final double MIN_SPAWN_MINUTES = 0.25;
    private static final double MAX_SPAWN_MINUTES = 10.0;

    private final Deque<Double> samples = new ArrayDeque<>();

    private long shaftExitTimeMillis = -1;

    /**
     * Call this when the player leaves a Mineshaft.
     */
    public void onShaftExit() {
        shaftExitTimeMillis = System.currentTimeMillis();
    }

    /** Call this when the player enters a newly spawned Mineshaft.
     *
     * Returns the measured spawn time in minutes.
     * Returns -1 if there wasn't a valid previous shaft exit.
     */
    public double onShaftEnter() {
        if (shaftExitTimeMillis <= 0) {
            return -1;
        }

        long now = System.currentTimeMillis();

        double minutes =
            (now - shaftExitTimeMillis) / 60000.0;

        // Consume the timestamp.
        shaftExitTimeMillis = -1;

        if (minutes < MIN_SPAWN_MINUTES ||
            minutes > MAX_SPAWN_MINUTES) {
            return -1;
        }

        addSample(minutes);

        return minutes;
    }

    /* Adds a spawn-time measurement. */
    public void addSample(double minutes) {
        if (minutes < MIN_SPAWN_MINUTES ||
            minutes > MAX_SPAWN_MINUTES) {
            return;
        }

        samples.addLast(minutes);

        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    /* Returns the rolling average spawn time.
     * Returns -1 if we don't have enough data. */
    public double getAverageSpawnMinutes() {
        if (samples.isEmpty()) {
            return -1;
        }

        double total = 0;
        for (double sample : samples) {
            total += sample;
        }
        return total / samples.size();
    }

    /* Returns the number of recorded samples. */
    public int getSampleCount() {
        return samples.size();
    }

    /* Returns the most recent measured spawn time. */
    public double getLatestSpawnMinutes() {
        if (samples.isEmpty()) {
            return -1;
        }
        return samples.peekLast();
    }

    /* Returns true if we currently have a valid spawn-time estimate. */
    public boolean hasSpawnTime() {
        return !samples.isEmpty();
    }

    /**
     * Clears the current timing state.
     *
     * Useful when:
     * - changing islands
     * - disconnecting
     * - leaving the mining area
     * - resetting the session
     */
    public void reset() {
        samples.clear();
        shaftExitTimeMillis = -1;
    }
}