package dev.shafthelper.client;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Displays mining efficiency information similar to PingOffsetMiner.
 * Shows uptime and efficiency percentage when mining.
 */
public final class EfficiencyDisplay implements HudElement {

    private static boolean initialized = false;
    
    // Tracking data
    private static final int TIMEOUT_SECONDS = 30;
    private static long timeStarted = 0;
    private static long lastMineTime = 0;
    private static float lastUptime = Float.NaN;  
    private static int lastEfficiency = Integer.MIN_VALUE;  
    private static int lastPingEff = Integer.MIN_VALUE;
    private static int blocksMined = 0;
    private static int expectedBlocks = 0;  
    private static double idealElapsedMs = 0;
    private static double lastMsPerTick = Double.NaN;  
    private static Component uptimeComp, effComp, pingEffComp, msPerTickComp;

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
            idealElapsedMs = 0;  
        }  
        lastMineTime = currentTime;  
        blocksMined++;  
        idealElapsedMs += MiningCalculator.computeIdealBreakMs(MiningCalculator.getEstimatedTicks());
    }
    
    public static void onBlockExpected() {  
        long now = System.currentTimeMillis();  
        if (!isMining()) {  
            timeStarted = now;  
            blocksMined = 0;  
            expectedBlocks = 0;  
        }  
        lastMineTime = now;   // keep the session alive while looking at gemstones  
        expectedBlocks++;  
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
        if (blocksMined == 0) return 100;  
        double actualMs = Math.max(System.currentTimeMillis() - timeStarted, 1);  
        return Math.clamp((int) Math.round(idealElapsedMs / actualMs * 100.0), 0, 100);  
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        
        // Only show if actively mining
        if (!isMining()) return;
        
        Font font = client.font;
        
        ModConfig config = ShaftTracker.config();
        if (!config.efficiencyEnabled) return;
        int boxW = Math.max(1, (int) Math.round(150 * config.effScale));
        int boxH = Math.max(1, (int) Math.round(50 * config.effScale));
        
        // Position based on config
        int x = position(config.effX, graphics.guiWidth(), boxW);
        int y = position(config.effY, graphics.guiHeight(), boxH);
        int inset = Math.max(1, (int) Math.round(2 * config.effScale));
        int lineStep = Math.max(1, (int) Math.round(10 * config.effScale));

        int text = ShaftTracker.config().themeText;
        
        float uptime = getUptime();
        int efficiency = getEfficiency();
        int pingEff = MiningCalculator.getPingEfficiency();
        double getMsPerTick = ServerStats.getMsPerTick();
        
        // Draw uptime
        if (uptime != lastUptime || uptimeComp == null) {
            uptimeComp = Component.literal(String.format("Uptime: %.1fs", uptime));
            lastUptime = uptime;
        }
        if (efficiency != lastEfficiency || effComp == null) {
            effComp = Component.literal(String.format("Efficiency: %d%%", efficiency));
            lastEfficiency = efficiency;
        }
        if (pingEff != lastPingEff || pingEffComp == null) {
            pingEffComp = Component.literal(String.format("Ping Eff: %d%%", pingEff));
            lastPingEff = pingEff;
        }
        if (getMsPerTick != lastMsPerTick || msPerTickComp == null) {
            msPerTickComp = Component.literal(String.format("ms/tick: %.1f", getMsPerTick));
            lastMsPerTick = getMsPerTick;
        }

        graphics.text(font, uptimeComp, x + inset, y + inset, text, true);
        graphics.text(font, effComp, x + inset, y + lineStep, text, true);
        graphics.text(font, pingEffComp, x + inset, y + lineStep * 2, text, true);
        graphics.text(font, msPerTickComp, x + inset, y + lineStep * 3, text, true);
    }

    static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 8);
        return 4 + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}