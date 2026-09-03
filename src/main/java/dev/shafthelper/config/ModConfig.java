package dev.shafthelper.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import dev.shafthelper.core.Cold;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.ShaftOptions;
import dev.shafthelper.core.Waypoint;

/**
 * Mod settings saved to config/shafthelper.json so they survive leaving and rejoining Minecraft.
 * Stats can be filled in automatically from Hypixel's tab list (autoStats) or set by hand in the
 * config screen; enums are stored as the same strings the /shaft command accepts.
 */
public final class ModConfig {
    public static final String THEME_MIDNIGHT = "midnight";
    public static final String THEME_SUNSET = "sunset";
    public static final String THEME_AURORA = "aurora";
    public static final String THEME_FOREST = "forest";

    public String guiTheme = THEME_MIDNIGHT;
    public int themeBg = 0xE00D1B2A;
    public int themeBorder = 0xFF1B263B;
    public int themeAccent = 0xFF778DA9;
    public int themeText = 0xFFE0E1DD;
    public int themeTextOff = 0xFF6B7280;

    public int miningSpeed = 0;
    public int miningFortune = 0;
    public int gemstoneFortune = 0;
    public int gemstoneSpread = 0;
    public int proffesionalLevel = 0;
    
    public double pristine = 0;
    public double efficiency = Cold.DEFAULT_EFFICIENCY;
    public double coldRes = 0;

    public String benchmark = ShaftOptions.DEFAULT_BENCHMARK;
    public String prices = "sell_offer";
    public String priceData = "average";
    public String priceBasis = "flawless";

    public boolean goblinOmelette = false;
    public boolean autoStats = true;
    public boolean trackerEnabled = true;
    public boolean logEnabled = true;
    public boolean profitEnabled = true;
    public boolean enableDustParticles = true;
    public boolean enableDebugOverlay = false;
    public boolean enableNetwork = true;
    public boolean miningBuffTimerEnabled = true;
    public boolean miningDeployableTimerEnabled = true;
    public boolean presetsAutoImported = false;
    public boolean orderedWaypointsEnabled = true;
    public boolean corpseWaypointsEnabled = true;
    public boolean corpseFinderEnabled = true;

    public double orderedDistance = 2.0;
    public double orderedChunks = 64;
    
    /** HUD position as a percent of the screen: 0 = left/top edge, 100 = right/bottom edge. */
    public double trackerX = 0;  
    public double trackerY = 22.0;  
    public double trackerScale = 1.0;
    public double netX = 0;  
    public double netY = 13.0;  
    public double netScale = 1.0;
    public double logX = 0.0;  
    public double logY = 50.0;  
    public double logScale = 1.0;
    public double profitX = 0.0;  
    public double profitY = 40.0;  
    public double profitScale = 1.0;
    public double calcX = 53.0;  
    public double calcY = 28.0;  
    public double calcScale = 1.0;
    public double tickX = 53.0;  
    public double tickY = 49.0;  
    public double tickScale = 1.0;
    public double effX = 56.0;  
    public double effY = 48.0;
    public double effScale = 1.0;
    public double playerTimersX = 4.0;
    public double playerTimersY = 72.0;
    public double playerTimersScale = 1.0;

    /** Sound settings */
    public boolean pingSoundAlert = true;

    /** List of waypoints for rendering in-game. */
    public List<Waypoint> waypoints = new ArrayList<>();
    
    /** Network settings for mining calculator */
    public int configuredPing = 50; // Default ping in ms

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ModConfig load(Path path) {
        if (Files.exists(path)) {
            try {
                ModConfig config = GSON.fromJson(Files.readString(path), ModConfig.class);
                if (config != null) {
                    config.ensureThemeDefaults();
                    if (config.waypoints == null) {
                        config.waypoints = new ArrayList<>();
                    }
                    return config;
                }
            } catch (IOException | JsonSyntaxException error) {
                System.err.println("Could not read " + path + ", using defaults: " + error);
            }
        }
        ModConfig config = new ModConfig();
        config.ensureThemeDefaults();
        return config;
    }

    public void ensureThemeDefaults() {
        if (guiTheme == null || guiTheme.isBlank()) {
            guiTheme = THEME_MIDNIGHT;
        }

        if (themeBg == 0 && themeBorder == 0 && themeAccent == 0 && themeText == 0 && themeTextOff == 0) {
            applyTheme(guiTheme);
            return;
        }

        String normalized = guiTheme.toLowerCase(Locale.ROOT);
        if (Map.of(THEME_MIDNIGHT, 1, THEME_SUNSET, 1, THEME_FOREST, 1, THEME_AURORA, 1).containsKey(normalized)) {
            applyTheme(normalized);
        }
    }

    public void cycleTheme() {
        List<String> order = List.of(THEME_MIDNIGHT, THEME_SUNSET, THEME_FOREST, THEME_AURORA);
        int index = order.indexOf(guiTheme);
        if (index < 0) index = 0;
        applyTheme(order.get((index + 1) % order.size()));
    }

    public String nextThemeId() {
        List<String> order = List.of(
            THEME_MIDNIGHT,
            THEME_SUNSET,
            THEME_FOREST,
            THEME_AURORA
        );
        int index = order.indexOf(guiTheme);
        if (index < 0) index = 0;
        return order.get((index + 1) % order.size());
    }

    public void applyTheme(String themeId) {
        String normalized = themeId == null ? THEME_MIDNIGHT : themeId.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case THEME_SUNSET -> {
                guiTheme = THEME_SUNSET;
                themeBg = 0xE04A1F2A;
                themeBorder = 0xFF7A3C24;
                themeAccent = 0xFFF2A65A;
                themeText = 0xFFF9E6D3;
                themeTextOff = 0xFFB07D5B;
            }
            case THEME_FOREST -> {
                guiTheme = THEME_FOREST;
                themeBg = 0xE0132F22;
                themeBorder = 0xFF2F5F46;
                themeAccent = 0xFF8DD39E;
                themeText = 0xFFE8F7ED;
                themeTextOff = 0xFF7AA788;
            }
            case THEME_AURORA -> {
                guiTheme = THEME_AURORA;
                themeBg = 0xE0122338;
                themeBorder = 0xFF2E6F73;
                themeAccent = 0xFF67E8F9;
                themeText = 0xFFEAFBFF;
                themeTextOff = 0xFF7FB0B7;
            }
            default -> {
                guiTheme = THEME_MIDNIGHT;
                themeBg = 0xE00D1B2A;
                themeBorder = 0xFF1B263B;
                themeAccent = 0xFF778DA9;
                themeText = 0xFFE0E1DD;
                themeTextOff = 0xFF6B7280;
            }
        }
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException error) {
            System.err.println("Could not save " + path + ": " + error);
        }
    }

    public Prices.Mode priceMode() {
        return "insta_sell".equals(normalize(prices)) ? Prices.Mode.INSTA_SELL : Prices.Mode.SELL_OFFER;
    }

    public Prices.Data priceDataMode() {
        return "live".equals(normalize(priceData)) ? Prices.Data.LIVE : Prices.Data.AVERAGE;
    }

    public Prices.Basis priceBasisMode() {
        return "listed".equals(normalize(priceBasis)) ? Prices.Basis.LISTED : Prices.Basis.FLAWLESS;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
