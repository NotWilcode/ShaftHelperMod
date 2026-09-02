package dev.shafthelper.client;

import java.util.regex.Pattern;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class PickaxeAbilityAlert implements HudElement {
    private static final Pattern READY_PATTERN = Pattern.compile(".*\\S.* is now available!$");
    private static final long DISPLAY_MS = 4_000L;
    private static boolean initialized = false;
    private static long visibleUntilMs = 0L;

    public static void register() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register(PickaxeAbilityAlert::onChatMessage);
        ClientTickEvents.END_CLIENT_TICK.register(PickaxeAbilityAlert::onClientTick);
    }

    static boolean matchesReadyText(String text) {
        if (text == null) return false;
        return READY_PATTERN.matcher(stripFormatting(text).trim()).matches();
    }

    private static void onClientTick(Minecraft client) {
        if (visibleUntilMs != 0L && System.currentTimeMillis() >= visibleUntilMs) {
            visibleUntilMs = 0L;
        }
    }

    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay || message == null) return;
        String text = stripFormatting(message.getString());
        if (!matchesReadyText(text)) return;

        visibleUntilMs = System.currentTimeMillis() + DISPLAY_MS;
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 1.4F));
        }
    }

    private static String stripFormatting(String raw) {
        if (raw == null) return "";
        return raw.replace("§", "").replace("\u00a7", "").trim();
    }

    private static boolean isVisible() {
        return visibleUntilMs > System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!isVisible()) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        Font font = client.font;
        String text = "Pickaxe ability ready";
        int width = Math.max(320, font.width(text) + 48);
        int height = 56;
        int x = (graphics.guiWidth() - width) / 2;
        int y = Math.max(40, graphics.guiHeight() / 5);

        ModConfig config = ShaftTracker.config();
        if (config == null) return;

        long now = System.currentTimeMillis();
        double pulse = (Math.sin(now / 180.0) + 1.0) / 2.0;
        int alpha = 160 + (int) (80.0 * pulse);
        int bg = ((alpha & 0xFF) << 24) | (config.themeBg & 0x00FFFFFF);
        int border = config.themeAccent;
        int textColor = config.themeText;

        graphics.fill(x, y, x + width, y + height, bg);
        graphics.fill(x, y, x + width, y + 2, border);
        graphics.fill(x, y + height - 2, x + width, y + height, border);
        graphics.fill(x, y, x + 2, y + height, border);
        graphics.fill(x + width - 2, y, x + width, y + height, border);

        int textX = x + (width - font.width(text)) / 2;
        int textY = y + (height - 9) / 2;
        graphics.text(font, Component.literal(text), textX, textY, textColor, true);
    }
}
