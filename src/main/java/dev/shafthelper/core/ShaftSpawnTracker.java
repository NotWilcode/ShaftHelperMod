package dev.shafthelper.core;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class ShaftSpawnTracker {

    private static final int MAX_SAMPLES = 10;

    private static final double MIN_SPAWN_MINUTES = 0.25;
    private static final double MAX_SPAWN_MINUTES = 10.0;

    private final Deque<Double> timeSamples = new ArrayDeque<>();
    private final Deque<Double> rateSamples = new ArrayDeque<>();

    private long shaftExitTimeMillis = -1;
    private double shaftExitProfit = -1;

    /** Called when leaving a shaft.
     * totalProfit is the cumulative PristineTracker profit at that moment. */
    public synchronized void onShaftExit(double totalProfit) {
        shaftExitTimeMillis = System.currentTimeMillis();
        shaftExitProfit = totalProfit;
    }

    /** Called when entering the next shaft.
     * Returns the measured spawn time in minutes, or -1 if invalid. */
    public synchronized double onShaftEnter(double totalProfit) {
        if (shaftExitTimeMillis <= 0 || shaftExitProfit < 0) {
            return -1;
        }

        long now = System.currentTimeMillis();

        double minutes = (now - shaftExitTimeMillis) / 60_000.0;

        double profit = Math.max(0.0, totalProfit - shaftExitProfit);

        // Clear the active measurement regardless of validity.
        shaftExitTimeMillis = -1;
        shaftExitProfit = -1;

        if (minutes < MIN_SPAWN_MINUTES ||
            minutes > MAX_SPAWN_MINUTES) {
            return -1;
        }

        double coinsPerHour = profit * 60.0 / minutes;

        addSample(minutes, coinsPerHour);

        return minutes;
    }

    private void addSample(double minutes, double coinsPerHour) {
        timeSamples.addLast(minutes);
        rateSamples.addLast(coinsPerHour);

        while (timeSamples.size() > MAX_SAMPLES) {
            timeSamples.removeFirst();
            rateSamples.removeFirst();
        }
    }

    public synchronized boolean hasSpawnTime() {
        return !timeSamples.isEmpty();
    }

    /* Median spawn time. */
    public synchronized double getAverageSpawnMinutes() {
        if (timeSamples.isEmpty()) {
            return -1;
        }

        double[] values = new double[timeSamples.size()];

        int i = 0;
        for (double value : timeSamples) {
            values[i++] = value;
        }

        Arrays.sort(values);

        int middle = values.length / 2;

        if (values.length % 2 == 0) {
            return (values[middle - 1] + values[middle]) / 2.0;
        }

        return values[middle];
    }

    /**
     * Median spawn-only coins/hour.
     *
     * This is deliberately median rather than a simple average so that
     * one bad/incomplete spawn cycle doesn't heavily distort the optimizer.
     */
    public synchronized double getAverageSpawnCoinsPerHour() {
        if (rateSamples.isEmpty()) {
            return -1;
        }

        double[] values = new double[rateSamples.size()];

        int i = 0;
        for (double value : rateSamples) { values[i++] = value; }

        Arrays.sort(values);

        int middle = values.length / 2;

        if (values.length % 2 == 0) {
            return (values[middle - 1] + values[middle]) / 2.0;
        }

        return values[middle];
    }

    public synchronized double getLatestSpawnMinutes() {
        return timeSamples.isEmpty()
            ? -1
            : timeSamples.peekLast();
    }

    public synchronized double getLatestSpawnCoinsPerHour() {
        return rateSamples.isEmpty()
            ? -1
            : rateSamples.peekLast();
    }

    public synchronized void reset() {
        timeSamples.clear();
        rateSamples.clear();

        shaftExitTimeMillis = -1;
        shaftExitProfit = -1;
    }
}