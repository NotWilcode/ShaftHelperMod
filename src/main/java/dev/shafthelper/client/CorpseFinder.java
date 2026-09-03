package dev.shafthelper.client;  
  
import java.util.ArrayList;  
import java.util.List;  
import java.util.Locale;  
import java.util.Optional;  
  
import dev.shafthelper.core.AreaDetector;  
import dev.shafthelper.core.Waypoint;  
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;  
import net.minecraft.client.Minecraft;  
import net.minecraft.world.entity.decoration.ArmorStand;  
import net.minecraft.world.phys.AABB;  
  
public final class CorpseFinder {  
  
    private static final double RADIUS = 40.0;  
  
    // Corpse type -> box color (ARGB-less RGB int, matches Waypoint.color convention).  
    private record CorpseType(String key, int color) {}  
    private static final CorpseType[] TYPES = new CorpseType[] {  
        new CorpseType("lapis",      0x2952FF), // blue  
        new CorpseType("umber",      0xB8621B), // brown/orange  
        new CorpseType("tungsten",   0x9AA0A6), // grey  
        new CorpseType("vanguard",   0xFF3B3B), // red  
        new CorpseType("prospector", 0xFFD24A), // gold  
    };  
  
    private static volatile List<Waypoint> detected = List.of();  
    private static boolean initialized = false;  
  
    public static void register() {  
        if (initialized) return;  
        ClientTickEvents.END_CLIENT_TICK.register(CorpseFinder::scan);  
        initialized = true;  
    }  
  
    /** Returns a snapshot copy of the currently detected corpse waypoints. */  
    public static List<Waypoint> detectedCorpses() {  
        return new ArrayList<>(detected);  
    }  
  
    private static void scan(Minecraft client) {  
        if (client.player == null || client.level == null) {  
            detected = List.of();  
            return;  
        }  
  
        dev.shafthelper.config.ModConfig config = ShaftTracker.config();  
        if (config == null || !config.corpseFinderEnabled) {  
            detected = List.of();  
            return;  
        }  
  
        // Only run in Glacite Mineshafts to avoid false positives elsewhere.  
        Optional<AreaDetector.Area> area = ShaftTracker.currentArea();  
        if (area.orElse(AreaDetector.Area.UNKNOWN) != AreaDetector.Area.MINESHAFTS) {  
            detected = List.of();  
            return;  
        }  
  
        AABB box = client.player.getBoundingBox().inflate(RADIUS);  
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, box);  
  
        List<Waypoint> found = new ArrayList<>();  
        for (ArmorStand stand : stands) {  
            String name = displayName(stand);  
            if (name == null) continue;  
            String lower = name.toLowerCase(Locale.ROOT);  
            if (!lower.contains("corpse")) continue;  
  
            CorpseType type = matchType(lower);  
            int color = type != null ? type.color() : 0xFF0000;  
  
            int x = (int) Math.floor(stand.getX());  
            int y = (int) Math.floor(stand.getY());  
            int z = (int) Math.floor(stand.getZ());  
  
            if (alreadyAdded(found, x, y, z)) continue;  
  
            Waypoint wp = new Waypoint(name.trim(), x, y, z, "Corpse Finder", "Mineshaft");  
            wp.enabled = true;  
            wp.color = color;  
            found.add(wp);  
        }  
  
        detected = found;  
    }  
  
    private static String displayName(ArmorStand stand) {  
        if (stand.getCustomName() != null) return stand.getCustomName().getString();  
        // Some corpses expose their label through the entity's own name.  
        if (stand.hasCustomName()) return stand.getName().getString();  
        return null;  
    }  
  
    private static CorpseType matchType(String lowerName) {  
        for (CorpseType type : TYPES) {  
            if (lowerName.contains(type.key())) return type;  
        }  
        return null;  
    }  
  
    private static boolean alreadyAdded(List<Waypoint> list, int x, int y, int z) {  
        for (Waypoint wp : list) {  
            if (Math.abs(wp.x - x) <= 1 && Math.abs(wp.y - y) <= 1 && Math.abs(wp.z - z) <= 1) {  
                return true;  
            }  
        }  
        return false;  
    }  
  
    private CorpseFinder() {}  
}