package dev.shafthelper.client;  
  
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shafthelper.ShaftHelperClient;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Waypoint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;  
import net.minecraft.world.phys.AABB;  
  
/**  
 * Detects Glacite Mineshaft corpses by their HELMET (they are name-less armor stands):  
 *   Tungsten -> player head with custom_data id "MINERAL_HELMET" (confirmed)  
 *   Lapis    -> Sea Lantern helmet  
 *   Umber    -> orange-dyed leather helmet  
 *   Vanguard -> Vanguard player head (custom_data id, best-guess "VANGUARD")  
 *  
 * Checkpoint system: pick the nearest un-cleared candidate corpse waypoint; when the player is  
 * close, confirm+clear it if a real corpse stand is there, otherwise clear it as empty and move  
 * on. READS entity data only -- no movement/clicking/looting (Hypixel-legal).  
 */  
public final class CorpseFinder {  
  
    private static final Logger LOGGER = LoggerFactory.getLogger("ShaftHelper/CorpseFinder");  
    private static final boolean DEBUG = true;  
  
    private static final double RADIUS = 40.0;  
    private static final double CHECK_RADIUS = 6.0;   // how close to a candidate = "checked"  
    private static final double CORPSE_MATCH_RADIUS = 6.0; // stand-to-candidate match distance  
  
    private record CorpseType(String name, int boxColor) {}  
    private static final CorpseType TUNGSTEN = new CorpseType("Tungsten Corpse", 0x9AA0A6);  
    private static final CorpseType LAPIS    = new CorpseType("Lapis Corpse",    0x2952FF);  
    private static final CorpseType UMBER    = new CorpseType("Umber Corpse",    0xB8621B);  
    private static final CorpseType VANGUARD = new CorpseType("Vanguard Corpse", 0xFF3B3B);  
  
    private static volatile List<Waypoint> detected = List.of();  
    private static volatile Waypoint activeCandidate = null;  
  
    // Checkpoint state, reset when the shaft changes.  
    private static String currentShaftCode = "";  
    private static final Set<String> clearedCandidates = new HashSet<>(); // "x,y,z"  
    private static final Set<Long> loggedPositions = new HashSet<>();  
  
    public static void register() {  
        ClientTickEvents.END_CLIENT_TICK.register(CorpseFinder::scan);  
    }  
  
    /** Real detected corpse armor stands (fresh each scan). */  
    public static List<Waypoint> detectedCorpses() {  
        return new ArrayList<>(detected);  
    }  
  
    /** The current candidate corpse waypoint the player should head to, or null. */  
    public static Waypoint activeCandidate() {  
        return activeCandidate;  
    }  
  
    private static void scan(Minecraft client) {  
        if (client.player == null || client.level == null) { reset(); return; }  
  
        dev.shafthelper.config.ModConfig config = ShaftTracker.config();  
        if (config == null || !config.corpseFinderEnabled) { reset(); return; }  
  
        // Gate strictly to Glacite Mineshafts so nothing clogs logs elsewhere.  
        if (ShaftTracker.currentArea().orElse(AreaDetector.Area.UNKNOWN) != AreaDetector.Area.MINESHAFTS) {  
            reset();  
            return;  
        }  
  
        var player = client.player;  
  
        // Reset checkpoint state when the shaft changes.  
        String shaftCode = ShaftTracker.currentShaft().map(s -> s.code()).orElse("");  
        if (!shaftCode.equals(currentShaftCode)) {  
            currentShaftCode = shaftCode;  
            clearedCandidates.clear();  
            loggedPositions.clear(); 
            int totalForShaft = 0;
            for (Waypoint c : ShaftHelperClient.corpseWaypoints()) {  
                if (WaypointRenderer.groupMatchesShaft(c.group, shaftCode)) totalForShaft++;  
            }
            allFound = totalForShaft > 0 && clearedCandidates.size() >= totalForShaft;
        }  
  
        // 1) Detect real corpse stands in range.  
        AABB box = player.getBoundingBox().inflate(RADIUS);  
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, box);  
        List<Waypoint> found = new ArrayList<>();  
        for (ArmorStand stand : stands) {  
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);  
            CorpseType type = classify(head);  
            if (type == null) continue;  
  
            int x = (int) Math.floor(stand.getX());  
            int y = (int) Math.floor(stand.getY());  
            int z = (int) Math.floor(stand.getZ());  
            if (DEBUG) debugLog(head, type, x, y, z);  
            if (alreadyAdded(found, x, y, z)) continue;  
  
