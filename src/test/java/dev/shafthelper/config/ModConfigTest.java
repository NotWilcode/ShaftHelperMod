package dev.shafthelper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shafthelper.core.Cold;
import dev.shafthelper.core.Prices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultsMatchTheCommandDefaults() {
        ModConfig config = new ModConfig();
        assertEquals(0, config.miningSpeed);
        assertEquals(Cold.DEFAULT_EFFICIENCY, config.efficiency);
        assertEquals("Jasper", config.benchmark);
        assertTrue(config.autoStats);
        assertTrue(config.trackerEnabled);
        assertTrue(config.profitEnabled);
        assertTrue(config.logEnabled);
        assertEquals(Prices.DEFAULT_MODE, config.priceMode());
        assertEquals(Prices.DEFAULT_DATA, config.priceDataMode());
        assertEquals(Prices.DEFAULT_BASIS, config.priceBasisMode());
    }

    @Test
    void savesAndLoadsRoundTrip() {
        Path path = dir.resolve("shafthelper.json");
        ModConfig config = new ModConfig();
        config.miningSpeed = 1500;
        config.miningFortune = 400;
        config.pristine = 3.5;
        config.benchmark = "Sapphire";
        config.prices = "insta_sell";
        config.priceData = "live";
        config.priceBasis = "listed";
        config.autoStats = false;
        config.trackerEnabled = false;
        config.trackerX = 75;
        config.trackerY = 60;
        config.save(path);

        ModConfig loaded = ModConfig.load(path);
        assertEquals(1500, loaded.miningSpeed);
        assertEquals(400, loaded.miningFortune);
        assertEquals(3.5, loaded.pristine);
        assertEquals("Sapphire", loaded.benchmark);
        assertEquals(Prices.Mode.INSTA_SELL, loaded.priceMode());
        assertEquals(Prices.Data.LIVE, loaded.priceDataMode());
        assertEquals(Prices.Basis.LISTED, loaded.priceBasisMode());
        assertFalse(loaded.autoStats);
        assertFalse(loaded.trackerEnabled);
        assertEquals(75, loaded.trackerX);
        assertEquals(60, loaded.trackerY);
    }

    @Test
    void missingFileGivesDefaults() {
        ModConfig config = ModConfig.load(dir.resolve("missing.json"));
        assertEquals(0, config.miningSpeed);
        assertTrue(config.trackerEnabled);
    }

    @Test
    void corruptFileGivesDefaults() throws IOException {
        Path path = dir.resolve("shafthelper.json");
        Files.writeString(path, "not json{{{");
        ModConfig config = ModConfig.load(path);
        assertEquals(0, config.miningSpeed);
        assertTrue(config.autoStats);
    }
}
