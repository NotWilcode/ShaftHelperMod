package dev.shafthelper.core;  
  
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<String, Long> corpseDrops = new LinkedHashMap<>();

    /** Getters used by ShaftTracker.dropTrackerLines(). */  
    public synchronized Map<String, Long> rareDrops() { return new LinkedHashMap<>(rareDrops); }  
    public synchronized Map<String, Long> fiesta()    { return new LinkedHashMap<>(fiesta); }  
    public synchronized Map<String, Long> sacks()     { return new LinkedHashMap<>(sacks); }  
    public synchronized Map<String, Long> corpseDrops() { return new LinkedHashMap<>(corpseDrops); }

    /** Matches a hover line "
     * +196 Glacite (Mining Sack)
     * +138 Hard Stone (Mining Sack)
     * +268 Flawed Sapphire Gemstone (Gemstones Sack)
     * +978 Rough Sapphire Gemstone (Gemstones Sack)
     *  */  
    private static final Pattern SACK_LINE = Pattern.compile(
        "(?i)^\\+?\\s*([\\d,]+)\\s+(.+?)(?:\\s+\\([^)]*Sack\\))?\\s*$"
    );
  
    /** Display name (lowercased) -> Bazaar product id, for price lookups. */  
    private static final Map<String, String> ITEM_IDS = Map.of(  
        "glacite", "GLACITE",  
        "enchanted glacite", "ENCHANTED_GLACITE",  
        "hard stone", "HARD_STONE", 
        "enchanted hard stone", "ENCHANTED_HARD_STONE",
        "refined mineral", "REFINED_MINERAL",  
        "glossy gemstone", "GLOSSY_GEMSTONE"  
    );

    private static final Map<String, String> CORPSE_ITEM_IDS = Map.ofEntries(
        Map.entry("umber key", "UMBER_KEY"),
        Map.entry("tungsten key", "TUNGSTEN_KEY"),
        Map.entry("skeleton key", "SKELETON_KEY"),

        Map.entry("refined mithril", "REFINED_MITHRIL"),
        Map.entry("refined titanium", "REFINED_TITANIUM"),
        Map.entry("refined umber", "REFINED_UMBER"),
        Map.entry("refined tungsten", "REFINED_TUNGSTEN"),

        Map.entry("umber plate", "UMBER_PLATE"),
        Map.entry("tungsten plate", "TUNGSTEN_PLATE"),

        Map.entry("glacite amalgamation", "GLACITE_AMALGAMATION"),
        Map.entry("glacite jewel", "GLACITE_JEWEL"),
        Map.entry("bejeweled handle", "BEJEWELED_HANDLE"),

        Map.entry("shattered locket", "SHATTERED_LOCKET"),
        Map.entry("suspicious scrap", "SUSPICIOUS_SCRAP"),

        Map.entry("mithril plate", "MITHRIL_PLATE"),
        Map.entry("dwarven o's metallic minis", "DWARVEN_OS_METALLIC_MINIS")
    );

    public static final List<String> CORPSE_PRODUCT_IDS = List.copyOf(CORPSE_ITEM_IDS.values());
  
    /** All bazaar ids the price fetch must include so values resolve. */  
    public static final List<String> PRODUCT_IDS =
        java.util.stream.Stream.concat(
            ITEM_IDS.values().stream(),
            CORPSE_ITEM_IDS.values().stream()
        ).distinct().toList();
  
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

            Matcher m = SACK_LINE.matcher(line);
            if (!m.matches()) continue;

            String num = m.group(1);
            String name = m.group(2).trim();

            long amount;
            try {
                amount = Long.parseLong(num.replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }

            if (amount <= 0 || name.isEmpty()) continue;

            sacks.merge(name, amount, Long::sum);
            any = true;
        }
        return any;  
    }  

    public synchronized boolean recordCorpseLoot(CorpseLootParser.Loot loot) {
        if (loot == null || loot.rewards().isEmpty()) return false;

        boolean any = false;

        for (Map.Entry<String, Long> entry : loot.rewards().entrySet()) {
            String name = entry.getKey();
            long amount = entry.getValue();

            if (amount <= 0) continue;

            // Powder is not an item we want to track.
            if (name.equalsIgnoreCase("Glacite Powder")
                || name.equalsIgnoreCase("Gemstone Powder")) {
                continue;
            }

            corpseDrops.merge(name, amount, Long::sum);
            any = true;
        }

        return any;
    }
  
    public static String idFor(String displayName) {
        String key = displayName.toLowerCase(Locale.ROOT).trim();

        String sackId = ITEM_IDS.get(key);
        if (sackId != null) {
            return sackId;
        }

        return CORPSE_ITEM_IDS.get(key);
    }  
  
    public synchronized Map<String, Long> snapshot() {  
        return new LinkedHashMap<>(sacks);   // <-- was counts  
    }  
  
    public synchronized boolean isEmpty() {  
        return rareDrops.isEmpty() && fiesta.isEmpty() && sacks.isEmpty();  
    }  
  
    public synchronized void resetSession() {  
        rareDrops.clear();  
        fiesta.clear();  
        sacks.clear();  
        corpseDrops.clear();
    }
}