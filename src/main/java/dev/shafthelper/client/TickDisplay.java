package dev.shafthelper.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import dev.shafthelper.config.ModConfig;

/**
 * Displays tick timing information similar to PingOffsetMiner.
 * Shows "elapsed/needed" ticks with color coding for optimal mining timing.
 */
public final class TickDisplay implements HudElement {

    private static boolean initialized = false;
    
    // Colors matching POM style
    private static final int PRE_MINED_COLOR = 0xFFFF0000; // Red - still mining
    private static final int POST_MINED_COLOR = 0xFF00FF00; // Green - ready to move

    public static void register() {
        if (!initialized) {
            initialized = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        
        // Get timing data from MiningCalculator
        int ticksElapsed = MiningCalculator.getTicksElapsed();
        double ticksNeeded = MiningCalculator.getTicksNeeded();
        boolean timeoutExceeded = MiningCalculator.isTimeoutExceeded();
        
        // Only show if we're actively mining
        if (ticksNeeded <= 0) return;
        
        Font font = client.font;
        
        ModConfig config = ShaftTracker.config();
        int boxW = Math.max(1, (int) Math.round(100 * config.tickScale));
        int boxH = Math.max(1, (int) Math.round(20 * config.tickScale));
        
        // Position based on config
        int x = position(config.tickX, graphics.guiWidth(), boxW);
        int y = position(config.tickY, graphics.guiHeight(), boxH);
        
        // Format tick string
        String tickString = String.format("%d/%.0f", ticksElapsed, ticksNeeded);
        int color = timeoutExceeded ? POST_MINED_COLOR : PRE_MINED_COLOR;
        int inset = Math.max(1, (int) Math.round(2 * config.tickScale));
        
        // Draw tick display with shadow
        graphics.text(font, Component.literal(tickString), x + inset, y + inset, color, true);
    }

    static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 8);
        return 4 + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}