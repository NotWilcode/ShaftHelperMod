package dev.shafthelper.client;

import java.util.List;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The /shaft overview as an overlay: each gemstone with the lapis corpses it needs to beat the
 * benchmark (or "skip"), plus the pristine proc tracker and the shafts visited this session.
 * Position comes from the config's HUD X/Y sliders (percent of the screen).
 */
public final class ShaftHud implements HudElement {

    private static final int LINE_HEIGHT = 10;
    private static final int EDGE = 4;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        List<Component> lines = ShaftTracker.trackerLines();
        if (lines.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        Font font = client.font;

        ModConfig config = ShaftTracker.config();
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int scaledWidth = Math.max(1, (int) Math.round(width * config.trackerScale));
        int scaledHeight = Math.max(1, (int) Math.round(lines.size() * LINE_HEIGHT * config.trackerScale));
        //Background box
        int x = position(config.trackerX, graphics.guiWidth(), scaledWidth);
        int top = position(config.trackerY, graphics.guiHeight(), scaledHeight);

        int bg = config.themeBg;
        int border = config.themeBorder;
        int text = config.themeText;
        int l = x - 2, t = top - 2, r = x + scaledWidth + 2, b = top + scaledHeight + 2;  
        graphics.fill(l, t, r, b, bg);  // background
        graphics.fill(l, t, r, t + 1, border);  // top edge
        graphics.fill(l, b - 1, r, b, border);  // bottom edge
        graphics.fill(l, t, l + 1, b, border);  // left edge
        graphics.fill(r - 1, t, r, b, border);  // right edge

        int lineStep = Math.max(1, (int) Math.round(LINE_HEIGHT * config.trackerScale));
        int textX = x;
        int y = top;
        for (Component line : lines) {
            graphics.text(font, line, textX, y, text, true);
            y += lineStep;
        }
    }

    /** Maps the slider percent onto the space the box can occupy without leaving the screen. */
    private static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 2 * EDGE);
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}
