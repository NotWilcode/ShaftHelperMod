package dev.shafthelper.client;  
  
import dev.shafthelper.client.widget.StyledButton;  
import net.minecraft.util.Util;  
import net.minecraft.client.gui.screens.ConfirmLinkScreen;  
import net.minecraft.client.gui.screens.Screen;  
import net.minecraft.network.chat.Component;  
  
public final class ShaftMenuScreen extends Screen {  
  
    private static final int BTN_W = 200;  
    private static final int BTN_H = 20;  
    private static final int GAP = 6;  
  
    // TODO: replace these with the real links  
    private static final String GITHUB_URL   = "https://github.com/NotWilcode/ShaftHelperMod";  
    private static final String MODRINTH_URL = "https://modrinth.com/mod/REPLACE_ME";  
    private static final String DISCORD_URL  = "https://discord.gg/REPLACE_ME";  
  
    public ShaftMenuScreen() {  
        super(Component.literal("Shaft Helper"));  
    }  
  
    @Override  
    protected void init() {  
        int x = this.width / 2 - BTN_W / 2;  
        int y = this.height / 2 - (BTN_H + GAP) * 3;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Config"), b -> this.minecraft.setScreen(new ConfigScreen())));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Help"), b -> this.minecraft.setScreen(new ShaftGuideScreen())));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Info"), b -> this.minecraft.setScreen(new ShaftInfoScreen())));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("GitHub"), b -> openLink(GITHUB_URL)));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Modrinth"), b -> openLink(MODRINTH_URL)));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Discord"), b -> openLink(DISCORD_URL)));  
        y += BTN_H + GAP;  
  
        addRenderableWidget(new StyledButton(x, y, BTN_W, BTN_H,  
            Component.literal("Close"), b -> this.onClose()));  
    }  
  
    private void openLink(String url) {  
        this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {  
            if (confirmed) Util.getPlatform().openUri(url);  
            this.minecraft.setScreen(this);  
        }, url, true));  
    }  
}