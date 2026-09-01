package dev.shafthelper.client;  
  
import java.util.ArrayList;
import java.util.List;

import dev.shafthelper.config.ModConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;


public class HUDEditorScreen extends Screen {  
  
    // These MUST match ShaftHud's private constants so the ghost box lines up.  
    private static final int LINE_HEIGHT = 10;  
    private static final int EDGE = 4;  
    private static final int DIM_BG = 0x90202020; // transparent grey "edit mode" tint  
    private static final int GHOST_BG = 0xE00D1B2A;  
    private static final int GHOST_BORDER = 0xFF778DA9; // accent so it reads as "grabbable"  
  
    private final Screen parent;  

    private interface Hud {
        int width();
        int height();
        double percentX(); 
        void percentX(double p);
        double percentY(); 
        void percentY(double p);
    }

    private final List<Hud> huds = new ArrayList<>();
 
    private Hud dragging = null;  
    private int grabOffX, grabOffY; // where inside the box the user grabbed  

    public HUDEditorScreen(Screen parent) {  
        super(Component.literal("HUD Editor"));  
        this.parent = parent;  
    }  
  
    @Override  
    protected void init() {  
        super.init();  
        huds.clear();  
        ModConfig config = ShaftTracker.config();  
  
        // --- Shaft HUD ---  
        huds.add(new Hud() {  
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                int w = 0;  
                for (Component line : ShaftTracker.trackerLines()) w = Math.max(w, font.width(line));  
                return w;  
            }  
            public int height() { return ShaftTracker.trackerLines().size() * LINE_HEIGHT; }  
            public double percentX() { return config.trackerX; }  public void percentX(double p) { config.trackerX = p; }  
            public double percentY() { return config.trackerY; }  public void percentY(double p) { config.trackerY = p; }  
        });  
        // --- Network HUD ---  
        huds.add(new Hud() {  
            public int width() { return 110; }  
            public int height() { return 30; }  
            public double percentX() { return config.netX; }  public void percentX(double p) { config.netX = p; }  
            public double percentY() { return config.netY; }  public void percentY(double p) { config.netY = p; }  
        });  
        // --- Log HUD --- 
        huds.add(new Hud() {
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                int w = 0;  
                for (Component line : ShaftTracker.logLines()) w = Math.max(w, font.width(line));  
                return w;  
            }  
            public int height() { return ShaftTracker.logLines().size() * LINE_HEIGHT; }  
            public double percentX() { return config.logX; }  public void percentX(double p) { config.logX = p; }  
            public double percentY() { return config.logY; }  public void percentY(double p) { config.logY = p; }  
        }); 
        // --- Profit HUD --- 
        huds.add(new Hud() {
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                int w = 0;  
                for (Component line : ShaftTracker.profitLines()) w = Math.max(w, font.width(line));  
                return w;  
            }   
            public int height() { return ShaftTracker.profitLines().size() * LINE_HEIGHT; } 
            public double percentX() { return config.profitX; }  public void percentX(double p) { config.profitX = p; }  
            public double percentY() { return config.profitY; }  public void percentY(double p) { config.profitY = p; }  
        }); 
        // --- Mining Calculator HUD ---
        huds.add(new Hud() {
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                // Calculate width based on the text displayed in MiningCalculator
                int w = Math.max(
                    font.width(Component.literal("Ruby")),
                    Math.max(
                        font.width(Component.literal("Ticks: 50")),
                        Math.max(
                            font.width(Component.literal("Offset: 5.0")),
                            Math.max(
                                font.width(Component.literal("Ping: 50ms")),
                                Math.max(
                                    font.width(Component.literal("TPS: 20.0")),
                                    font.width(Component.literal("Speed: 50.0"))
                                )
                            )
                        )
                    )
                ) + 15; // Add padding
                return w;  
            }   
            public int height() { return 75; } // Fixed height to match MiningCalculator.BOX_HEIGHT
            public double percentX() { return config.calcX; }  public void percentX(double p) { config.calcX = p; }  
            public double percentY() { return config.calcY; }  public void percentY(double p) { config.calcY = p; }  
        });
        
        // --- Tick Display HUD ---
        huds.add(new Hud() {
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                return font.width(Component.literal("50/50")) + 10;  
            }   
            public int height() { return 15; } 
            public double percentX() { return config.tickX; }  public void percentX(double p) { config.tickX = p; }  
            public double percentY() { return config.tickY; }  public void percentY(double p) { config.tickY = p; }  
        });
        
        // --- Efficiency Display HUD ---
        huds.add(new Hud() {
            public int width() {  
                Font font = HUDEditorScreen.this.font;  
                return Math.max(
                    font.width(Component.literal("Uptime: 30.0s")),
                    font.width(Component.literal("Efficiency: 100%"))
                ) + 10;  
            }   
            public int height() { return 25; } 
            public double percentX() { return config.effX; }  public void percentX(double p) { config.effX = p; }  
            public double percentY() { return config.effY; }  public void percentY(double p) { config.effY = p; }  
        }); 

        // --- Player Timers HUD ---
        huds.add(new Hud() {
            public int width() {
                Font font = HUDEditorScreen.this.font;
                String[] labels = { "Cold Resistance: 1m 30s", "Fillet O' Fortune: 10m 0s", "Pristine Potato: 5m 0s", "Deployable: 5m 0s" };
                int w = 0;
                for (String text : labels) {
                    w = Math.max(w, font.width(Component.literal(text)));
                }
                return w + 12;
            }
            public int height() { return 50; }
            public double percentX() { return config.playerTimersX; }
            public void percentX(double p) { config.playerTimersX = p; }
            public double percentY() { return config.playerTimersY; }
            public void percentY(double p) { config.playerTimersY = p; }
        });
    }   
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {  
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);  
  
        // 1. Grey out the whole screen to signal edit mode.  
        graphics.fill(0, 0, this.width, this.height, DIM_BG);  
  
        for (Hud hud : huds) {
            int w = hud.width(), h = hud.height();
            if (w == 0 || h == 0) continue;  // skip empty HUDs
            int x = percentToPixel(hud.percentX(), this.width, w);
            int y = percentToPixel(hud.percentY(), this.height, h);

            int l = x - 2, t = y - 2, r = x + w + 2, b = y + h + 2;  
            graphics.fill(l, t, r, b, GHOST_BG);  
            graphics.fill(l, t, r, t + 1, GHOST_BORDER);  
            graphics.fill(l, b - 1, r, b, GHOST_BORDER);  
            graphics.fill(l, t, l + 1, b, GHOST_BORDER);  
            graphics.fill(r - 1, t, r, b, GHOST_BORDER);  
        }

        String hint = "Drag the box, then Esc to save";  
        graphics.text(this.font, hint,  
            this.width / 2 - this.font.width(hint) / 2, 6, 0xFFE0E1DD, true);  
    }  
  
    @Override  
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {  
        if (event.button() == 0) {  
        // Iterate topmost-first so overlapping boxes grab the last-drawn one.  
            for (int i = huds.size() - 1; i >= 0; i--) {  
                Hud hud = huds.get(i);  
                int w = hud.width(), h = hud.height();  
                if (w == 0 || h == 0) continue;  
                int x = percentToPixel(hud.percentX(), this.width, w);  
                int y = percentToPixel(hud.percentY(), this.height, h);  
                if (event.x() >= x - 2 && event.x() <= x + w + 2  
                        && event.y() >= y - 2 && event.y() <= y + h + 2) {  
                    dragging = hud;  
                    grabOffX = (int) event.x() - x;  
                    grabOffY = (int) event.y() - y;  
                    return true;  
                }  
            }  
        }  
        return super.mouseClicked(event, doubleClick); 
    }  
  
    @Override  
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {  
        if (dragging != null && event.button() == 0) {  
            int w = dragging.width(), h = dragging.height();  
            int newX = (int) event.x() - grabOffX;  
            int newY = (int) event.y() - grabOffY;  
            dragging.percentX(pixelToPercent(newX, this.width, w));  
            dragging.percentY(pixelToPercent(newY, this.height, h));  
            return true;  
        }  
        return super.mouseDragged(event, deltaX, deltaY);  
    }   
  
    @Override  
    public boolean mouseReleased(MouseButtonEvent event) {  
        dragging = null;  
        return super.mouseReleased(event);  
    }  
  
    private static int percentToPixel(double percent, int screen, int size) {  
        int available = Math.max(0, screen - size - 2 * EDGE);  
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);  
    }  
  
    private static double pixelToPercent(int pixel, int screen, int size) {  
        int available = Math.max(0, screen - size - 2 * EDGE);  
        if (available == 0) return 0;  
        return Math.clamp((pixel - EDGE) * 100.0 / available, 0.0, 100.0);  
    }  
  
    @Override  
    public void onClose() {  
        ShaftTracker.saveConfig();          // persists hudX/hudY AND netX/netY  
        this.minecraft.setScreen(parent);  
    }  
}