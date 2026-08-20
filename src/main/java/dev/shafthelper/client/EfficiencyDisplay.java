package dev.shafthelper.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import dev.shafthelper.config.ModConfig;

/**
 * Displays mining efficiency information similar to PingOffsetMiner.
 * Shows uptime and efficiency percentage when mining.
 */
public final class EfficiencyDisplay implements HudElement {

    private static boolean initialized = false;
    
    // Tracking data
    private static long timeStarted = 0;
    private static int blocksMined = 0;
    private static int expectedBlocks = 0;
    private static long lastMineTime = 0;
    private static final int TIMEOUT_SECONDS = 30;

    public static void register() {
        if (!initialized) {
            initialized = true;
        }
    }
    
    public static void onBlockMined() {
        long currentTime = System.currentTimeMillis();
        if (!isMining()) {
            timeStarted = currentTime;
            blocksMined = 0;
            expectedBlocks = 0;
        }
        lastMineTime = currentTime;
        blocksMined++;
    }
    
    public static void onBlockExpected() {
        if (isMining()) {
            expectedBlocks++;
        }
    }
    
    private static boolean isMining() {
        long currentTime = System.currentTimeMillis();
        return currentTime < (lastMineTime + TIMEOUT_SECONDS * 1000L);
    }
    
    private static float getUptime() {
        if (!isMining()) return 0;
        float difference = System.currentTimeMillis() - timeStarted;
        return difference / 1000f;
    }
    
    private static int getEfficiency() {
        if (expectedBlocks == 0) return 100;
        float eff = (float) blocksMined / expectedBlocks;
        return Math.round(eff * 100f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        
        // Only show if actively mining
        if (!isMining()) return;
        
        Font font = client.font;
        
        ModConfig config = ShaftTracker.config();
        
        // Position based on config
        int x = position(config.effX, graphics.guiWidth(), 150);
        int y = position(config.effY, graphics.guiHeight(), 40);
        
        float uptime = getUptime();
        int efficiency = getEfficiency();
        
        // Draw uptime
        String uptimeText = String.format("Uptime: %.1fs", uptime);
        graphics.text(font, Component.literal(uptimeText), x, y, 0xFF55FFFF, true);
        
        // Draw efficiency
        String effText = String.format("Efficiency: %d%%", efficiency);
        graphics.text(font, Component.literal(effText), x, y + 10, 0xFF55FFFF, true);
    }

    static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 8);
        return 4 + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}