package dev.shafthelper.client.widget;  
  
import java.util.function.Consumer;

import dev.shafthelper.core.Gemstones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;  
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;  
  
public final class StyledSlider extends AbstractSliderButton {  
    private final Consumer<String> onChange;  
    private static final int MAX = Gemstones.ALL.size() - 1;  
  
    public StyledSlider(int x, int y, int w, int h, String current, Consumer<String> onChange) {  
        super(x, y, w, h, Component.empty(), indexOf(current) / (double) MAX);  
        this.onChange = onChange;  
        updateMessage();  
    }  
  
    private static int indexOf(String name) {  
        for (int i = 0; i <= MAX; i++)  
            if (Gemstones.ALL.get(i).name().equals(name)) return i;  
        return 0;  
    }  
  
    private int index() { return (int) Math.round(value * MAX); }  
  
    @Override  
    protected void updateMessage() {  
        setMessage(Component.literal(Gemstones.ALL.get(index()).name()));  
    }  
  
    @Override  
    protected void applyValue() {  
        onChange.accept(Gemstones.ALL.get(index()).name());  
    }  
  
    @Override  
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {  
        int x = getX(), y = getY(), x2 = x + getWidth(), y2 = y + getHeight();  
        StyledTheme.box(g, x, y, x2, y2, StyledTheme.BG, StyledTheme.BORDER);  
        int hx = x + (int) (value * (getWidth() - 8));  
        g.fill(hx, y, hx + 8, y2, StyledTheme.ACCENT);  
    
        var font = Minecraft.getInstance().font;  
        String label = getMessage().getString();  
        g.text(font, label, x + (getWidth() - font.width(label)) / 2, y + (getHeight() - 8) / 2,  
            StyledTheme.TEXT, true);  
    } 
}