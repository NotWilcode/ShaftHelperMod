package dev.shafthelper.client;

import java.util.ArrayList;
import java.util.List;

import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.HotmParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class HotmTracker {
    private static boolean initialized;

    public static void register() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(HotmTracker::scan);
    }

    private static void scan(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        String title = screen.getTitle().getString();
        if (!title.toLowerCase().contains("heart of the mountain")) return;

        List<List<String>> tooltips = new ArrayList<>();
        for (var slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> tooltip = new ArrayList<>();
            tooltip.add(stack.getHoverName().getString());
            ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) tooltip.add(line.getString());
            }
            tooltips.add(tooltip);
        }

        HotmParser.Perks perks = HotmParser.perks(tooltips);
        ModConfig config = ShaftTracker.config();
        if (config == null) return;
        boolean changed = false;
        if (perks.professionalLevel() != null && config.proffesionalLevel != perks.professionalLevel()) {
            config.proffesionalLevel = perks.professionalLevel();
            changed = true;
        }
        if (config.eagerAdventurer != perks.eagerAdventurer()) { config.eagerAdventurer = perks.eagerAdventurer(); changed = true; }
        if (config.mineshaftMayhem != perks.mineshaftMayhem()) { config.mineshaftMayhem = perks.mineshaftMayhem(); changed = true; }
        if (config.steadyHand != perks.steadyHand()) { config.steadyHand = perks.steadyHand(); changed = true; }
        if (config.ragsToRiches != perks.ragsToRiches()) { config.ragsToRiches = perks.ragsToRiches(); changed = true; }
        if (changed) {
            ShaftTracker.saveConfig();
        }
    }

    private HotmTracker() {}
}