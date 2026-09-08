package dev.shafthelper.client;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Cold;
import dev.shafthelper.core.CorpseLootParser;
import dev.shafthelper.core.DropTracker;
import dev.shafthelper.core.Format;
import dev.shafthelper.core.Gemstone;
import dev.shafthelper.core.HttpFetcher;
import dev.shafthelper.core.Mining;
import dev.shafthelper.core.MiningCalc;
import dev.shafthelper.core.MiningFiesta;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.Pristine;
import dev.shafthelper.core.ProcTracker;
import dev.shafthelper.core.ShaftDetector;
import dev.shafthelper.core.ShaftLog;
import dev.shafthelper.core.ShaftSpawnTracker;
import dev.shafthelper.core.StatsParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
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
    private static final DropTracker DROPS = new DropTracker();
    private static int disconnectScans;
    private static int leftMiningScans;

    private static ModConfig config;
    private static Path configPath;
    private static int ticks;
    private static volatile Map<String, Double> prices;
    private static volatile long pricesFetchedAt;
    private static volatile boolean fetchingPrices;
    private static volatile List<Component> trackerLines = List.of();  
    private static volatile List<Component> logLines = List.of();  
    private static volatile List<Component> profitLines = List.of();
    private static volatile MiningFiesta.State miningFiesta = MiningFiesta.State.empty();
    private static MayhemBuff mayhemBuff = MayhemBuff.NONE;

    private static final ShaftSpawnTracker SHAFT_SPAWN = new ShaftSpawnTracker();

    private enum MayhemBuff {
        NONE, MINING_FORTUNE, MINING_SPEED, COLD_RESISTANCE
    }

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

    /** Latest fetched Bazaar prices (product id -> unit price), or null before the first fetch. */  
    public static Map<String, Double> prices() {  
        return prices;  
    }  
  
    /** Corpse-loot display name -> current Bazaar unit price, or null if unknown. */  
    public static Double corpseItemUnitPrice(String displayName) {  
        Map<String, Double> current = prices;  
        if (current == null || displayName == null) return null;  
        String id = DropTracker.idFor(displayName);  
        if (id == null) return null;  
        Double price = current.get(id);  
        return (price != null && Double.isFinite(price) && price > 0) ? price : null;  
    }

    public static List<Component> trackerLines() { return trackerLines; }
    public static List<Component> profitLines() { return profitLines; }
    public static List<Component> logLines() { return logLines; } 
  
    public static void onGameMessage(Component message) {
        String messageStr = message.getString();
        String lower = messageStr.toLowerCase(Locale.ROOT);
        Map<String, Double> current = prices;

        if (lower.contains("mineshaft mayhem")) {
            mayhemBuff = lower.contains("mining fortune")
                ? MayhemBuff.MINING_FORTUNE
                : lower.contains("mining speed")
                    ? MayhemBuff.MINING_SPEED
                    : lower.contains("cold resistance")
                        ? MayhemBuff.COLD_RESISTANCE
                        : MayhemBuff.NONE;
        }

        CorpseLootParser.Loot corpseLoot =
            CorpseLootParser.parse(message);

        if (corpseLoot != null) {
            if (DROPS.recordCorpseLoot(corpseLoot)) {
                refreshHudLines();
            }
        } else if (PROCS.record(
            messageStr,
            current,
            config.pristine,
            System.currentTimeMillis()
        )) {
            refreshHudLines();
        } else if (config.dropTrackerEnabled && lower.contains("[sacks]")) {
            String hover = extractHoverText(message);

            if (DROPS.recordSackHover(hover)) {
                refreshHudLines();
            }
        }
    }
  
    /** Walks a chat component + siblings and concatenates every ShowText hover tooltip. */  
    private static String extractHoverText(Component component) {  
        StringBuilder sb = new StringBuilder();  
        collectHover(component, sb);  
        return sb.toString();  
    }  
  
    private static void collectHover(Component component, StringBuilder sb) {  
        var hover = component.getStyle().getHoverEvent();  
        if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowText showText) {  
            if (sb.length() > 0) sb.append('\n');  
            sb.append(showText.value().getString());  
        }  
        for (Component sibling : component.getSiblings()) {  
            collectHover(sibling, sb);  
        }  
    }
    public static void onEndTick(Minecraft client) {  
        if (++ticks % SCAN_INTERVAL_TICKS != 0) return;  
    
        ClientPacketListener connection = client.getConnection();  
        if (connection == null || client.level == null) {  
            // A warp restores the connection within a scan or two; only a *sustained*  
            // null is a real disconnect. Preserve data until we're sure.  
            if (++disconnectScans >= 4) {  
                double finalProfit = PROCS.totalProfit();  
                LOG.leave(finalProfit);  
                LOG.clear();  
                PROCS.resetAll();  
                DROPS.resetSession(); 
                SHAFT_SPAWN.reset(); 
                currentLapisCorpses = -1;  
                currentUmberCorpses = -1;  
                currentTungstenCorpses = -1;  
                detectedShaft = Optional.empty();  
                missingShaftScans = 0;  
                mayhemBuff = MayhemBuff.NONE;
                disconnectScans = 0;  
            }  
            return;  
        }  
        disconnectScans = 0;  
    
        List<String> lines = collectLines(client, connection);  
        miningFiesta = MiningFiesta.parse(lines);
        if (config.autoStats) readStats(client, lines);  
        readLapisCorpses(lines);  
        detectedArea = AreaDetector.detect(lines);  
    
        // Area-based real-leave detection (now that `lines` and `detectedArea` exist).  
        AreaDetector.Area area = detectedArea.orElse(AreaDetector.Area.UNKNOWN);  
        boolean inMining = area == AreaDetector.Area.MINESHAFTS  
                        || area == AreaDetector.Area.DWARVEN_MINES;  
        if (!inMining) {  
            // Warp briefly reports UNKNOWN; require a few consecutive non-mining scans.  
            if (++leftMiningScans >= 3) {  
                double finalProfit = PROCS.totalProfit();  
                LOG.leave(finalProfit);  
                LOG.clear();  
                PROCS.resetAll();  
                DROPS.resetSession();  
                SHAFT_SPAWN.reset();
                currentLapisCorpses = -1;  
                currentUmberCorpses = -1;  
                currentTungstenCorpses = -1;  
                detectedShaft = Optional.empty();  
                missingShaftScans = 0;  
                leftMiningScans = 0;  
                mayhemBuff = MayhemBuff.NONE;
            }  
            return;   // don't run shaft-detection logic when not mining  
        } else {  
            leftMiningScans = 0;  
        }  
    
        Optional<ShaftDetector.Shaft> shaft = ShaftDetector.detect(lines);
        
        // Check if shaft status changed (entered, left, or switched)
        if (shaft.isPresent() && !detectedShaft.isPresent()) {
            // Entering a shaft
            missingShaftScans = 0;
            double initialProfit = PROCS.totalProfit();
            LOG.enter(shaft.get(), currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses, System.currentTimeMillis(), initialProfit);
            double measuredSpawnMinutes = SHAFT_SPAWN.onShaftEnter(PROCS.totalProfit());
            detectedShaft = shaft;
        } else if (shaft.isPresent() && detectedShaft.isPresent() && !shaft.get().code().equals(detectedShaft.get().code())) {
            // Switching shafts
            missingShaftScans = 0;
            double finalProfit = PROCS.totalProfit();
            LOG.leave(finalProfit);
            SHAFT_SPAWN.onShaftExit(PROCS.totalProfit());
            double measuredSpawnMinutes = SHAFT_SPAWN.onShaftEnter(PROCS.totalProfit());
            double initialProfit = PROCS.totalProfit();
            LOG.enter(shaft.get(), currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses, System.currentTimeMillis(), initialProfit);
            detectedShaft = shaft;
        } else if (!shaft.isPresent() && detectedShaft.isPresent()) {
            // Leaving a shaft
            if (++missingShaftScans >= 2) {
                double finalProfit = PROCS.totalProfit();
                LOG.leave(finalProfit);
                SHAFT_SPAWN.onShaftExit(PROCS.totalProfit());
                detectedShaft = Optional.empty();
                missingShaftScans = 0;
            }
        }

        if (detectedShaft.isPresent()
            && (currentLapisCorpses >= 0 || currentUmberCorpses >= 0 || currentTungstenCorpses >= 0)) {
            LOG.updateCurrentCorpses(currentLapisCorpses, currentUmberCorpses, currentTungstenCorpses);
        }

        applyMineshaftGroupToggle();

        if (config.trackerEnabled && config.miningSpeed > 0) {
            refreshPrices();
            refreshHudLines();
        }
    }

    public static Optional<ShaftDetector.Shaft> currentShaft() {
        return detectedShaft;
    }

    public static Optional<ShaftLog.Entry> currentShaftEntry() {
        if (detectedShaft.isEmpty()) return Optional.empty();
        List<ShaftLog.Entry> entries = LOG.entries();
        if (entries.isEmpty()) return Optional.empty();
        ShaftLog.Entry entry = entries.get(entries.size() - 1);
        return entry.code().equals(detectedShaft.get().code()) ? Optional.of(entry) : Optional.empty();
    }

    public static MiningFiesta.State miningFiesta() {
        return miningFiesta;
    }

    public static boolean fiestaActive() {
        return miningFiesta.active();
    }

    public static Optional<AreaDetector.Area> currentArea() {
        return detectedArea;
    }

    public static int currentShaftLapisCorpses() {
        return currentLapisCorpses;
    }

    private static List<String> collectLines(Minecraft client, ClientPacketListener connection) {  
        List<String> lines = new ArrayList<>();  

        PlayerTabOverlay tabList = client.gui == null ? null : client.gui.getTabList();
        if (tabList != null) {
            for (String fieldName : List.of("header", "footer")) {
                try {
                    Field field = PlayerTabOverlay.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(tabList);
                    if (value instanceof Component component) lines.add(component.getString());
                } catch (ReflectiveOperationException ignored) {
                    // Header/footer are version-dependent; scoreboard lines still work.
                }
            }
        }
    
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

    private static void readStats(Minecraft client, List<String> lines) {
        // Only update mining stats while actually holding a mining tool.
        if (client.player == null || !isMiningTool(client.player.getMainHandItem())) {
            return;
        }

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
        if (stats.cold() != null && stats.cold() != config.coldRes) {
            config.coldRes = Math.clamp(stats.cold(), 0.0, Cold.MAX_COLD_RESISTANCE);
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
    private static int missingShaftScans;

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
        Map<String, Double> current = prices;

        // --- Profit box ---
        List<Component> profit = new ArrayList<>();

        if (config.profitEnabled && config.miningSpeed > 0 && current != null) {
            profit.addAll(trackerLine(current));
        }
        profitLines = profit;

        // --- Tracker box ---
        List<Component> tracker = new ArrayList<>();

        if (config.trackerEnabled) {
            if (config.miningSpeed <= 0) {
                tracker.add(header("Shaft Helper"));
                tracker.add(gray("Waiting for stats (tab list Stats widget)"));
            } else if (current == null) {
                tracker.add(header("Shaft Helper (fetching prices...)"));
            } else {
                tracker.add(header("Shaft Helper"));

                if (currentShaft().isPresent()
                    && currentShaftLapisCorpses() >= 0
                    && currentShaft().get().gem().isGemstone()) {

                    ShaftDetector.Shaft shaft = currentShaft().get();
                    int lapisCorpses = currentShaftLapisCorpses();

                    double profitWithCorpses =
                        calculateCurrentShaftProfit(
                            shaft.gem(),
                            lapisCorpses,
                            current
                        );

                    double spawnCoinsPerHour =
                        SHAFT_SPAWN.hasSpawnTime()
                            ? SHAFT_SPAWN.getAverageSpawnCoinsPerHour()
                            : 0.0;

                    double spawnMinutes =
                        SHAFT_SPAWN.hasSpawnTime()
                            ? SHAFT_SPAWN.getAverageSpawnMinutes()
                            : MiningCalc.SHAFT_SPAWN_MINUTES;

                    MiningCalc.Strategy strategy =
                        MiningCalc.calculate(
                            current,
                            effectiveMiningSpeed(),
                            effectiveMiningFortune(),
                            config.gemstoneFortune,
                            effectiveGemstoneSpread(),
                            config.pristine,
                            effectiveColdResistance(),
                            config.efficiency,
                            spawnCoinsPerHour,
                            spawnMinutes
                        );

                    boolean shouldMine =
                        strategy.shouldMine(shaft.gem(), lapisCorpses);

                    Component decision =
                        shouldMine
                            ? Component.literal(" MINE").withStyle(ChatFormatting.GREEN)
                            : Component.literal(" SKIP").withStyle(ChatFormatting.RED);

                    tracker.add(
                        gemColored(
                            shaft.gem(),
                            "Current " + shaft.gem().name()
                            + " shaft (" + lapisCorpses + " lapis)"
                        )
                        .append(
                            gray(": " + Format.compact(profitWithCorpses) + "/hr")
                        )
                        .append(decision)
                    );
                }
                tracker.addAll(overviewLines(current));
            }
        }
        trackerLines = tracker;

        // --- Log box: this session's shafts ---
        List<Component> log = new ArrayList<>();
        if (config.logEnabled) {
            List<ShaftLog.Entry> entries = LOG.entries();

            if (!LOG.isEmpty()) {
                log.add(gray("This session:"));

                for (ShaftLog.Entry entry : entries) {
                    String shaftLabel = entry.number() + " " + entry.code();
                    String corpseLabel = "";

                    if (entry.lapisCorpses() >= 0)
                        corpseLabel += entry.lapisCorpses() + "l";

                    if (entry.umberCorpses() >= 0)
                        corpseLabel += entry.umberCorpses() + "u";

                    if (entry.tungstenCorpses() >= 0)
                        corpseLabel += entry.tungstenCorpses() + "t";

                    if (!corpseLabel.isEmpty())
                        shaftLabel += " " + corpseLabel;

                    log.add(gemColored(entry.gem(), shaftLabel));

                    if (entry.number() == entries.size()
                        && currentShaft().isPresent()
                        && currentShaft().get().code().equals(entry.code())) {

                        double shaftProfit =
                            PROCS.totalProfit() - entry.initialProfit();

                        if (shaftProfit > 0)
                            log.add(gray(
                                "  Total: " + Format.compact(shaftProfit)
                            ));

                    } else {
                        double shaftProfit =
                            entry.finalProfit() - entry.initialProfit();

                        if (shaftProfit > 0)
                            log.add(gray(
                                "  Total: " + Format.compact(shaftProfit)
                            ));
                    }
                }
            }
        }
        logLines = log;
    }

    private static List<Component> overviewLines(Map<String, Double> current) {
        double spawnCoinsPerHour =
            SHAFT_SPAWN.hasSpawnTime()
                ? SHAFT_SPAWN.getAverageSpawnCoinsPerHour()
                : 0.0;

        double spawnMinutes =
            SHAFT_SPAWN.hasSpawnTime()
                ? SHAFT_SPAWN.getAverageSpawnMinutes()
                : MiningCalc.SHAFT_SPAWN_MINUTES;

        MiningCalc.Strategy strategy =
            MiningCalc.calculate(
                current,
                effectiveMiningSpeed(),
                effectiveMiningFortune(),
                config.gemstoneFortune,
                effectiveGemstoneSpread(),
                config.pristine,
                effectiveColdResistance(),
                config.efficiency,
                spawnCoinsPerHour,
                spawnMinutes
            );

        List<Component> lines = new ArrayList<>();

        String spawnRateText =
            String.format(
                "Spawn: %.1fM/hr",
                strategy.spawnCoinsPerHour() / 1_000_000.0
            );

        String spawnTimeText =
            String.format(
                "Spawn Time: %.2f min",
                strategy.spawnMinutes()
            );

        String optimalText =
            String.format(
                "Optimal: %.1fM/hr",
                strategy.coinsPerHour() / 1_000_000.0
            );

        String thresholdText =
            String.format(
                "Threshold: %.1fM/hr",
                strategy.threshold() / 1_000_000.0
            );

        lines.add(gray(spawnRateText));
        lines.add(gray(spawnTimeText));
        lines.add(gray(optimalText));
        lines.add(gray(thresholdText));

         // Find the lowest accepted lapis count for each gemstone.
        java.util.Map<Gemstone, Integer> lowestAccepted = new java.util.LinkedHashMap<>();

        for (MiningCalc.ShaftState state : strategy.acceptedStates()) {
            lowestAccepted.merge(
                state.gem(),
                state.lapis(),
                Math::min
            );
        }

        // Display accepted gemstones, lowest accepted lapis -> 4L.
        int shown = 0;
        for (java.util.Map.Entry<Gemstone, Integer> entry : lowestAccepted.entrySet()) {
            if (shown >= 12) break;

            Gemstone gem = entry.getKey();
            int lowestLapis = entry.getValue();

            lines.add(gemColored(
                gem,
                "MINE " + gem.name() + " " + lowestLapis + "-4L"
            )
            );
            shown++;
        }
        return lines;
    }

    /** Maps a tracked drop/sack item display name to its Bazaar product id. */  
    private static String idFor(String name) {  
        String key = name.toUpperCase(Locale.ROOT)
            .trim()  
            .replace("'", "")  
            .replaceAll("[^A-Z0-9]+", "_")  
            .replaceAll("^_+|_+$", "");  
        return switch (key) {  
            case "GLACITE" -> "GLACITE";  
            case "ENCHANTED_GLACITE" -> "ENCHANTED_GLACITE";  
            case "HARD_STONE", "HARDSTONE" -> "HARD_STONE";  
            case "REFINED_MINERAL"  -> "REFINED_MINERAL";  
            case "GLOSSY_GEMSTONE"  -> "GLOSSY_GEMSTONE";  
            case "MITHRIL", "MITHRIL_ORE" -> "MITHRIL_ORE";  
            case "TITANIUM", "TITANIUM_ORE" -> "TITANIUM_ORE";  
            default -> key;  
        };
    }

    private static boolean isMiningTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String name = stack.getHoverName().getString().trim();

        return name.endsWith("Mithril Drill SX-R226")
            || name.endsWith("Mithril Drill SX-R326")
            || name.endsWith("Titanium Drill DR-X355")
            || name.endsWith("Titanium Drill DR-X455")
            || name.endsWith("Titanium Drill DR-X555")
            || name.endsWith("Titanium Drill DR-X655")
            || name.endsWith("Divan's Drill")
            || name.endsWith("Ruby Drill TX-15")
            || name.endsWith("Gemstone Drill LT-522")
            || name.endsWith("Topaz Drill KGR-12")
            || name.endsWith("Jasper Drill X")
            || name.endsWith("Rookie Pickaxe")
            || name.endsWith("Promising Pickaxe")
            || name.endsWith("Zombie Pickaxe")
            || name.endsWith("Lapis Pickaxe")
            || name.endsWith("Fractured Mithril Pickaxe")
            || name.endsWith("Bandaged Mithril Pickaxe")
            || name.endsWith("Mithril Pickaxe")
            || name.endsWith("Jungle Pickaxe")
            || name.endsWith("Refined Mithril Pickaxe")
            || name.endsWith("Rusty Titanium Pickaxe")
            || name.endsWith("Titanium Pickaxe")
            || name.endsWith("Polished Titanium Pickaxe")
            || name.endsWith("Stonk")
            || name.endsWith("Pickonimbus 2000")
            || name.endsWith("Bingonimbus 2000")
            || name.endsWith("Gemstone Gauntlet");
    }

    /** Coins/hr the pristine procs actually earned, to hold against the theoretical ranking. */  
    private static List<Component> trackerLine(Map<String, Double> current) {  
        List<Component> lines = new ArrayList<>();  
        // Uses PROC.estimate currently, thats BAD. change to spawn-phase profit estimate instead.
        PROCS.estimate(current, config.pristine, System.currentTimeMillis())  
            .ifPresent(estimate -> {  
                lines.add(Component.literal("Tracker: " + estimate.procs() + " proc"  
                        + (estimate.procs() == 1 ? "" : "s") + " in " + minutes(estimate.elapsedMs()) + " — ")  
                    .withStyle(ChatFormatting.GRAY)  
                    .append(Component.literal("~" + Format.compact(estimate.coinsPerHour()) + "/hr")  
                        .withStyle(ChatFormatting.GOLD)));  
            });  

        // Combine every coin source: pristine procs + sacks + frozen corpse loot.
        Map<String, Double> breakdown =
            new LinkedHashMap<>(
                PROCS.profitBreakdown(current, config.pristine)
            );

        // Sack loot
        for (Map.Entry<String, Long> entry : DROPS.sacks().entrySet()) {
            double value =
                entry.getValue()
                * current.getOrDefault(
                    DropTracker.idFor(entry.getKey()),
                    0.0
                );

            if (value > 0) {
                breakdown.merge(entry.getKey(), value, Double::sum);
            }
        }

        // Frozen corpse loot
        for (Map.Entry<String, Long> entry : DROPS.corpseDrops().entrySet()) {
            double value =
                entry.getValue()
                * current.getOrDefault(
                    DropTracker.idFor(entry.getKey()),
                    0.0
                );

            if (value > 0) {
                breakdown.merge(entry.getKey(), value, Double::sum);
            }
        } 
  
        double total = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();  
        if (total > 0) {  
            lines.add(Component.literal("Total Profit: " + Format.compact(total))  
                .withStyle(ChatFormatting.GREEN));  
            breakdown.entrySet().stream()  
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))  
                .limit(5)  
                .forEach(e -> lines.add(gray("  " + e.getKey() + ": " + Format.compact(e.getValue()))));  
        }  
  
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
        List<Mining.Breakdown> breakdown = Mining.calculateBreakdown(effectiveMiningSpeed(), effectiveMiningFortune(), config.gemstoneFortune, effectiveGemstoneSpread());
        Mining.Breakdown shaftBreakdown = breakdown.stream()
            .filter(b -> b.gem().name().equals(gem.name()))
            .findFirst()
            .orElseThrow();
        double effectivePristine = config.pristine + lapisCorpses;
        return Mining.coinsPerHour(shaftBreakdown, prices, effectivePristine);
    }

    public static int miningSpeedBonus() {
        if (!inMineshaft()) return 0;
        int bonus = config.eagerAdventurer ? 4000 : 0;
        if (config.mineshaftMayhem && mayhemBuff == MayhemBuff.MINING_SPEED) bonus += 200;
        return bonus;
    }

    public static int miningFortuneBonus() {
        if (!inMineshaft()) return 0;
        int bonus = config.ragsToRiches ? 2000 : 0;
        if (config.mineshaftMayhem && mayhemBuff == MayhemBuff.MINING_FORTUNE) bonus += 100;
        return bonus;
    }

    public static int gemstoneSpreadBonus() {
        return inMineshaft() && config.steadyHand ? 10 : 0;
    }

    public static double coldResistanceBonus() {
        return inMineshaft() && config.mineshaftMayhem && mayhemBuff == MayhemBuff.COLD_RESISTANCE ? 10 : 0;
    }

    private static boolean inMineshaft() {
        return detectedArea.orElse(AreaDetector.Area.UNKNOWN) == AreaDetector.Area.MINESHAFTS;
    }

    private static int effectiveMiningSpeed() {
        return config.miningSpeed + miningSpeedBonus();
    }

    private static int effectiveMiningFortune() {
        int fortune = config.miningFortune + miningFortuneBonus();
        return fiestaActive() ? fortune * 2 : fortune;
    }

    private static int effectiveGemstoneSpread() {
        return config.gemstoneSpread + gemstoneSpreadBonus();
    }

    private static double effectiveColdResistance() {
        return config.coldRes + coldResistanceBonus();
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
