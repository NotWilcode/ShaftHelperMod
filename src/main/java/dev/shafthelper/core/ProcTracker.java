package dev.shafthelper.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tracks the "PRISTINE!" procs Hypixel prints in chat and turns them into the coins/hr actually
 * being earned. Procs only show the flawed drops, so the rough gems mined alongside them are
 * extrapolated from the Pristine chance: at P% Pristine, each proc'd block implies (100-P)/P
 * blocks that dropped rough.
 */
public final class ProcTracker {

    /** e.g. "PRISTINE! You found :gem-emoji: Flawed :Gem: Gemstone x28!" */
    private static final Pattern PROC = Pattern.compile(
        "(?i)PRISTINE!.*?Flawed\\s*:?([A-Za-z ]+?):?\\s*Gemstone\\s*x?\\s*([\\d,]+)");

    /** Lobby switch messages that indicate the player is changing servers */
    private static final Pattern LOBBY_SWITCH = Pattern.compile(
        "(?i)(Warping\\.\\.\\.|Sending to server)");

    private final Map<Gemstone, Long> flawed = new LinkedHashMap<>();
    private long startedAt;
    private int procs;
    private double totalProfit;

    private static final Map<String, Gemstone> BY_NAME = Gemstones.ALL.stream()
        .collect(Collectors.toUnmodifiableMap(
            gem -> gem.name().toLowerCase(Locale.ROOT), gem -> gem));

    /** Feeds a chat line through the proc pattern; the clock starts on the first proc. */
    public synchronized boolean record(String message, long now) {
        return record(message, null, 0, now);
    }

    /** Feeds a chat line through the proc pattern with prices for estimated profit tracking only. */
    public synchronized boolean record(String message, Map<String, Double> prices, double pristine, long now) {  
        if (message == null) return false;  
        Matcher matcher = PROC.matcher(message.replaceAll("\u00a7.", ""));  
        if (!matcher.find()) return false;  
        Gemstone gem = BY_NAME.get(matcher.group(1).trim().toLowerCase(Locale.ROOT));  
        if (gem == null) return false;  
        long count = Long.parseLong(matcher.group(2).replace(",", ""));  
        if (procs == 0) startedAt = now;  
        flawed.merge(gem, count, Long::sum);  
        procs += 1;  
        return true;  
    }

    /** Checks if a message indicates a lobby switch (for resetting the session tracker). */
    public static boolean isLobbySwitch(String message) {
        if (message == null) return false;
        return LOBBY_SWITCH.matcher(message.replaceAll("\u00a7.", "")).find();
    }

    public synchronized void reset() {
        flawed.clear();
        procs = 0;
        startedAt = 0;
    }

    /** Resets only session data (procs, flawed, timing) but preserves total profit. */
    public synchronized void resetSession() {
        flawed.clear();
        procs = 0;
        startedAt = 0;
    }

    /** Resets everything including total profit (on disconnect). */
    public synchronized void resetAll() {
        resetSession();
        totalProfit = 0;
    }

    public synchronized int procs() {
        return procs;
    }

    public synchronized double totalProfit() {
        return totalProfit;
    }

    public record Estimate(int procs, long elapsedMs, double flawedValue, double coinsPerHour) {}

    /** Value earned so far and the coins/hr it works out to over the time since the first proc. */
    public synchronized Optional<Estimate> estimate(Map<String, Double> prices, double pristine, long now) {
        if (procs == 0 || prices == null) return Optional.empty();
        double flawedValue = 0;
        double roughValue = 0;
        double chance = Mining.pristineChance(pristine);
        for (Map.Entry<Gemstone, Long> entry : flawed.entrySet()) {
            Gemstone gem = entry.getKey();
            long count = entry.getValue();
            flawedValue += count * prices.getOrDefault(gem.flawedId(), 0.0);
            if (chance > 0) {
                roughValue += count * ((1 - chance) / chance) * prices.getOrDefault(gem.roughId(), 0.0);
            }
        }
        long elapsed = Math.max(now - startedAt, 1);
        double perHour = (flawedValue + roughValue) * 3600_000.0 / elapsed;
        return Optional.of(new Estimate(procs, elapsed, flawedValue, perHour));
    }
}
