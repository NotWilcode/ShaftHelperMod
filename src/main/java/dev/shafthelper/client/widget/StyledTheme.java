package dev.shafthelper.client.widget;  
  
import dev.shafthelper.client.ShaftTracker;
import dev.shafthelper.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;  
  
public final class StyledTheme {  
    public static final int BG = 0xE00D1B2A;  
    public static final int BG_HOVER = 0xE0163049;  
    public static final int BORDER = 0xFF1B263B;  
    public static final int ACCENT = 0xFF778DA9;  
    public static final int TEXT = 0xFFE0E1DD;  
    public static final int TEXT_OFF = 0xFF6B7280;  

    private StyledTheme() {}  

    public static ModConfig config() {
        return ShaftTracker.config();
    }

    public static int bg() {
        ModConfig config = config();
        return config == null ? BG : config.themeBg;
    }

    public static int bgHover() {
        ModConfig config = config();
        int base = config == null ? BG_HOVER : config.themeBg;
        return (base & 0x00FFFFFF) | 0xE0000000;
    }

    public static int border() {
        ModConfig config = config();
        return config == null ? BORDER : config.themeBorder;
    }

    public static int accent() {
        ModConfig config = config();
        return config == null ? ACCENT : config.themeAccent;
    }

    public static int text() {
        ModConfig config = config();
        return config == null ? TEXT : config.themeText;
    }

    public static int textOff() {
        ModConfig config = config();
        return config == null ? TEXT_OFF : config.themeTextOff;
    }
  
    /** Fill + 1px border, matching the ConfigScreen panel look. */  
    public static void box(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int bg, int border) {  
        g.fill(x, y, x2, y2, bg);  
        g.fill(x, y, x2, y + 1, border);   // top  
        g.fill(x, y2 - 1, x2, y2, border); // bottom  
        g.fill(x, y, x + 1, y2, border);   // left  
        g.fill(x2 - 1, y, x2, y2, border); // right  
    }  
}