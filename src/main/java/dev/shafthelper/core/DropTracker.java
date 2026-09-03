package dev.shafthelper.core;  
  
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;  
import java.util.regex.Pattern;  
  
public final class DropTracker {  
  
    // "RARE DROP! Littlefoot Fluff" / "PET DROP! ..." etc.  
    private static final Pattern RARE_DROP = Pattern.compile(  
        "(?i)(?:RARE|PET|CRAZY RARE) DROP!\\s*(?:\\([^)]*\\)\\s*)?(.+?)(?:\\s*\\([^)]*\\))?$");  
  
    // Sack pickup lines, e.g. "+64 Glacite (Glacite Sack)" or "+12 Refined Mineral"  
    private static final Pattern SACK_GAIN = Pattern.compile(  
        "(?i)\\+\\s*([\\d,]+)\\s*(.+?)(?:\\s*\\(.*\\))?$");  
  
    private static final String[] FIESTA_ITEMS = { "Refined Mineral", "Glossy Gemstone" };  
  
    private final Map<String, Long> rareDrops = new LinkedHashMap<>();  
    private final Map<String, Long> fiesta = new LinkedHashMap<>();  
    private final Map<String, Long> sacks = new LinkedHashMap<>();  
  
    public synchronized boolean record(String message) {  
        if (message == null) return false;  
        String clean = message.replaceAll("\u00a7.", "").trim();  
  
        Matcher rare = RARE_DROP.matcher(clean);  
        if (rare.find()) {  
            String item = rare.group(1).trim();  
            if (!item.isEmpty()) {  
                rareDrops.merge(item, 1L, Long::sum);  
                return true;  
            }  
        }  
  
        Matcher gain = SACK_GAIN.matcher(clean);  
        if (gain.find()) {  
            long amount = Long.parseLong(gain.group(1).replace(",", ""));  
            String item = gain.group(2).trim();  
            if (item.isEmpty()) return false;  
            for (String f : FIESTA_ITEMS) {  
                if (item.equalsIgnoreCase(f)) {  
                    fiesta.merge(f, amount, Long::sum);  
                    return true;  
                }  
            }  
            sacks.merge(item, amount, Long::sum);  
            return true;  
        }  
  
        return false;  
    }  
  
    public synchronized Map<String, Long> rareDrops() { return new LinkedHashMap<>(rareDrops); }  
    public synchronized Map<String, Long> fiesta() { return new LinkedHashMap<>(fiesta); }  
    public synchronized Map<String, Long> sacks() { return new LinkedHashMap<>(sacks); }  
  
    public synchronized boolean isEmpty() {  
        return rareDrops.isEmpty() && fiesta.isEmpty() && sacks.isEmpty();  
    }  
  
    /** Counts persist across lobby moves, so this is intentionally a no-op. */  
    public synchronized void resetSession() { }  
  
    public synchronized void resetAll() {  
        rareDrops.clear();  
        fiesta.clear();  
        sacks.clear();  
    }  
}