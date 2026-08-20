package dev.shafthelper.client.widget;  
  
import net.minecraft.client.gui.GuiGraphicsExtractor;  
  
public final class StyledTheme {  
    public static final int BG       = 0xE00D1B2A; // PANEL_BG  
    public static final int BG_HOVER = 0xE0163049;  
    public static final int BORDER   = 0xFF1B263B; // PANEL_BORDER  
    public static final int ACCENT   = 0xFF778DA9; // HEADER_COLOR  
    public static final int TEXT     = 0xFFE0E1DD; // LABEL_COLOR  
    public static final int TEXT_OFF = 0xFF6B7280;  
  
    private StyledTheme() {}  
  
    /** Fill + 1px border, matching the ConfigScreen panel look. */  
    public static void box(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int bg, int border) {  
        g.fill(x, y, x2, y2, bg);  
        g.fill(x, y, x2, y + 1, border);   // top  
        g.fill(x, y2 - 1, x2, y2, border); // bottom  
        g.fill(x, y, x + 1, y2, border);   // left  
        g.fill(x2 - 1, y, x2, y2, border); // right  
    }  
}