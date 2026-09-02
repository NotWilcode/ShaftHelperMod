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
  
    private final Screen parent;  

    private interface Hud {
        int width();
        int height();
        double scale();
        void scale(double s);
        double percentX(); 
        void percentX(double p);
        double percentY(); 
        void percentY(double p);
    }

    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 2.5;

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
            public double scale() { return config.trackerScale; }  public void scale(double s) { config.trackerScale = s; }
            public double percentX() { return config.trackerX; }  public void percentX(double p) { config.trackerX = p; }  
            public double percentY() { return config.trackerY; }  public void percentY(double p) { config.trackerY = p; }  
        });  
        // --- Network HUD ---  
        huds.add(new Hud() {  
            public int width() { return 110; }  
            public int height() { return 30; }  
            public double scale() { return config.netScale; }  public void scale(double s) { config.netScale = s; }
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
            public double scale() { return config.logScale; }  public void scale(double s) { config.logScale = s; }
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
            public double scale() { return config.profitScale; }  public void scale(double s) { config.profitScale = s; }
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
            public double scale() { return config.calcScale; } public void scale(double s) { config.calcScale = s; }
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
            public double scale() { return config.tickScale; } public void scale(double s) { config.tickScale = s; }
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
            public double scale() { return config.effScale; } public void scale(double s) { config.effScale = s; }
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
            public double scale() { return config.playerTimersScale; } public void scale(double s) { config.playerTimersScale = s; }
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
            int w = scaledWidth(hud), h = scaledHeight(hud);
            if (w == 0 || h == 0) continue;  // skip empty HUDs
            int x = percentToPixel(hud.percentX(), this.width, w);
            int y = percentToPixel(hud.percentY(), this.height, h);

            int bg = ShaftTracker.config().themeBg;
            int border = ShaftTracker.config().themeAccent;
            int text = ShaftTracker.config().themeText;
            int l = x - 2, t = y - 2, r = x + w + 2, b = y + h + 2;  
            graphics.fill(l, t, r, b, bg);  
            graphics.fill(l, t, r, t + 1, border);  
            graphics.fill(l, b - 1, r, b, border);  
            graphics.fill(l, t, l + 1, b, border);  
            graphics.fill(r - 1, t, r, b, border);  

            String scaleText = String.format("Scale: %.2fx", hud.scale());
            graphics.text(this.font, scaleText, x + 4, y + 4, text, true);
        }

        String hint = "Drag to move, wheel to resize, then Esc to save";  
        graphics.text(this.font, hint,  
            this.width / 2 - this.font.width(hint) / 2, 6, ShaftTracker.config().themeText, true);  
    }  
  
    @Override  
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {  
        if (event.button() == 0) {  
        // Iterate topmost-first so overlapping boxes grab the last-drawn one.  
            for (int i = huds.size() - 1; i >= 0; i--) {  
                Hud hud = huds.get(i);  
                int w = scaledWidth(hud), h = scaledHeight(hud);  
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Hud target = null;
        for (int i = huds.size() - 1; i >= 0; i--) {
            Hud hud = huds.get(i);
            int w = scaledWidth(hud), h = scaledHeight(hud);
            if (w == 0 || h == 0) continue;
            int x = percentToPixel(hud.percentX(), this.width, w);
            int y = percentToPixel(hud.percentY(), this.height, h);
            if (mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= y - 2 && mouseY <= y + h + 2) {
                target = hud;
                break;
            }
        }
        if (target == null && dragging != null) target = dragging;
        if (target != null && scrollY != 0.0) {
            double next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, target.scale() + (scrollY > 0 ? -0.1 : 0.1)));
            target.scale(next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
  
    @Override  
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {  
        if (dragging != null && event.button() == 0) {  
            int w = scaledWidth(dragging), h = scaledHeight(dragging);  
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
  
    private static int scaledWidth(Hud hud) {
        return Math.max(1, (int) Math.round(hud.width() * hud.scale()));
    }

    private static int scaledHeight(Hud hud) {
        return Math.max(1, (int) Math.round(hud.height() * hud.scale()));
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