package dev.shafthelper;

import dev.shafthelper.client.MiningCalculator;
import dev.shafthelper.client.NetworkTracker;
import dev.shafthelper.client.ShaftProfitHud;
import dev.shafthelper.client.ShaftLogHud;
import dev.shafthelper.client.ShaftHud;
import dev.shafthelper.client.ShaftTracker;
import dev.shafthelper.client.WaypointRenderer;
import dev.shafthelper.client.TickDisplay;
import dev.shafthelper.client.ShaftTracker;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Waypoint;
import dev.shafthelper.client.EfficiencyDisplay;
import dev.shafthelper.command.ShaftCommand;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import net.fabricmc.api.ClientModInitializer;
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
        autoImportPresets();
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> ShaftCommand.register(dispatcher));
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

    private void autoImportPresets() {  
        try {  
            if (ShaftTracker.config().presetsAutoImported) return; // once-done guard  
    
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("PresavedWaypoints");  
            if (!Files.isDirectory(dir)) return;  
    
            Gson gson = new GsonBuilder().create();  
            boolean addedAny = false;  
    
            try (java.util.stream.Stream<Path> files = Files.list(dir)) {  
                for (Path file : (Iterable<Path>) files::iterator) {  
                    if (!file.toString().endsWith(".json")) continue;  
                    addedAny |= importPresetFile(file, gson);  
                }  
            }  
    
            ShaftTracker.config().presetsAutoImported = true;  
            ShaftTracker.saveConfig();  
        } catch (Exception e) {  
            // auto-import failed — user can still import manually  
        }  
    }  
    
    private boolean importPresetFile(Path path, Gson gson) {  
        try {  
            String json = Files.readString(path);  
            JsonElement root = JsonParser.parseString(json);  
    
            JsonArray elements = new JsonArray();  
            if (root.isJsonArray()) elements = root.getAsJsonArray();  
            else if (root.isJsonObject()) elements.add(root);  
    
            String headerGroup = null;  
            String headerIsland = "Mineshafts";  
            boolean firstElement = true;  
            boolean addedAny = false;  
    
            for (JsonElement el : elements) {  
                if (!el.isJsonObject()) { firstElement = false; continue; }  
                JsonObject obj = el.getAsJsonObject();  
    
                boolean isHeader = firstElement  
                    && (obj.has("group") || obj.has("area"))  
                    && !obj.has("x") && !obj.has("y") && !obj.has("z")  
                    && !obj.has("options");  
                firstElement = false;  
                if (isHeader) {  
                    if (obj.has("group")) headerGroup = obj.get("group").getAsString();  
                    if (obj.has("area")) {  
                        String a = AreaDetector.getDisplayName(  
                            AreaDetector.fromDisplayName(obj.get("area").getAsString()));  
                        if (a != null && !a.isEmpty()) headerIsland = a;  
                    }  
                    continue;  
                }  
    
                Waypoint wp = parseWaypoint(obj, gson);  
                if (wp != null && wp.name != null && !wp.name.isEmpty()) {  
                    if (headerGroup != null) wp.group = headerGroup;  
                    wp.island = headerIsland;  
                    ShaftTracker.config().waypoints.add(wp);  
                    addedAny = true;  
                }  
            }  
            return addedAny;  
        } catch (Exception e) {  
            return false;  
        }  
    }  
    
    // Mirrors ConfigScreen's SkyBlock-vs-plain handling  
    private Waypoint parseWaypoint(JsonObject obj, Gson gson) {  
        boolean sbFormat = obj.has("options") || obj.has("r") || obj.has("g") || obj.has("b");  
        if (sbFormat) {  
            double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;  
            double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;  
            double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;  
            float r = obj.has("r") ? obj.get("r").getAsFloat() : 1f;  
            float g = obj.has("g") ? obj.get("g").getAsFloat() : 0f;  
            float b = obj.has("b") ? obj.get("b").getAsFloat() : 0f;  
            String name = null;  
            if (obj.has("options") && obj.getAsJsonObject("options").has("name"))  
                name = obj.getAsJsonObject("options").get("name").getAsString();  
    
            Waypoint wp = new Waypoint();  
            wp.name = name;  
            wp.x = (int) Math.round(-x);   // un-negate, same as ConfigScreen.fromSbFormat  
            wp.y = (int) Math.round(y);  
            wp.z = (int) Math.round(-z);  
            wp.color = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);  
            wp.enabled = true;  
            wp.setId(UUID.randomUUID().toString());  
            return wp;  
        } else {  
            Waypoint wp = gson.fromJson(obj, Waypoint.class);  
            if (wp != null) wp.setId(UUID.randomUUID().toString());  
            return wp;  
        }  
    }
}