            Waypoint wp = new Waypoint(type.name(), x, y, z, "Corpse Finder", "Mineshaft");  
            wp.enabled = true;  
            wp.color = type.boxColor();  
            found.add(wp);  
        }  
        detected = found;  
  
        // 2) Checkpoint flow over the bundled candidate waypoints for this shaft.  
        List<Waypoint> candidates = new ArrayList<>();  
        for (Waypoint c : ShaftHelperClient.corpseWaypoints()) {  
            if (!WaypointRenderer.groupMatchesShaft(c.group, shaftCode)) continue;  
            if (clearedCandidates.contains(key(c.x, c.y, c.z))) continue;  
            candidates.add(c);  
        }  
  
        // Pick nearest un-cleared candidate.  
        Waypoint nearest = null;  
        double best = Double.MAX_VALUE;  
        for (Waypoint c : candidates) {  
            double d = c.distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ());  
            if (d < best) { best = d; nearest = c; }  
        }  
        activeCandidate = nearest;  
  
        // If we're close to the active candidate, resolve it.  
        if (nearest != null && best <= CHECK_RADIUS) {  
            boolean realCorpseHere = false;  
            for (Waypoint real : found) {  
                if (real.distanceTo(nearest.x, nearest.y, nearest.z) <= CORPSE_MATCH_RADIUS) {  
                    realCorpseHere = true;  
                    break;  
                }  
            }  
            // Either way the candidate is now resolved -> check it off and move on.  
            clearedCandidates.add(key(nearest.x, nearest.y, nearest.z));  
            if (DEBUG) LOGGER.info("[CorpseFinder] candidate {} {},{},{} -> {}",  
                nearest.name, nearest.x, nearest.y, nearest.z,  
                realCorpseHere ? "CORPSE FOUND" : "empty, skipping");  
            activeCandidate = null; // recomputed next tick from remaining candidates  
        }  
    }  

    private static volatile boolean allFound = false;  
    private static int foundCorpseCount = 0;  
    
    public static boolean allCorpsesFound() { return allFound; }  
    public static boolean isCandidateCleared(int x, int y, int z) {  
        return clearedCandidates.contains(key(x, y, z));  
    }
  
    private static void reset() {  
        detected = List.of();  
        activeCandidate = null;  
        allFound = false;
        foundCorpseCount = 0;
    }  
  
    /** Maps a corpse helmet item to its corpse type, or null if not a corpse helmet. */  
    private static CorpseType classify(ItemStack head) {  
        if (head == null || head.isEmpty()) return null;  
  
        String id = skyblockId(head);  
        if (id != null) {  
            String upper = id.toUpperCase(Locale.ROOT);  
            if (upper.contains("MINERAL"))  return TUNGSTEN;  
            if (upper.contains("LAPIS"))    return LAPIS;  
            if (upper.contains("UMBER"))    return UMBER;  
            if (upper.contains("VANGUARD")) return VANGUARD;  
        }  
        if (head.is(Items.SEA_LANTERN)) return LAPIS;  
        if (head.is(Items.LEATHER_HELMET) && dyedColor(head) != null) return UMBER;  
        return null;  
    }  
  
    private static String skyblockId(ItemStack stack) {  
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);  
        if (data == null) return null;  
        CompoundTag tag = data.copyTag();  
        // NOTE: on MC 26.x mappings CompoundTag#getString returns Optional<String>.  
        // If this does not compile, use: String id = tag.getString("id").orElse("");  
        String id = tag.getString("id").orElse("");  
        return (id == null || id.isEmpty()) ? null : id;  
    }  
  
    private static Integer dyedColor(ItemStack stack) {  
        if (stack == null || stack.isEmpty()) return null;  
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);  
        return dyed == null ? null : (dyed.rgb() & 0xFFFFFF);  
    }  
  
    private static void debugLog(ItemStack head, CorpseType type, int x, int y, int z) {  
        long k = (((long) x) << 42) ^ (((long) y) << 21) ^ (z & 0x1FFFFFL);  
        if (!loggedPositions.add(k)) return;  
        Integer color = dyedColor(head);  
        LOGGER.info("[CorpseFinder] {} at {},{},{} | item={} | id={} | color={}",  
            type.name(), x, y, z, head.getItem(), skyblockId(head),  
            color == null ? "none" : String.format("#%06X", color));  
    }  
  
    private static boolean alreadyAdded(List<Waypoint> list, int x, int y, int z) {  
        for (Waypoint wp : list) {  
            if (Math.abs(wp.x - x) <= 1 && Math.abs(wp.y - y) <= 1 && Math.abs(wp.z - z) <= 1) return true;  
        }  
        return false;  
    }  
  
    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }  
  
    private CorpseFinder() {}  
}