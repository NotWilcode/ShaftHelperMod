package dev.shafthelper.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public boolean goblinOmelette = true;
    public boolean autoStats = true;
    public boolean trackerEnabled = true;
    public boolean logEnabled = true;
    public boolean profitEnabled = true;
    public boolean enableDustParticles = true;
    public boolean enableDebugOverlay = false;
    public boolean presetsAutoImported = false;
    
    /** HUD position as a percent of the screen: 0 = left/top edge, 100 = right/bottom edge. */
    public double trackerX = 0;  
    public double trackerY = 0;  
    public double netX = 100;  
    public double netY = 5;  
    public double logX = 0;  
    public double logY = 30;  
    public double profitX = 0;  
    public double profitY = 50;  
    public double calcX = 100;  
    public double calcY = 30;  
    public double tickX = 100;  
    public double tickY = 30;  
    public double effX = 100;  
    public double effY = 30;

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
                    if (config.waypoints == null) {
                        config.waypoints = new ArrayList<>();
                    }
                    return config;
                }
            } catch (IOException | JsonSyntaxException error) {
                System.err.println("Could not read " + path + ", using defaults: " + error);
            }
        }
        return new ModConfig();
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
