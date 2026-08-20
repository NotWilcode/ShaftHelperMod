package dev.shafthelper.core;

public final class Cold {

    /** Cold ticks up by 1 every 5 seconds in a mineshaft, and 100 Cold gets you thrown out. */
    public static final int SECONDS_PER_COLD = 5;
    public static final int COLD_LIMIT = 100;
    /** Every 100 Cold Resistance halves the rate, and 138.5 is as high as the stat goes. */
    public static final double MAX_COLD_RESISTANCE = 138.5;
    /** Mining is never non-stop: walking to the next vein, corpses. */
    public static final double DEFAULT_EFFICIENCY = 70;

    public static double secondsPerCold(double coldResistance) {
        if (coldResistance < 0) throw new IllegalArgumentException("coldResistance cannot be negative");
        return SECONDS_PER_COLD * (1 + coldResistance / 100);
    }

    /** How long you last in a shaft before 100 Cold kicks you out. */
    public static double shaftSeconds(double coldResistance) {
        return secondsPerCold(coldResistance) * COLD_LIMIT;
    }

    public record ShaftRun(double seconds, double minutes, double coins) {}

    /** Coins from one shaft: an hourly rate, the time Cold allows, and the share of it spent mining. */
    public static ShaftRun shaftProfit(double coinsPerHour, double coldResistance, double efficiency) {
        double seconds = shaftSeconds(coldResistance);
        return new ShaftRun(seconds, seconds / 60, coinsPerHour * seconds * (efficiency / 100) / 3600);
    }

    private Cold() {}
}
