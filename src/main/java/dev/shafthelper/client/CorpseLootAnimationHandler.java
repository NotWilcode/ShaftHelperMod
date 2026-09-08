package dev.shafthelper.client;

import java.util.function.Function;

import dev.shafthelper.client.CorpseOpeningScreen;
import dev.shafthelper.core.CorpseLootParser;
import dev.shafthelper.core.DropTracker;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Wires CorpseLootParser + DropTracker into a chat listener that pops the
 * CS:GO-style reveal screen whenever a Vanguard corpse is opened.
 *
 * Only VANGUARD corpses trigger the animation; Lapis/Umber/Tungsten loot is
 * left to display normally. Register this once from your client mod
 * initializer, e.g.:
 *
 *   new CorpseLootAnimationHandler(dropTracker, this::bazaarUnitPrice).register();
 */
public final class CorpseLootAnimationHandler {

    private final DropTracker dropTracker;
    private final Function<String, Double> priceLookup;

    /**
     * @param dropTracker your existing session tracker (recordCorpseLoot is
     *                    called exactly as it would be without this feature,
     *                    so totals/session tracking are unaffected)
     * @param priceLookup display name -> current unit price, or null if you
     *                    don't have one wired up yet (falls back to ranking
     *                    the reel winner by amount instead of value)
     */
    public CorpseLootAnimationHandler(DropTracker dropTracker, Function<String, Double> priceLookup) {
        this.dropTracker = dropTracker;
        this.priceLookup = priceLookup;
    }

    public void register() {
        // GAME (not ALLOW_GAME) fires after the message has already been
        // added to chat, so the loot text shows first, then we take over
        // the screen right after - matching the "show it, then hide/animate" flow.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // ignore action bar messages
            onChatMessage(message);
        });
    }

    private void onChatMessage(Component message) {
        CorpseLootParser.Loot loot = CorpseLootParser.parse(message);
        if (loot == null) return;
        if (loot.type() != CorpseLootParser.CorpseType.VANGUARD) return;
        if (loot.rewards().isEmpty()) return;

        dropTracker.recordCorpseLoot(loot);

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen == null) {
                client.setScreen(new CorpseOpeningScreen(loot.rewards(), priceLookup));
            }
        });
    }
}