package dev.shafthelper.client;  
  
import java.util.List;  
  
import dev.shafthelper.config.ModConfig;  
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;  
import net.minecraft.client.DeltaTracker;  
import net.minecraft.client.Minecraft;  
import net.minecraft.client.gui.Font;  
import net.minecraft.client.gui.GuiGraphicsExtractor;  
import net.minecraft.network.chat.Component;  
  
public final class ShaftLogHud implements HudElement {  
  
    private static final int LINE_HEIGHT = 10;  
    private static final int EDGE = 4;  
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {  
        List<Component> lines = ShaftTracker.logLines();  
        if (lines.isEmpty()) return;  
  
        Minecraft client = Minecraft.getInstance();  
        if (client.getDebugOverlay().showDebugScreen()) return;  
        Font font = client.font;  
  
        ModConfig config = ShaftTracker.config();  
        int width = 0;  
        for (Component line : lines) {  
            width = Math.max(width, font.width(line));  
        }  
        int scaledWidth = Math.max(1, (int) Math.round(width * config.logScale));  
        int scaledHeight = Math.max(1, (int) Math.round(lines.size() * LINE_HEIGHT * config.logScale));  
        int x = position(config.logX, graphics.guiWidth(), scaledWidth);  
        int top = topPosition(config.logY, graphics.guiHeight());  
  
        int bg = config.themeBg;
        int border = config.themeBorder;
        int text = config.themeText;
        int l = x - 2, t = top - 2, r = x + scaledWidth + 2, b = top + scaledHeight + 2;  
        graphics.fill(l, t, r, b, bg);  
        graphics.fill(l, t, r, t + 1, border);  
        graphics.fill(l, b - 1, r, b, border);  
        graphics.fill(l, t, l + 1, b, border);  
        graphics.fill(r - 1, t, r, b, border);  
  
        int lineStep = Math.max(1, (int) Math.round(LINE_HEIGHT * config.logScale));
        int y = top;  
        for (Component line : lines) {  
            graphics.text(font, line, x, y, text, true);  
            y += lineStep;  
        }  
    }  
  
    private static int position(double percent, int screen, int size) {  
        int available = Math.max(0, screen - size - 2 * EDGE);  
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);  
    }  

    private static int topPosition(double percent, int screen) {
        int available = Math.max(0, screen - 2 * EDGE);
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }
}