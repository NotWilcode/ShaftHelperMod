package dev.shafthelper.client;  
  
import java.util.List;  
  
import dev.shafthelper.config.ModConfig;  
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;  
import net.minecraft.client.DeltaTracker;  
import net.minecraft.client.Minecraft;  
import net.minecraft.client.gui.Font;  
import net.minecraft.client.gui.GuiGraphicsExtractor;  
import net.minecraft.network.chat.Component;  
  
public final class ShaftProfitHud implements HudElement {  
  
    private static final int LINE_HEIGHT = 10;  
    private static final int LINE_COLOR = 0xFFE0E1DD;  
    private static final int BACKGROUND = 0xE00D1B2A;  
    private static final int BORDER     = 0xFF1B263B;  
    private static final int EDGE = 4;  
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {  
        List<Component> lines = ShaftTracker.profitLines();  
        if (lines.isEmpty()) return;  
  
        Minecraft client = Minecraft.getInstance();  
        if (client.getDebugOverlay().showDebugScreen()) return;  
        Font font = client.font;  
  
        ModConfig config = ShaftTracker.config();  
        int width = 0;  
        for (Component line : lines) {  
            width = Math.max(width, font.width(line));  
        }  
        int height = lines.size() * LINE_HEIGHT;  
        int x = position(config.profitX, graphics.guiWidth(), width);  
        int top = position(config.profitY, graphics.guiHeight(), height);  
  
        int l = x - 2, t = top - 2, r = x + width + 2, b = top + height + 2;  
        graphics.fill(l, t, r, b, BACKGROUND);  
        graphics.fill(l, t, r, t + 1, BORDER);  
        graphics.fill(l, b - 1, r, b, BORDER);  
        graphics.fill(l, t, l + 1, b, BORDER);  
        graphics.fill(r - 1, t, r, b, BORDER);  
  
        int y = top;  
        for (Component line : lines) {  
            graphics.text(font, line, x, y, LINE_COLOR, true);  
            y += LINE_HEIGHT;  
        }  
    }  
  
    private static int position(double percent, int screen, int size) {  
        int available = Math.max(0, screen - size - 2 * EDGE);  
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);  
    }  
}