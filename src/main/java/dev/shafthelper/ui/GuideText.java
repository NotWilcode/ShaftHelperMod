package dev.shafthelper.ui;

import dev.shafthelper.core.Cold;
import dev.shafthelper.core.Gemstones;
import dev.shafthelper.core.Prices;
import dev.shafthelper.core.Pristine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The /shaft guide, split over four chat pages just like the bot's help embeds. */
public final class GuideText {

    public static final List<String> PAGE_TITLES = List.of(
        "What this mod does",
        "Is this shaft worth mining?",
        "What will this shaft pay me?",
        "Every option explained"
    );

    private static final String BENCHMARK = "Jasper";
    private static final String MAX_SHAFT_MINUTES =
        String.format(Locale.ROOT, "%.1f", Cold.shaftSeconds(Cold.MAX_COLD_RESISTANCE) / 60);

    private static Component title(int page) {
        return Component.literal("\u26CF Mineshaft calculator — " + page + "/4 · " + PAGE_TITLES.get(page - 1))
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    private static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static Component heading(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    private static Component pageList(int current) {
        var line = Component.literal("More pages: ").withStyle(ChatFormatting.GOLD);
        for (int page = 1; page <= PAGE_TITLES.size(); page += 1) {
            if (page > 1) line.append(gray(" · "));
            String label = page + ". " + PAGE_TITLES.get(page - 1);
            line.append(page == current
                ? Component.literal(label).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                : Component.literal("/shaft help:" + page + " " + label).withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    public static List<Component> page(int page) {
        int clamped = Math.min(Math.max(page, 1), PAGE_TITLES.size());
        List<Component> lines = switch (clamped) {
            case 2 -> page2();
            case 3 -> page3();
            case 4 -> page4();
            default -> page1();
        };
        lines.add(pageList(clamped));
        lines.add(gray("Prices come from the Hypixel Bazaar · coins/hr assumes non-stop mining"));
        return lines;
    }

    private static List<Component> page1() {
        List<Component> lines = new ArrayList<>();
        lines.add(title(1));
        lines.add(gray("Gemstone mineshafts are worth mining or worth resetting, and the deciding factor is the"
            + " lapis corpses inside: each one is +1 Pristine for that shaft only, up to " + Pristine.MAX_LAPIS_CORPSES + "."
            + " A " + BENCHMARK + " shaft is always worth it, so this mod answers how many corpses another gemstone"
            + " needs before it beats a plain Jasper shaft — and what a shaft actually pays out."));
        lines.add(heading("Two things it can tell you"));
        lines.add(white("/shaft mining_speed:1500") .copy()
            .append(gray(" — compare all 12 gemstones: ticks, coins/hr and corpses needed")));
        lines.add(white("/shaft mining_speed:1500 type:Amber lapis:3").copy()
            .append(gray(" — price the one shaft you are in right now")));
        lines.add(heading("The only option you must give"));
        lines.add(gray("mining_speed. Everything else has a sensible default and prices come from the Bazaar."));
        lines.add(heading("How drops are priced"));
        lines.add(gray("Everything is valued from the Flawless gem it crafts into ("
            + String.format(Locale.ROOT, "%,d", Gemstones.ROUGH_PER_FLAWLESS) + " rough = 1 Flawless),"
            + " averaged over 3 days. Flawless is the tier people actually buy, so its price barely moves."));
        return lines;
    }

    private static List<Component> page2() {
        List<Component> lines = new ArrayList<>();
        lines.add(title(2));
        lines.add(white("/shaft mining_speed:1500 mining_fortune:400 pristine:3"));
        lines.add(gray("lists every gemstone with the number of lapis corpses its shaft needs to out-earn a Jasper"
            + " shaft with no corpses. Jasper never gets the corpse bonus — that is the whole point of it"
            + " being the yardstick."));
        lines.add(heading("Reading each row"));
        lines.add(gray("Ticks — ticks to break one block, 20 ticks = 1 second"));
        lines.add(gray("Coins/hr — that shaft with the corpses it needs already in it, never more than "
            + Pristine.MAX_LAPIS_CORPSES));
        lines.add(gray("Pristine — the exact bonus Pristine it needs to catch Jasper (none = it already wins)"));
        lines.add(gray("Corpses — that number rounded up. 5 (>" + Pristine.MAX_LAPIS_CORPSES
            + ") means no shaft can hold enough, so reset it"));
        lines.add(heading("So in game"));
        lines.add(gray("Read the \"Mine the shaft if it spawned...\" lines, remember the corpse counts,"
            + " and reset anything below them."));
        return lines;
    }

    private static List<Component> page3() {
        List<Component> lines = new ArrayList<>();
        lines.add(title(3));
        lines.add(gray("Add type: and lapis: and the mod prices the single shaft you are standing in:"));
        lines.add(white("/shaft mining_speed:10000 mining_fortune:2500 pristine:15 type:Amber lapis:3 cold_res:100"));
        lines.add(heading("Cold decides how long you get"));
        lines.add(gray("Cold goes up 1 every " + Cold.SECONDS_PER_COLD + "s, and " + Cold.COLD_LIMIT
            + " Cold throws you out. Cold Resistance slows that clock:"
            + " seconds per Cold = 5 x (1 + cold_res / 100), so the max " + Cold.MAX_COLD_RESISTANCE
            + " res is one Cold every " + String.format(Locale.ROOT, "%.1f", Cold.secondsPerCold(Cold.MAX_COLD_RESISTANCE))
            + "s = " + MAX_SHAFT_MINUTES + " minutes inside."));
        lines.add(heading("The payout"));
        lines.add(gray("profit = coins/hr x time in shaft x efficiency"));
        lines.add(gray("efficiency (default " + (int) Cold.DEFAULT_EFFICIENCY + "%) is the share of that time"
            + " actually breaking blocks rather than walking, looting corpses or opening the next room."));
        return lines;
    }

    private static List<Component> page4() {
        List<Component> lines = new ArrayList<>();
        lines.add(title(4));
        lines.add(heading("Your stats"));
        lines.add(gray("mining_speed — Mining Speed. Ticks per block are floor(strength x 30 / mining_speed)"));
        lines.add(gray("mining_fortune — Gemstone Mining Fortune. Drops are " + Gemstones.MIN_DROPS + "-"
            + Gemstones.MAX_DROPS + " per block x (1 + fortune/100)"));
        lines.add(gray("pristine — your base Pristine. Each point is a 1% chance the block's drops come out flawed ("
            + Gemstones.ROUGH_PER_FLAWED + " rough = 1 flawed)"));
        lines.add(gray("cold_res — Cold Resistance, how long you last in a shaft (max " + Cold.MAX_COLD_RESISTANCE
            + " ~ " + MAX_SHAFT_MINUTES + " min)"));
        lines.add(heading("The shaft in front of you"));
        lines.add(gray("type — which gemstone shaft it is; switches the mod to single-shaft mode"));
        lines.add(gray("lapis — lapis corpses in it, 0-" + Pristine.MAX_LAPIS_CORPSES
            + ", each worth +1 Pristine while you are inside"));
        lines.add(gray("efficiency — how much of your time in there is spent mining (default "
            + (int) Cold.DEFAULT_EFFICIENCY + "%)"));
        lines.add(heading("Prices"));
        lines.add(gray("prices — " + Prices.modeLabel(Prices.DEFAULT_MODE) + " (default) is what a filled sell"
            + " offer pays; " + Prices.modeLabel(Prices.Mode.INSTA_SELL) + " is what selling to the Bazaar pays right now"));
        lines.add(gray("price_data — " + Prices.dataLabel(Prices.DEFAULT_DATA) + " (default) rides out manipulation; "
            + Prices.dataLabel(Prices.Data.LIVE) + " is the current snapshot"));
        lines.add(gray("price_basis — Flawless value (default): a rough is worth flawless / " + Gemstones.TIER_RATIO
            + "^3 and a flawed flawless / " + Gemstones.TIER_RATIO + "^2. Flawless is what players actually craft"
            + " and buy, so it is the hardest tier to manipulate. price_basis:listed values the drops at their own"
            + " Bazaar price instead"));
        lines.add(heading("The comparison"));
        lines.add(gray("benchmark — the shaft everything is measured against, mined at your base Pristine"
            + " (default Jasper)"));
        return lines;
    }

    private GuideText() {}
}
