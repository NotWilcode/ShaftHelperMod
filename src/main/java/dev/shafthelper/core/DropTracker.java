package dev.shafthelper.core;  
  
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;  
import java.util.regex.Pattern;  
  
/** Tracks items gained via the [Sacks] chat notification (parsed from its hover tooltip). */  
public final class DropTracker {  
  
    // Item display-name -> count collected this session.  
    private final Map<String, Long> rareDrops = new LinkedHashMap<>();  
    private final Map<String, Long> fiesta    = new LinkedHashMap<>();  
    private final Map<String, Long> sacks     = new LinkedHashMap<>();  
  
    /** Getters used by ShaftTracker.dropTrackerLines(). */  
    public synchronized Map<String, Long> rareDrops() { return new LinkedHashMap<>(rareDrops); }  
    public synchronized Map<String, Long> fiesta()    { return new LinkedHashMap<>(fiesta); }  
    public synchronized Map<String, Long> sacks()     { return new LinkedHashMap<>(sacks); }  

    /** Matches a hover line like "Glacite: +150" or "Refined Mineral +3" or "+150 Glacite". */  
    private static final Pattern SACK_LINE = Pattern.compile(  
        "(?i)^(?:\\+?\\s*([\\d,]+)\\s+)?([A-Za-z' ]+?)\\s*[: ]?\\s*\\+?\\s*([\\d,]+)?\\s*$");  
  
    /** Display name (lowercased) -> Bazaar product id, for price lookups. */  
    private static final Map<String, String> ITEM_IDS = Map.of(  
        "glacite", "GLACITE_JEWEL",  
        "hard stone", "HARD_STONE",  
        "refined mineral", "REFINED_MINERAL",  
        "glossy gemstone", "GLOSSY_GEMSTONE"  
    );  
  
    /** All bazaar ids the price fetch must include so values resolve. */  
    public static final java.util.List<String> PRODUCT_IDS =  
        java.util.List.copyOf(new java.util.HashSet<>(ITEM_IDS.values()));  
  
    /** name -> total count gained this session. Insertion order preserved for the HUD. */  
    private final Map<String, Long> counts = new LinkedHashMap<>();  
  
    /**  
     * Records the parsed hover text of a [Sacks] message.  
     * @param hoverText the full tooltip text (may contain multiple lines separated by \n)  
     * @return true if at least one item was recorded  
     */  
    public synchronized boolean recordSackHover(String hoverText) {  
        if (hoverText == null || hoverText.isBlank()) return false;  
        boolean any = false;  
        for (String raw : hoverText.split("\n")) {  
            String line = raw.replaceAll("\u00a7.", "").trim();  
            if (line.isEmpty()) continue;  
            // Skip non-item lines like headers/footers.  
            if (line.toLowerCase(Locale.ROOT).contains("sack") && !line.contains("+")) continue;  
            Matcher m = SACK_LINE.matcher(line);  
            if (!m.matches()) continue;  
            String pre = m.group(1);  
            String name = m.group(2) == null ? "" : m.group(2).trim();  
            String post = m.group(3);  
            String num = post != null ? post : pre;  
            if (name.isEmpty() || num == null) continue;  
            long amount;  
            try {  
                amount = Long.parseLong(num.replace(",", ""));  
            } catch (NumberFormatException e) {  
                continue;  
            }  
            if (amount <= 0) continue;  
            counts.merge(name, amount, Long::sum);  
            any = true;  
        }  
        return any;  
    }  
  
    /** Bazaar id for a tracked display name, or null if we don't price it. */  
    public static String idFor(String displayName) {  
        return ITEM_IDS.get(displayName.toLowerCase(Locale.ROOT));  
    }  
  
    public synchronized Map<String, Long> snapshot() {  
        return new LinkedHashMap<>(counts);  
    }  
  
    public synchronized boolean isEmpty() {  
        return rareDrops.isEmpty() && fiesta.isEmpty() && sacks.isEmpty();  
    }  
  
    public synchronized void resetSession() {  
        counts.clear();  
        fiesta.clear();  
        sacks.clear();  
    }  
}