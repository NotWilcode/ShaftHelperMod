package dev.shafthelper.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Format;
import dev.shafthelper.core.Gemstone;
import dev.shafthelper.core.HttpFetcher;
import dev.shafthelper.core.Mining;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.Pristine;
import dev.shafthelper.core.ProcTracker;
import dev.shafthelper.core.ShaftDetector;
import dev.shafthelper.core.ShaftLog;
import dev.shafthelper.core.StatsParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Scans the tab list and scoreboard every couple of seconds: reads mining stats from Hypixel's
 * Stats widget (autoStats), spots the mineshaft the player is in by its JASP_C-style code,
 * tracks lapis corpses in the current shaft, and keeps the HUD lines and Bazaar prices
 * (refreshed every 10 minutes) up to date.
 */
public final class ShaftTracker {

    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final long PRICE_REFRESH_MS = 10 * 60 * 1000;

    private static final HttpFetcher FETCHER = HttpFetcher.real();
    private static final ShaftLog LOG = new ShaftLog();
    private static final ProcTracker PROCS = new ProcTracker();

    private static ModConfig config;
    private static Path configPath;
    private static int ticks;
    private static volatile Map<String, Double> prices;
    private static volatile long pricesFetchedAt;
    private static volatile boolean fetchingPrices;
    private static volatile List<Component> trackerLines = List.of();  
    private static volatile List<Component> logLines = List.of();  
    private static volatile List<Component> profitLines = List.of();

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("shafthelper.json");
        config = ModConfig.load(configPath);
    }

    public static ModConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.save(configPath);
        refreshHudLines();
    }

    public static ShaftLog log() {
        return LOG;
    }

    public static List<Component> trackerLines() { return trackerLines; }
    public static List<Component> profitLines() { return profitLines; }
    public static List<Component> logLines() { return logLines; }

    /** Chat listener hook: counts Hypixel's "PRISTINE!" proc messages for the profit tracker. */
    public static void onGameMessage(Component message) {
        String messageStr = message.getString();
        String lower = messageStr.toLowerCase(Locale.ROOT);  
        Map<String, Double> current = prices;
        if (PROCS.record(messageStr, current, config.pristine, System.currentTimeMillis())) {
            refreshHudLines();
        } else if (ProcTracker.isLobbySwitch(messageStr)) {
            double finalProfit = PROCS.totalProfit();
            LOG.leave(finalProfit);
            PROCS.resetSession();
            currentLapisCorpses = -1;
            currentUmberCorpses = -1;
            currentTungstenCorpses = -1;
            detectedShaft = Optional.empty();
            refreshHudLines();
        }
    }

    public static void onEndTick(Minecraft client) {
        if (++ticks % SCAN_INTERVAL_TICKS != 0) return;

        ClientPacketListener connection = client.getConnection();
        if (connection == null || client.level == null) {
            double finalProfit = PROCS.totalProfit();
            LOG.leave(finalProfit);
            PROCS.resetAll();
            currentLapisCorpses = -1;
            currentUmberCorpses = -1;
            currentTungstenCorpses = -1;
            detectedShaft = Optional.empty();
            trackerLines = List.of();
            return;
        }

        List<String> lines = collectLines(client, connection);
        if (config.autoStats) readStats(lines);
        readLapisCorpses(lines);
        detectedArea = AreaDetector.detect(lines);
  
        if (detectedShaft.isPresent() && (currentLapisCorpses >= 0 || currentUmberCorpses >= 0 || currentTungstenCorpses >= 0)) {  
            LOG.updateCurrentCorpses(currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses);  
        }

        Optional<ShaftDetector.Shaft> shaft = ShaftDetector.detect(lines);
        
        // Check if shaft status changed (entered, left, or switched)
        if (shaft.isPresent() && !detectedShaft.isPresent()) {
            // Entering a shaft
            double initialProfit = PROCS.totalProfit();
            LOG.enter(shaft.get(), currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses, System.currentTimeMillis(), initialProfit);
            detectedShaft = shaft;
        } else if (shaft.isPresent() && detectedShaft.isPresent() && !shaft.get().code().equals(detectedShaft.get().code())) {
            // Switching shafts
            double finalProfit = PROCS.totalProfit();
            LOG.leave(finalProfit);
            double initialProfit = PROCS.totalProfit();
            LOG.enter(shaft.get(), currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses, System.currentTimeMillis(), initialProfit);
            detectedShaft = shaft;
        } else if (!shaft.isPresent() && detectedShaft.isPresent()) {
            // Leaving a shaft
            double finalProfit = PROCS.totalProfit();
            LOG.leave(finalProfit);
            detectedShaft = Optional.empty();
        }

        applyMineshaftGroupToggle();

        if (config.trackerEnabled && config.miningSpeed > 0) refreshPrices();
            refreshHudLines();
    }

    public static Optional<ShaftDetector.Shaft> currentShaft() {
        return detectedShaft;
    }

    public static Optional<AreaDetector.Area> currentArea() {
        return detectedArea;
    }

    public static int currentShaftLapisCorpses() {
        return currentLapisCorpses;
    }

    private static List<String> collectLines(Minecraft client, ClientPacketListener connection) {  
        List<String> lines = new ArrayList<>();  
    
        // Tab list (unchanged)  
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {  
            Component name = info.getTabListDisplayName();  
            lines.add(name != null ? name.getString() : info.getProfile().name());  
        }  
    
        Scoreboard scoreboard = client.level.getScoreboard();  
    
        // Sidebar objective: rebuild each line as prefix + entryName + suffix  
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);  
        if (sidebar != null) {  
            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {  
                String owner = entry.owner();  
                PlayerTeam team = scoreboard.getPlayersTeam(owner);  
                // formatNameForTeam applies prefix + name + suffix exactly like vanilla renders it  
                String full = PlayerTeam.formatNameForTeam(team, Component.literal(owner)).getString();  
                lines.add(full);  
            }  
        }  
    
        // Fallback: keep the old team prefix+suffix scan so tab/team-based servers still work  
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {  
            lines.add(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());  
        }  
    
        return lines;  
    }

    private static void readStats(List<String> lines) {
        StatsParser.Stats stats = StatsParser.parse(lines);
        boolean changed = false;
        if (stats.miningSpeed() != null && (int) (double) stats.miningSpeed() != config.miningSpeed) {
            config.miningSpeed = (int) (double) stats.miningSpeed();
            changed = true;
        }
        if (stats.miningFortune() != null && (int) (double) stats.miningFortune() != config.miningFortune) {
            config.miningFortune = (int) (double) stats.miningFortune();
            changed = true;
        }
        if (stats.gemstoneFortune() != null && (int) (double) stats.gemstoneFortune() != config.gemstoneFortune) {
            config.gemstoneFortune = (int) (double) stats.gemstoneFortune();
            changed = true;
        }
        if (stats.gemstoneSpread() != null && (int) (double) stats.gemstoneSpread() != config.gemstoneSpread) {
            config.gemstoneSpread = (int) (double) stats.gemstoneSpread();
            changed = true;
        }
        if (stats.pristine() != null && stats.pristine() != config.pristine) {
            config.pristine = stats.pristine();
            changed = true;
        }
        if (changed) config.save(configPath);
    }

    /** Current lapis corpses in the shaft (0-4), or -1 if not detected. */
    private static volatile int currentLapisCorpses = -1;
    private static volatile int currentUmberCorpses = -1;
    private static volatile int currentTungstenCorpses = -1;

    /** Currently detected shaft from tab list (separate from log for proper tracking) */
    private static volatile Optional<ShaftDetector.Shaft> detectedShaft = Optional.empty();

    /** Currently detected area from tab list/scoreboard */
    private static volatile Optional<AreaDetector.Area> detectedArea = Optional.empty();

    public static int currentLapisCorpses() {
        return currentLapisCorpses;
    }

    public static int currentUmberCorpses() {
        return currentUmberCorpses;
    }

    public static int currentTungstenCorpses() {
        return currentTungstenCorpses;
    }

    private static void readLapisCorpses(List<String> lines) {
        StatsParser.Stats stats = StatsParser.parse(lines);
        if (stats.lapisCorpses() != null) {
            currentLapisCorpses = (int) (double) stats.lapisCorpses();
        } else {
            currentLapisCorpses = -1;
        }
        if (stats.umberCorpses() != null) {
            currentUmberCorpses = (int) (double) stats.umberCorpses();
        } else {
            currentUmberCorpses = -1;
        }
        if (stats.tungstenCorpses() != null) {
            currentTungstenCorpses = (int) (double) stats.tungstenCorpses();
        } else {
            currentTungstenCorpses = -1;
        }
    }

    private static void refreshPrices() {  
        long now = System.currentTimeMillis();  
        if (fetchingPrices || (prices != null && now - pricesFetchedAt < PRICE_REFRESH_MS)) return;  
        fetchingPrices = true;  
        Prices.load(config.priceMode(), config.priceDataMode(), config.priceBasisMode(), FETCHER)  
            .whenComplete((result, error) -> {  
                if (error == null && result != null && result.prices() != null) {  
                    prices = result.prices();  
                    pricesFetchedAt = System.currentTimeMillis();  
                    refreshHudLines();  
                }  
                fetchingPrices = false;  
            });  
    }

    private static void refreshHudLines() {
        if (!config.trackerEnabled) {   
            trackerLines = List.of();  
            profitLines = List.of();  
            logLines = List.of();  
            return;  
        }  
        Map<String, Double> current = prices;  
  
        // --- Tracker box: header + overview ---  
        List<Component> tracker = new ArrayList<>();  
        if (config.miningSpeed <= 0) {  
            tracker.add(header("Shaft Helper"));  
            tracker.add(gray("Waiting for stats (tab list Stats widget)"));  
        } else if (current == null) {  
            tracker.add(header("Shaft Helper (fetching prices...)"));  
        } else {  
            tracker.add(header("Shaft Helper (vs " + config.benchmark + ")"));  
            if (currentShaft().isPresent() && currentShaftLapisCorpses() >= 0 && currentShaft().get().gem().isGemstone()) {  
                ShaftDetector.Shaft shaft = currentShaft().get();  
                int lapisCorpses = currentShaftLapisCorpses();  
                double profitWithCorpses = calculateCurrentShaftProfit(shaft.gem(), lapisCorpses, current);  
                tracker.add(gemColored(shaft.gem(), "Current " + shaft.gem().name() + " shaft (" + lapisCorpses + " lapis)")  
                    .append(gray(": " + Format.compact(profitWithCorpses) + "/hr")));  
            }  
            tracker.addAll(overviewLines(current));  
        }  
        trackerLines = tracker;  
  
        // --- Profit box: total profit / tracker estimate ---  
        List<Component> profit = new ArrayList<>();  
        if (config.miningSpeed > 0 && current != null) {  
            profit.addAll(trackerLine(current));  
        }  
        profitLines = profit;  
  
        // --- Log box: this session's shafts ---  
        List<Component> log = new ArrayList<>();  
        if (!LOG.isEmpty()) {  
            log.add(gray("This session:"));  
            for (ShaftLog.Entry entry : LOG.entries()) {  
                String shaftLabel = entry.number() + " " + entry.code();  
                String corpseLabel = "";  
                if (entry.lapisCorpses() >= 0) corpseLabel += entry.lapisCorpses() + "l";  
                if (entry.umberCorpses() >= 0) corpseLabel += entry.umberCorpses() + "u";  
                if (entry.tungstenCorpses() >= 0) corpseLabel += entry.tungstenCorpses() + "t";  
                if (!corpseLabel.isEmpty()) shaftLabel += " " + corpseLabel;  
                log.add(gemColored(entry.gem(), shaftLabel));  
                if (currentShaft().isPresent() && currentShaft().get().gem().name().equals(entry.gem().name())  
                    && currentShaft().get().code().equals(entry.code())) {  
                    double shaftProfit = PROCS.totalProfit() - entry.initialProfit();  
                    if (shaftProfit > 0) log.add(gray("  Total: " + Format.compact(shaftProfit)));  
                } else {  
                    double shaftProfit = entry.finalProfit() - entry.initialProfit();  
                    if (shaftProfit > 0) log.add(gray("  Total: " + Format.compact(shaftProfit)));  
                }  
            }  
        }  
        logLines = log;  
    }

    /** What /shaft answers, one line per gemstone: mine it (and with how many lapis) or skip it. */
    private static List<Component> overviewLines(Map<String, Double> current) {
        List<Mining.Ranked> ranked = Mining.rankByProfit(
            Mining.calculateBreakdown(config.miningSpeed, config.miningFortune, config.gemstoneFortune, config.gemstoneSpread), current, config.pristine);
        
        // Filter out non-gemstone shafts from the helper display
        List<Mining.Ranked> gemstoneRanked = ranked.stream()
            .filter(gem -> gem.gem().isGemstone())
            .toList();
        
        Mining.Ranked reference = gemstoneRanked.stream()
            .filter(gem -> gem.name().equalsIgnoreCase(config.benchmark))
            .findFirst().orElse(gemstoneRanked.get(0));
        return Pristine.corpseTable(gemstoneRanked, current, reference, config.pristine, Pristine.MAX_LAPIS_CORPSES)
            .stream()
            .sorted((a, b) -> Double.compare(b.coinsWithCorpses(), a.coinsWithCorpses()))
            .map(row -> {
                String status = switch (row.status()) {
                    case REFERENCE -> "benchmark, " + Format.compact(row.coinsWithCorpses()) + "/hr";
                    case AHEAD -> "mine, " + Format.compact(row.coinsWithCorpses()) + "/hr";
                    case REACHABLE -> row.corpses() + " lapis, " + Format.compact(row.coinsWithCorpses()) + "/hr";
                    case OUT_OF_REACH, IMPOSSIBLE -> Format.compact(row.coinsWithCorpses()) + "/hr";
                };
                ChatFormatting color = switch (row.status()) {
                    case REFERENCE -> ChatFormatting.AQUA;
                    case AHEAD, REACHABLE -> ChatFormatting.GREEN;
                    case OUT_OF_REACH, IMPOSSIBLE -> ChatFormatting.RED;
                };
                return (Component) gemColored(row.ranked().gem(), row.name())
                    .append(Component.literal(": " + status).withStyle(color));
            })
            .toList();
    }

    /** Coins/hr the pristine procs actually earned, to hold against the theoretical ranking. */
    private static List<Component> trackerLine(Map<String, Double> current) {
        List<Component> lines = new ArrayList<>();
        PROCS.estimate(current, config.pristine, System.currentTimeMillis())
            .ifPresent(estimate -> {
                lines.add(Component.literal("Tracker: " + estimate.procs() + " proc"
                        + (estimate.procs() == 1 ? "" : "s") + " in " + minutes(estimate.elapsedMs()) + " — ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("~" + Format.compact(estimate.coinsPerHour()) + "/hr")
                        .withStyle(ChatFormatting.GOLD)));
                
                // Add total profit line under the estimated $/h
                double totalProfit = PROCS.totalProfit();
                if (totalProfit > 0) {
                    lines.add(Component.literal("Total Profit: " + Format.compact(totalProfit))
                        .withStyle(ChatFormatting.GREEN));
                }
            });
        
        return lines;
    }

    private static String minutes(long elapsedMs) {
        long minutes = elapsedMs / 60_000;
        return minutes < 1 ? "<1m" : minutes < 60 ? minutes + "m" : (minutes / 60) + "h" + (minutes % 60) + "m";
    }

    private static MutableComponent gemColored(Gemstone gem, String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(gem.color())));
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent header(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static String describe(ShaftLog.Entry entry, Map<String, Double> current) {
        if (current == null || config.miningSpeed <= 0) return "...";
        Pristine.Comparison comparison = ShaftLog.compare(entry.gem(), config.benchmark,
            config.miningSpeed, config.miningFortune, config.gemstoneFortune, config.gemstoneSpread, config.pristine, current);
        return switch (comparison.status()) {
            case REFERENCE -> config.benchmark.toLowerCase(Locale.ROOT) + " benchmark";
            case AHEAD -> "0 lapis, already ahead";
            case REACHABLE -> comparison.corpses() + " lapis";
            case OUT_OF_REACH -> "needs " + Format.compact(comparison.bonus()) + " Pristine, out of reach";
            case IMPOSSIBLE -> "can't catch up";
        };
    }

    private static double calculateCurrentShaftProfit(Gemstone gem, int lapisCorpses, Map<String, Double> prices) {
        List<Mining.Breakdown> breakdown = Mining.calculateBreakdown(config.miningSpeed, config.miningFortune, config.gemstoneFortune, config.gemstoneSpread);
        Mining.Breakdown shaftBreakdown = breakdown.stream()
            .filter(b -> b.gem().name().equals(gem.name()))
            .findFirst()
            .orElseThrow();
        double effectivePristine = config.pristine + lapisCorpses;
        return Mining.coinsPerHour(shaftBreakdown, prices, effectivePristine);
    }

    private static void applyMineshaftGroupToggle() {  
        if (config == null) return;  
    
        // Only auto-toggle inside Mineshafts; leave Dwarven/island-only groups alone.  
        if (detectedArea.orElse(AreaDetector.Area.UNKNOWN) != AreaDetector.Area.MINESHAFTS) return;  
    
        String shaftCode = detectedShaft.map(ShaftDetector.Shaft::code).orElse("");  
        boolean changed = false;  
        for (dev.shafthelper.core.Waypoint wp : config.waypoints) {  
            // Only touch shaft-scoped groups (those with an underscore, e.g. TOPA_1).  
            if (wp.group == null || wp.group.lastIndexOf('_') < 0) continue;  
            boolean shouldEnable = WaypointRenderer.groupMatchesShaft(wp.group, shaftCode);  
            if (wp.enabled != shouldEnable) {  
                wp.enabled = shouldEnable;  
                changed = true;  
            }  
        }  
        if (changed) saveConfig();  
    }

    private ShaftTracker() {}
}
