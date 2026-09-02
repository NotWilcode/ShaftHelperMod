package dev.shafthelper.client;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Tracks and displays client TPS (Ticks Per Second) and Ping.
 * Integrated with POM-based Custom Payload packet latency loops.
 */
public final class NetworkTracker implements HudElement {

    private static boolean initialized = false;
            
    static final int BOX_W = 110;
    static final int BOX_H = 30;
    static final int EDGE = 4;

    public static void register() {
        if (!initialized) {
            ServerStats.init();
            initialized = true;
        }
    }

    public static float getCurrentTPS() {
        return (float) ServerStats.getTps();
    }

    public static int getCurrentPing() {
        return (int) ServerStats.getPing();
    }

    public static void setPing(int ping) {
        ServerStats.addPing(ping);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        ModConfig config = ShaftTracker.config();
        if (!config.enableNetwork) return; 
        
        Font font = client.font;

        // Ensure you match the exact instance accessor pattern your project config uses
        int boxW = Math.max(1, (int) Math.round(BOX_W * config.netScale));
        int boxH = Math.max(1, (int) Math.round(BOX_H * config.netScale));
        int x = position(config.netX, graphics.guiWidth(), boxW);
        int y = position(config.netY, graphics.guiHeight(), boxH);
        
        int bg = config.themeBg;
        int border = config.themeBorder;
        int text = config.themeText;
        int accent = config.themeAccent;
        int inset = Math.max(1, (int) Math.round(5 * config.netScale));
        int secondLineY = y + Math.max(1, (int) Math.round(15 * config.netScale));

        // Draw background
        graphics.fill(x, y, x + boxW, y + boxH, bg);
        graphics.fill(x, y, x + boxW, y + 1, border);
        graphics.fill(x, y + boxH - 1, x + boxW, y + boxH, border);
        graphics.fill(x, y, x + 1, y + boxH, border);
        graphics.fill(x + boxW - 1, y, x + boxW, y + boxH, border);
        
        // Draw TPS
        float currentTPS = getCurrentTPS();
        String tpsText = String.format("TPS: %.1f", currentTPS);
        int tpsColor = currentTPS >= 18.0f ? accent : (currentTPS >= 15.0f ? 0xFFFFFF00 : 0xFFFF6347);
        graphics.text(font, Component.literal(tpsText), x + inset, y + inset, tpsColor, true);
        
        // Draw Ping
        int currentPing = getCurrentPing();
        String pingText;
        int pingColor;
        if (currentPing == 0) {
            pingText = "Mine a block";
            pingColor = 0xFFAAAAAA; // White for N/A
        } else {
            pingText = String.format("Ping: %dms", currentPing);
            pingColor = currentPing <= 100 ? 0xFF90EE90 : (currentPing <= 200 ? 0xFFFFFF00 : 0xFFFF6347);
        }
        graphics.text(font, Component.literal(pingText), x + inset, secondLineY, pingColor, true);
    }

    static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 2 * EDGE);
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}