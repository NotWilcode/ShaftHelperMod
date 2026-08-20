package dev.shafthelper.client;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

    private static final int UPDATE_INTERVAL = 20; // Update every second (20 ticks)
    private static int tickCounter = 0;
    private static boolean initialized = false;
            
    static final int BOX_W = 110;
    static final int BOX_H = 30;
    static final int EDGE = 4;

    public static void register() {
        if (!initialized) {
            ServerStats.init();
            ClientTickEvents.END_CLIENT_TICK.register(NetworkTracker::onTick);
            initialized = true;
        }
    }

    private static void onTick(Minecraft client) {
        tickCounter++;
    }

    public static float getCurrentTPS() {
        return (float) ServerStats.getTps();
    }

    public static int getCurrentPing() {
        return (int) ServerStats.getOneWayPing();
    }

    public static void setPing(int ping) {
        ServerStats.addPing(ping);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        
        Font font = client.font;

        // Ensure you match the exact instance accessor pattern your project config uses
        ModConfig config = ShaftTracker.config();
        int x = position(config.netX, graphics.guiWidth(), BOX_W);
        int y = position(config.netY, graphics.guiHeight(), BOX_H);
        
        // Draw background
        graphics.fill(x, y, x + BOX_W, y + BOX_H, 0xE00D1B2A);
        graphics.fill(x, y, x + BOX_W, y + 1, 0xFF1B263B);
        graphics.fill(x, y + BOX_H - 1, x + BOX_W, y + BOX_H, 0xFF1B263B);
        graphics.fill(x, y, x + 1, y + BOX_H, 0xFF1B263B);
        graphics.fill(x + BOX_W - 1, y, x + BOX_W, y + BOX_H, 0xFF1B263B);
        
        // Draw TPS
        float currentTPS = getCurrentTPS();
        String tpsText = String.format("TPS: %.1f", currentTPS);
        int tpsColor = currentTPS >= 18.0f ? 0xFF90EE90 : (currentTPS >= 15.0f ? 0xFFFFFF00 : 0xFFFF6347);
        graphics.text(font, Component.literal(tpsText), x + 5, y + 5, tpsColor, true);
        
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
        graphics.text(font, Component.literal(pingText), x + 5, y + 15, pingColor, true);
    }

    static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 2 * EDGE);
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}