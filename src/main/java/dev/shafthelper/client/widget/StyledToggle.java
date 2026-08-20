package dev.shafthelper.client.widget;  
  
import java.util.function.Consumer;  
import net.minecraft.client.gui.GuiGraphicsExtractor;  
import net.minecraft.client.gui.components.Button;  
import net.minecraft.network.chat.Component;  
  
public final class StyledToggle extends Button {  
    private boolean value;  
    private final Consumer<Boolean> onChange;  
  
    public StyledToggle(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {  
        // Pass the functionality directly into the Button constructor's action argument
        super(x, y, w, h, Component.empty(), button -> {
            if (button instanceof StyledToggle toggle) {
                toggle.value = !toggle.value;
                toggle.onChange.accept(toggle.value);
            }
        }, DEFAULT_NARRATION);  
        
        this.value = initial;  
        this.onChange = onChange;  
    }  
  
    @Override  
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {  
        int x = getX(), y = getY(), x2 = x + getWidth(), y2 = y + getHeight();  
        // track  
        StyledTheme.box(g, x, y, x2, y2, StyledTheme.BG,  
            value ? StyledTheme.ACCENT : StyledTheme.BORDER);  
        // knob: right half when on, left half when off  
        int knobW = getWidth() / 2 - 2;  
        int knobX = value ? (x2 - knobW - 2) : (x + 2);  
        g.fill(knobX, y + 2, knobX + knobW, y2 - 2,  
            value ? StyledTheme.ACCENT : StyledTheme.TEXT_OFF);  
    }  
}