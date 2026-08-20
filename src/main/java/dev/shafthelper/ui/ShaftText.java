package dev.shafthelper.ui;

import dev.shafthelper.core.Cold;
import dev.shafthelper.core.Format;
import dev.shafthelper.core.Gemstones;
import dev.shafthelper.core.Mining;
import dev.shafthelper.core.Mining.Breakdown;
import dev.shafthelper.core.Mining.Ranked;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.Pristine;
import dev.shafthelper.core.Pristine.CorpseRow;
import dev.shafthelper.core.ShaftOptions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Renders the calculator results as chat lines, the mod's stand-in for the bot's embeds. */
public final class ShaftText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private static MutableComponent gemName(dev.shafthelper.core.Gemstone gem) {
        return Component.literal(gem.name())
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(gem.color())));
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static String pricedAt(Long lastUpdated) {
        return lastUpdated == null ? "live" : TIME.format(Instant.ofEpochMilli(lastUpdated)) + " UTC";
    }

    /** Drops are only ever worth what they craft into, so every result says which side it priced. */
    private static String basisFooter(Prices.Basis basis) {
        return basis == Prices.Basis.LISTED
            ? "Priced from rough/flawed listings"
            : "Priced from Flawless (" + String.format(Locale.ROOT, "%,d", Gemstones.ROUGH_PER_FLAWLESS) + " rough = 1 Flawless)";
    }

    private static String priceSummary(ShaftOptions options) {
        return Prices.modeLabel(options.priceMode) + ", " + Prices.dataLabel(options.priceData)
            + ", " + Prices.basisLabel(options.priceBasis);
    }

    private static void addAdjustments(List<Component> lines, List<Prices.Adjustment> adjustments) {
        if (adjustments.isEmpty()) return;
        lines.add(Component.literal("Listings ignored as inflated").withStyle(ChatFormatting.YELLOW));
        List<Prices.Adjustment> worst = adjustments.stream()
            .sorted(Comparator.comparingDouble(adjustment -> adjustment.used() / adjustment.live()))
            .toList();
        int limit = Math.min(5, worst.size());
        for (Prices.Adjustment adjustment : worst.subList(0, limit)) {
            String tier = adjustment.id().startsWith("ROUGH_") ? "rough" : "flawed";
            long cut = Math.round((1 - adjustment.used() / adjustment.live()) * 100);
            lines.add(Component.literal("  ")
                .append(gemName(adjustment.gem()))
                .append(gray(" " + tier + " " + Format.compact(adjustment.live()) + " -> "
                    + Format.compact(adjustment.used()) + " (-" + cut + "%)")));
        }
        int rest = worst.size() - limit;
        if (rest > 0) lines.add(gray("  ...and " + rest + " smaller trim" + (rest == 1 ? "" : "s")));
    }

    /** Breaking times only, shown when no price source is reachable. */
    public static List<Component> noPrices(ShaftOptions options) {
        List<Breakdown> breakdown = Mining.calculateBreakdown(options.miningSpeed, options.fortune, options.gemstoneFortune, options.gemstoneSpread);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("\u26A0 Prices unavailable — "
                + String.format(Locale.ROOT, "%,d", options.miningSpeed) + " Mining Speed")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        lines.add(gray("Could not reach the Bazaar, so only breaking times are shown."));
        breakdown.stream().sorted(Comparator.comparingInt(Breakdown::ticks)).forEach(gem ->
            lines.add(Component.literal("  ")
                .append(gemName(gem.gem()))
                .append(gray(" — " + gem.ticks() + " ticks (" + String.format(Locale.ROOT, "%.2f", gem.seconds())
                    + "s) · " + Format.compact(gem.gemsPerHour()) + " gems/hr"))));
        return lines;
    }

    /** Default mode: every gemstone against the benchmark, with the corpses each shaft needs. */
    public static List<Component> overview(ShaftOptions options, Prices.Result result) {
        Map<String, Double> prices = result.prices();
        List<Breakdown> breakdown = Mining.calculateBreakdown(options.miningSpeed, options.fortune, options.gemstoneFortune, options.gemstoneSpread);
        List<Ranked> ranked = Mining.rankByProfit(breakdown, prices, options.pristine);
        Ranked reference = ranked.stream()
            .filter(gem -> gem.name().equals(options.benchmark))
            .findFirst()
            .orElse(ranked.getFirst());
        List<CorpseRow> rows = Pristine.corpseTable(ranked, prices, reference, options.pristine,
                Pristine.MAX_LAPIS_CORPSES).stream()
            .sorted(Comparator.comparingInt((CorpseRow row) -> row.status().ordinal())
                .thenComparingDouble(CorpseRow::bonus)
                .thenComparing(Comparator.comparingDouble(CorpseRow::coinsWithCorpses).reversed()))
            .toList();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("\u26CF Shaft corpses vs " + reference.name() + " — "
                + String.format(Locale.ROOT, "%,d", options.miningSpeed) + " Mining Speed")
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        lines.add(gray("Gems: " + Gemstones.MIN_DROPS + "-" + Gemstones.MAX_DROPS + " x "
            + options.fortune + " Mining Fortune = "
            + String.format(Locale.ROOT, "%.2f", breakdown.getFirst().dropsPerBlock()) + " gems/block"));
        lines.add(gray("Base Pristine: " + trim(options.pristine) + " · Prices: " + priceSummary(options)
            + " · Each lapis corpse = +1 Pristine"));
        lines.add(gray("Coins/hr with the corpses each shaft needs, and the corpses to beat " + reference.name() + ":"));

        for (CorpseRow row : rows) {
            MutableComponent line = Component.literal("  ").append(gemName(row.ranked().gem()));
            line.append(gray(" — " + row.ranked().ticks() + "t · "
                + Format.compact(row.coinsWithCorpses()) + "/hr · "));
            String pristine = Format.formatBonusPristine(row);
            String corpses = Format.formatCorpses(row, Pristine.MAX_LAPIS_CORPSES);
            ChatFormatting color = switch (row.status()) {
                case REFERENCE -> ChatFormatting.AQUA;
                case AHEAD, REACHABLE -> ChatFormatting.GREEN;
                case OUT_OF_REACH, IMPOSSIBLE -> ChatFormatting.RED;
            };
            line.append(Component.literal(row.status() == Pristine.Status.REFERENCE
                    ? "benchmark"
                    : pristine + " Pristine -> " + corpses + " corpses")
                .withStyle(color));
            lines.add(line);
        }

        lines.add(Component.literal("Mine the shaft if it spawned...").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        List<Component> verdict = verdictLines(rows);
        if (verdict.isEmpty()) {
            lines.add(gray("  Nothing beats the benchmark shaft, whatever spawns in it."));
        } else {
            lines.addAll(verdict);
        }

        addAdjustments(lines, result.adjustments());
        lines.add(gray(reference.name() + " at " + trim(options.pristine) + " Pristine = "
            + Format.compact(reference.coinsPerHour()) + " coins/hr · " + basisFooter(options.priceBasis)
            + " · " + pricedAt(result.lastUpdated()) + " · max " + Pristine.MAX_LAPIS_CORPSES + " corpses per shaft"));
        return lines;
    }

    private static List<Component> verdictLines(List<CorpseRow> rows) {
        List<Component> lines = new ArrayList<>();
        for (int corpses = 0; corpses <= Pristine.MAX_LAPIS_CORPSES; corpses += 1) {
            int count = corpses;
            List<CorpseRow> gems = rows.stream()
                .filter(row -> Format.countsTowardVerdict(row) && row.corpses() == count)
                .toList();
            if (gems.isEmpty()) continue;
            String label = corpses == 0 ? "no corpses" : corpses + " corpse" + (corpses == 1 ? "" : "s");
            MutableComponent line = Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY);
            for (int i = 0; i < gems.size(); i += 1) {
                if (i > 0) line.append(gray(", "));
                line.append(gemName(gems.get(i).ranked().gem()));
            }
            lines.add(line);
        }
        return lines;
    }

    /** type: mode — what the one shaft in front of you is worth before Cold throws you out. */
    public static List<Component> shaftRun(ShaftOptions options, Prices.Result result) {
        Map<String, Double> prices = result.prices();
        List<Breakdown> breakdown = Mining.calculateBreakdown(options.miningSpeed, options.fortune, options.gemstoneFortune, options.gemstoneSpread);
        double shaftPristine = options.pristine + options.lapis;
        Ranked gem = Mining.rankByProfit(breakdown, prices, shaftPristine).stream()
            .filter(row -> row.name().equals(options.type)).findFirst().orElseThrow();
        Ranked reference = Mining.rankByProfit(breakdown, prices, options.pristine).stream()
            .filter(row -> row.name().equals(options.benchmark)).findFirst().orElseThrow();

        Cold.ShaftRun run = Cold.shaftProfit(gem.coinsPerHour(), options.coldRes, options.efficiency);
        Cold.ShaftRun referenceRun = Cold.shaftProfit(reference.coinsPerHour(), options.coldRes, options.efficiency);
        boolean worthIt = run.coins() >= referenceRun.coins();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("\u26CF " + gem.name() + " shaft with " + options.lapis
                + " lapis corpse" + (options.lapis == 1 ? "" : "s"))
            .withStyle(worthIt ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        lines.add(gray("Pristine: " + trim(options.pristine) + " base + " + options.lapis + " corpse"
            + (options.lapis == 1 ? "" : "s") + " = " + trim(shaftPristine) + " in here"));
        lines.add(gray("Gems: " + Gemstones.MIN_DROPS + "-" + Gemstones.MAX_DROPS + " x " + options.fortune
            + " Mining Fortune = " + String.format(Locale.ROOT, "%.2f", gem.breakdown().dropsPerBlock())
            + " gems/block · Prices: " + priceSummary(options)));
        lines.add(gray("Mining: " + gem.ticks() + " ticks per block ("
            + String.format(Locale.ROOT, "%.2f", gem.breakdown().seconds()) + "s) · "
            + Format.compact(gem.breakdown().gemsPerHour()) + " gems/hr -> "
            + Format.compact(gem.coinsPerHour()) + " coins/hr non-stop"));
        lines.add(gray("Time in shaft: " + String.format(Locale.ROOT, "%.1f", Cold.secondsPerCold(options.coldRes))
            + "s per Cold at " + trim(options.coldRes) + " res · "
            + String.format(Locale.ROOT, "%.1f", run.minutes()) + " min until 100 Cold, "
            + trim(options.efficiency) + "% of it mining"));
        lines.add(Component.literal("Estimated profit for this shaft: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal(Format.compact(run.coins()) + " coins")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        MutableComponent verdict;
        if (gem.name().equals(reference.name())) {
            verdict = Component.literal("It is the benchmark shaft — always worth mining.")
                .withStyle(ChatFormatting.AQUA);
        } else {
            verdict = Component.literal(worthIt ? "\u2714 Mine it" : "\u2716 Reset")
                .withStyle(worthIt ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(" — " + Format.compact(run.coins()) + " vs "
                        + Format.compact(referenceRun.coins()) + " coins from a " + reference.name()
                        + " shaft with no corpses.")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false)));
        }
        lines.add(verdict);

        addAdjustments(lines, result.adjustments());
        lines.add(gray(basisFooter(options.priceBasis) + " · " + pricedAt(result.lastUpdated()) + " · "
            + trim(options.efficiency) + "% efficiency · " + trim(options.coldRes) + " Cold Resistance"));
        return lines;
    }

    private static String trim(double value) {
        return value == Math.rint(value)
            ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%s", value);
    }

    private ShaftText() {}
}
