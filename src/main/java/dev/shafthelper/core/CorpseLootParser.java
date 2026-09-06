package dev.shafthelper.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;

/**
 * Parses Hypixel frozen corpse loot messages.
 *
 * Supported corpse types:
 * Lapis, Umber, Tungsten, Vanguard.
 *
 * Example:
 * "VANGUARD CORPSE LOOT!"
 * "REWARDS"
 * "Refined Mithril x2"
 * "Umber Plate"
 * "Glacite Powder +47,341"
 */
public final class CorpseLootParser {

    private static final Pattern CORPSE_HEADER = Pattern.compile(
        "(?i)\\b(LAPIS|UMBER|TUNGSTEN|VANGUARD)\\s+CORPSE\\s+LOOT!?\\b"
    );

    /*
     * Handles:
     * Refined Mithril x2
     * Refined Umber x2
     * Glacite Powder +47,341
     * Glacite Powder: +47,341
     * Umber Plate
     */
    private static final Pattern REWARD_LINE = Pattern.compile(
        "^\\s*(.+?)\\s*(?::\\s*)?(?:\\+\\s*([\\d,]+)|x\\s*([\\d,]+))?\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    public enum CorpseType {
        LAPIS,
        UMBER,
        TUNGSTEN,
        VANGUARD
    }

    public record Loot(
        CorpseType type,
        Map<String, Long> rewards
    ) {}

    /**
     * Parse a corpse-loot chat message.
     * @return parsed loot, or null if the message isn't corpse loot.
     */
    public static Loot parse(Component message) {
        if (message == null) return null;
        return parse(message.getString());
    }

    /**
     * Parse a corpse-loot message from plain text.
     */
    public static Loot parse(String message) {
        if (message == null || message.isBlank()) return null;

        Matcher header = CORPSE_HEADER.matcher(message);

        if (!header.find()) {
            return null;
        }

        CorpseType type;

        try {
            type = CorpseType.valueOf(
                header.group(1).toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }

        Map<String, Long> rewards = new LinkedHashMap<>();

        /*
         * Only parse text after the corpse header.
         * This prevents unrelated chat text from being interpreted
         * as rewards.
         */
        String rewardText = message.substring(header.end());

        for (String raw : rewardText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Don't treat the section header as an item.
            if (line.equalsIgnoreCase("REWARDS")) continue;

            // Remove Minecraft formatting codes if they somehow remain.
            line = line.replaceAll("\\u00a7.", "").trim();
            if (line.isEmpty()) continue;

            Matcher reward = REWARD_LINE.matcher(line);
            if (!reward.matches()) continue;
            
            String name = reward.group(1).trim();
            if (name.isEmpty()) continue;

            if (isIgnoredReward(name)) continue;

            String plusAmount = reward.group(2);
            String stackAmount = reward.group(3);

            long amount = 1;
            String amountText = plusAmount != null ? plusAmount : stackAmount;

            if (amountText != null) {
                try {
                    amount = Long.parseLong(amountText.replace(",", ""));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }

            if (amount <= 0) continue;

            rewards.merge(name, amount, Long::sum);
        }

        return new Loot(type, rewards);
    }

    private static boolean isIgnoredReward(String name) {
        return name.equalsIgnoreCase("Glacite Powder")
            || name.equalsIgnoreCase("Gemstone Powder");
    }

    private CorpseLootParser() {}
}