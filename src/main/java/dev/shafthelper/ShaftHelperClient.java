package dev.shafthelper;

import dev.shafthelper.client.MiningCalculator;
import dev.shafthelper.client.NetworkTracker;
import dev.shafthelper.client.ShaftProfitHud;
import dev.shafthelper.client.ShaftLogHud;
import dev.shafthelper.client.ShaftHud;
import dev.shafthelper.client.ShaftTracker;
import dev.shafthelper.client.WaypointRenderer;
import dev.shafthelper.client.TickDisplay;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import dev.shafthelper.client.EfficiencyDisplay;
import dev.shafthelper.command.ShaftCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents; 
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class ShaftHelperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ShaftTracker.init();
        extractPresets();
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> ShaftCommand.register(dispatcher));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> dev.shafthelper.client.ServerStats.onWorldSwitch());  
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> dev.shafthelper.client.ServerStats.onWorldSwitch());
        ClientTickEvents.END_CLIENT_TICK.register(ShaftTracker::onEndTick);
        ClientReceiveMessageEvents.GAME.register(
            (message, overlay) -> { if (!overlay) ShaftTracker.onGameMessage(message); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "shaft_hud"), new ShaftHud());
        WaypointRenderer.register();
        NetworkTracker.register();
        MiningCalculator.register();
        TickDisplay.register();
        EfficiencyDisplay.register();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "network_tracker"), new NetworkTracker());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "mining_calculator"), new MiningCalculator());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "tick_display"), new TickDisplay());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "efficiency_display"), new EfficiencyDisplay());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "shaft_profit_hud"), new ShaftProfitHud());  
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shafthelper", "shaft_log_hud"), new ShaftLogHud());
    }

    private void extractPresets() {  
    try {  
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("PresavedWaypoints");  
        Files.createDirectories(dir);  
  
        List<String> names;  
        try (InputStream idx = getClass().getResourceAsStream("/assets/PresavedWaypoints/index.json")) {  
            if (idx == null) return;  
            String json = new String(idx.readAllBytes());  
            names = new Gson().fromJson(json, new TypeToken<List<String>>(){}.getType());  
        }  
        if (names == null) return;  
  
        for (String name : names) {  
            Path target = dir.resolve(name);  
            if (Files.exists(target)) continue; // don't overwrite user edits  
            try (InputStream in = getClass().getResourceAsStream("/assets/PresavedWaypoints/" + name)) {  
                if (in != null) Files.copy(in, target);  
            }  
        }  
    } catch (Exception e) {  
        // extraction failed — presets just won't be available  
    }  
}
}
