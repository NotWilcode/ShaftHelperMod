package dev.shafthelper.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class PlayerTimers implements HudElement {
    private static final long DEPLOYABLE_DURATION_MS = 5L * 60L * 1000L;
    private static final Pattern FORMATTING = Pattern.compile("\u00a7.");
    private static final String[] BUFF_ORDER = {
        "Cold Resistance",
        "Filet O' Fortune",
        "Pristine Potato"
    };

    private static volatile long deployableUntil = 0L;
    private static final Map<String, Long> activeBuffs = new HashMap<>();
    private static boolean initialized = false;
    private static boolean lastRightClickHeld = false;

    public static void register() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(PlayerTimers::onClientTick);
        ClientReceiveMessageEvents.GAME.register(PlayerTimers::onChatMessage);
    }

    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay || message == null) return;

        String text = stripFormatting(message.getString());
        if (text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String effect : BUFF_ORDER) {
            String effectLower = effect.toLowerCase(Locale.ROOT);
            if (!lower.contains(effectLower)) continue;
            Long parsed = parseMessageEffectDuration(text, effect);
            if (parsed != null && parsed > 0L) {
                activeBuffs.put(effect, parsed);
            }
        }
    }

    private static Long parseMessageEffectDuration(String text, String effectName) {
        String lower = text.toLowerCase(Locale.ROOT);
        String target = effectName.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(target);
        if (idx < 0) return null;

        String remainder = text.substring(idx + effectName.length());
        Matcher matcher = Pattern.compile("(?:for\\s+)?(\\d+\\s*(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)|\\d+\\s*:\\s*\\d+|\\d+\\s*(?:m|min|mins|minute|minutes)\\s*\\d+\\s*(?:s|sec|secs|second|seconds))").matcher(remainder);
        if (!matcher.find()) return null;
        return parseDuration(matcher.group(1));
    }

    private static void onClientTick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (deployableUntil != 0L && now >= deployableUntil) {
            deployableUntil = 0L;
        }

        if (client == null || client.player == null) {
            activeBuffs.clear();
            lastRightClickHeld = false;
            return;
        }

        boolean rightClickHeld = client.options != null && client.options.keyUse.isDown();
        if (rightClickHeld && !lastRightClickHeld && holdingDeployable(client.player)) {
            deployableUntil = now + DEPLOYABLE_DURATION_MS;
        }
        lastRightClickHeld = rightClickHeld;

        updateBuffTimers(client);
    }

    private static boolean holdingDeployable(Player player) {
        if (player == null) return false;
        String handName = normalizeItemName(player.getMainHandItem());
        if (handName.isEmpty()) {
            handName = normalizeItemName(player.getOffhandItem());
        }
        return isDeployableItem(handName);
    }

    private static boolean isDeployableItem(String itemName) {  
        if (itemName == null || itemName.isEmpty()) return false;  
        return itemName.contains("lantern") || itemName.contains("will-o'-wisp");  
    }

    private static String normalizeItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return normalizeItemName(stack.getHoverName().getString());
    }

    private static String normalizeItemName(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("’", "'").replace("\u00a7", "").trim();
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static String stripFormatting(String raw) {
        return raw == null ? "" : FORMATTING.matcher(raw).replaceAll("");
    }

    private static void updateBuffTimers(Minecraft client) {
        activeBuffs.clear();
        List<String> effectLines = collectEffectsLines(client);
        for (String effect : BUFF_ORDER) {
            Long remaining = parseEffectTime(effectLines, effect);
            if (remaining != null && remaining > 0L) {
                activeBuffs.put(effect, remaining);
            }
        }
    }

    private static List<String> collectEffectsLines(Minecraft client) {
        List<String> result = new ArrayList<>();
        if (client == null) return result;

        for (String raw : collectTabListLines(client)) {
            String line = stripFormatting(raw).trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("cold resistance")
                || lower.contains("filet o' fortune")
                || lower.contains("pristine potato")) {
                result.add(line);
            }
        }

        return result;
    }

    static Long parseEffectTime(List<String> lines, String effectName) {
        if (lines == null || lines.isEmpty()) return null;
        String lowerName = effectName.toLowerCase(Locale.ROOT);
        for (String line : lines) {
            String normalized = stripFormatting(line).trim();
            if (normalized.isEmpty()) continue;

            String lower = normalized.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(lowerName);
            if (idx < 0) continue;

            String remainder = normalized.substring(idx + effectName.length());
            String cleaned = remainder.replaceFirst("^(?:\\s+[IVXLCDM]+)?\\s*:?\\s*", "");
            int end = cleaned.indexOf(',');
            if (end >= 0) {
                cleaned = cleaned.substring(0, end);
            }
            Long value = parseDuration(cleaned);
            if (value != null) return value;
        }
        return null;
    }

    static Long parseDuration(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        Matcher clock = Pattern.compile("^(?:for\\s+)?(\\d+)\\s*:\\s*(\\d+)$").matcher(trimmed);
        if (clock.matches()) {
            int minutes = Integer.parseInt(clock.group(1));
            int seconds = Integer.parseInt(clock.group(2));
            return ((long) minutes * 60L + seconds) * 1000L;
        }

        Matcher complex = Pattern.compile("^(?:for\\s+)?(\\d+)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)$").matcher(trimmed);
        if (complex.matches()) {
            long amount = Long.parseLong(complex.group(1));
            String unit = complex.group(2);
            return switch (unit) {
                case "h", "hr", "hrs", "hour", "hours" -> amount * 3_600_000L;
                case "m", "min", "mins", "minute", "minutes" -> amount * 60_000L;
                case "s", "sec", "secs", "second", "seconds" -> amount * 1000L;
                default -> null;
            };
        }

        Matcher mixed = Pattern.compile("^(?:for\\s+)?(\\d+)\\s*(m|min|mins|minute|minutes)\\s*(\\d+)\\s*(s|sec|secs|second|seconds)$").matcher(trimmed);
        if (mixed.matches()) {
            long minutes = Long.parseLong(mixed.group(1));
            long seconds = Long.parseLong(mixed.group(3));
            return (minutes * 60L + seconds) * 1000L;
        }

        Matcher mixedHours = Pattern.compile("^(?:for\\s+)?(\\d+)\\s*(h|hr|hrs|hour|hours)\\s*(\\d+)\\s*(m|min|mins|minute|minutes)$").matcher(trimmed);
        if (mixedHours.matches()) {
            long hours = Long.parseLong(mixedHours.group(1));
            long minutes = Long.parseLong(mixedHours.group(3));
            return (hours * 60L + minutes) * 60L * 1000L;
        }

        return null;
    }

    private static List<String> collectTabListLines(Minecraft client) {
        List<String> lines = new ArrayList<>();
        if (client == null) return lines;

        Gui gui = client.gui;
        if (gui != null) {
            PlayerTabOverlay tabList = gui.getTabList();
            if (tabList != null) {
                for (String fieldName : List.of("header", "footer")) {
                    try {
                        Field field = PlayerTabOverlay.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(tabList);
                        if (value instanceof Component component) {
                            String text = stripFormatting(component.getString());
                            if (!text.isBlank()) {
                                lines.add(text);
                            }
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Some versions may not expose the field; fall back to the scoreboard/player scan below.
                    }
                }
            }
        }

        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getListedOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                lines.add(name != null ? name.getString() : info.getProfile().name());
            }
        }

        if (client.level == null) return lines;
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                String owner = entry.owner();
                PlayerTeam team = scoreboard.getPlayersTeam(owner);
                String full = PlayerTeam.formatNameForTeam(team, Component.literal(owner)).getString();
                lines.add(full);
            }
        }
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            lines.add(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
        }

        return lines;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getDebugOverlay().showDebugScreen()) return;

        ModConfig config = ShaftTracker.config();
        if (config == null) return;

        List<String> lines = new ArrayList<>();
        if (config.miningBuffTimerEnabled) {
            for (String buff : BUFF_ORDER) {
                Long remaining = activeBuffs.get(buff);
                if (remaining != null && remaining > 0L) {
                    lines.add(buff + ": " + formatDuration(remaining));
                }
            }
        }

        if (config.miningDeployableTimerEnabled) {
            long remaining = deployableUntil - System.currentTimeMillis();
            if (remaining > 0L) {
                lines.add("Deployable: " + formatDuration(remaining));
            }
        }

        if (lines.isEmpty()) return;

        Font font = client.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        int paddedWidth = width + 12;
        int lineHeight = 10;
        int height = lines.size() * lineHeight + 12;
        int x = position(config.playerTimersX, graphics.guiWidth(), paddedWidth);
        int y = position(config.playerTimersY, graphics.guiHeight(), height);

        graphics.fill(x, y, x + paddedWidth, y + height, 0xE00D1B2A);
        graphics.fill(x, y, x + paddedWidth, y + 1, 0xFF1B263B);
        graphics.fill(x, y + height - 1, x + paddedWidth, y + height, 0xFF1B263B);
        graphics.fill(x, y, x + 1, y + height, 0xFF1B263B);
        graphics.fill(x + paddedWidth - 1, y, x + paddedWidth, y + height, 0xFF1B263B);

        int drawY = y + 5;
        for (String line : lines) {
            graphics.text(font, Component.literal(line), x + 6, drawY, 0xFFE0E1DD, true);
            drawY += lineHeight;
        }
    }

    private static int position(double percent, int screen, int size) {
        int available = Math.max(0, screen - size - 8);
        return 4 + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) {
            return seconds > 0L ? minutes + "m " + seconds + "s" : minutes + "m";
        }
        return seconds > 0L ? seconds + "s" : "0s";
    }
}
