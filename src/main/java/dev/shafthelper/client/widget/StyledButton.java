package dev.shafthelper.client.widget;  
  
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;  
import net.minecraft.network.chat.Component;  
  
public final class StyledButton extends Button {  
    public StyledButton(int x, int y, int w, int h, Component msg, OnPress onPress) {  
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);  
    }  
  
    @Override  
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {  
        boolean hover = isHovered();  
        int bg     = !active ? StyledTheme.bg() : (hover ? StyledTheme.bgHover() : StyledTheme.bg());  
        int border = (hover && active) ? StyledTheme.accent() : StyledTheme.border();  
        StyledTheme.box(g, getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg, border);  
  
        var font = Minecraft.getInstance().font;  
        String label = getMessage().getString();  
        int tx = getX() + (getWidth() - font.width(label)) / 2;  
        int ty = getY() + (getHeight() - 8) / 2;  
        g.text(font, label, tx, ty, active ? StyledTheme.text() : StyledTheme.textOff(), true);  
    }  
}