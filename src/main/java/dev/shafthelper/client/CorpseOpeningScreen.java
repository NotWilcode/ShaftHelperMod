package dev.shafthelper.client;

import dev.shafthelper.core.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * CS:GO-style crate opening reveal for Vanguard corpse loot.
 *
 * A horizontal reel of the corpse's own rewards spins past a fixed
 * center pointer and decelerates onto a predetermined winner (the
 * highest-value reward). The winner is chosen before the reel is
 * built, exactly like a real crate opening: the outcome is fixed up
 * front, the animation is just presentation.
 */
public final class CorpseOpeningScreen extends Screen {

    // ---- tuning ---------------------------------------------------------

    private static final int SLOT_WIDTH = 110;
    private static final int SLOT_GAP = 12;
    private static final int SLOT_STEP = SLOT_WIDTH + SLOT_GAP;
    private static final int SLOT_HEIGHT = 130;

    private static final int REEL_LENGTH = 60;   // total slots generated
    private static final int WINNER_INDEX = 50;  // where the winner sits (leaves buffer for overshoot)

    private static final long SPIN_DURATION_MS = 6000L;
    private static final long AUTO_CLOSE_AFTER_MS = 2500L; // once landed, auto-close after this long

    // ---- state ------------------------------------------------------------

    private final List<RewardEntry> reelItems;  
    private final RewardEntry winner;   // the real best reward you got - lands under the pointer  
    private final RewardEntry jackpot;  // rarest table item - teases past right before landing 
    private final Function<String, Double> priceLookup;  
    private final double totalProfit;   // real bazaar coins for everything you got 
    private final double finalScrollOffset;  
    private final double jitterPx;

    private long startTimeMs = -1;
    private long landedAtMs = -1;
    private boolean skipped = false;

    // ---- Vanguard loot table (from the Hypixel SkyBlock wiki) -----------  
    // weight = drop weight; LOWER weight = rarer = flashier. We turn weight  
    // into a display "value" (160 / weight) purely so color banding and the  
    // jackpot tease rank correctly. Presentation only, not real prices.  
    private record VanguardDrop(String name, long amount, int weight) {  
        double rarityValue() { return 160.0 / weight; }  
    }  
  
    private static final List<VanguardDrop> VANGUARD_LOOT_TABLE = List.of(  
        new VanguardDrop("Flawless Onyx Gemstone", 1, 160),  
        new VanguardDrop("Flawless Onyx Gemstone", 2, 80),  
        new VanguardDrop("Flawless Citrine Gemstone", 1, 160),  
        new VanguardDrop("Flawless Citrine Gemstone", 2, 80),  
        new VanguardDrop("Flawless Peridot Gemstone", 1, 160),  
        new VanguardDrop("Flawless Peridot Gemstone", 2, 80),  
        new VanguardDrop("Flawless Aquamarine Gemstone", 1, 160),  
        new VanguardDrop("Flawless Aquamarine Gemstone", 2, 80),  
        new VanguardDrop("Dwarven O's Metallic Minis", 1, 120),  
        new VanguardDrop("Suspicious Scrap", 8, 160),  
        new VanguardDrop("Suspicious Scrap", 16, 80),  
        new VanguardDrop("Enchanted Book (Ice Cold I)", 1, 160),  
        new VanguardDrop("Blue Goblin Egg", 1, 160),  
        new VanguardDrop("Blue Goblin Egg", 2, 80),  
        new VanguardDrop("Refined Umber", 1, 160),  
        new VanguardDrop("Refined Umber", 2, 80),  
        new VanguardDrop("Refined Tungsten", 1, 160),  
        new VanguardDrop("Refined Tungsten", 2, 80),  
        new VanguardDrop("Glacite Amalgamation", 1, 160),  
        new VanguardDrop("Glacite Amalgamation", 2, 80),  
        new VanguardDrop("Glacite Amalgamation", 4, 40),  
        new VanguardDrop("Mithril Plate", 1, 60),  
        new VanguardDrop("Umber Plate", 1, 30),  
        new VanguardDrop("Tungsten Plate", 1, 30),  
        new VanguardDrop("Umber Key", 1, 80),  
        new VanguardDrop("Umber Key", 2, 40),  
        new VanguardDrop("Umber Key", 4, 20),  
        new VanguardDrop("Tungsten Key", 1, 80),  
        new VanguardDrop("Tungsten Key", 2, 40),  
        new VanguardDrop("Tungsten Key", 4, 20),  
        new VanguardDrop("Skeleton Key", 1, 10),  
        new VanguardDrop("Frozen Scute", 1, 10),  
        new VanguardDrop("Caged Wisp", 1, 10),  
        new VanguardDrop("Shattered Locket", 1, 5),  
        new VanguardDrop("Opal Crystal", 1, 120),  
        new VanguardDrop("Onyx Crystal", 1, 120),  
        new VanguardDrop("Peridot Crystal", 1, 120),  
        new VanguardDrop("Citrine Crystal", 1, 120),  
        new VanguardDrop("Aquamarine Crystal", 1, 120)  
    );  
  
    private static List<RewardEntry> lootTableEntries() {  
        List<RewardEntry> list = new ArrayList<>();  
        for (VanguardDrop d : VANGUARD_LOOT_TABLE) {  
            list.add(new RewardEntry(d.name(), d.amount(), d.rarityValue()));  
        }  
        return list;  
    }  
  
    /** Most-valued item in the whole table by real bazaar price (rarity as fallback). */  
    private static RewardEntry jackpotFromTable(Function<String, Double> priceLookup) {  
        RewardEntry best = null;  
        for (VanguardDrop d : VANGUARD_LOOT_TABLE) {  
            Double price = priceLookup == null ? null : priceLookup.apply(d.name());  
            double unitValue = (price != null && !price.isNaN() && price > 0)  
                ? price : d.rarityValue();  
            RewardEntry e = new RewardEntry(d.name(), d.amount(), unitValue);  
            if (best == null || e.totalValue() > best.totalValue()) best = e;  
        }  
        return best;  
    }  
  
    /** Real bazaar coins for the whole corpse, skipping items with no known price. */  
    private static double coinValueOf(Map<String, Long> rewards, Function<String, Double> priceLookup) {  
        if (priceLookup == null) return 0;  
        double total = 0;  
        for (Map.Entry<String, Long> e : rewards.entrySet()) {  
            Double price = priceLookup.apply(e.getKey());  
            if (price != null && !price.isNaN() && price > 0) total += e.getValue() * price;  
        }  
        return total;  
    }
  
    /** Table-derived rarity value for a real reward name, so unknown-price items still rank sensibly. */  
    private static double rarityValueFor(String name) {  
        double v = 1.0;  
        for (VanguardDrop d : VANGUARD_LOOT_TABLE) {  
            if (d.name().equalsIgnoreCase(name)) v = Math.max(v, d.rarityValue());  
        }  
        return v;  
    }

    public record RewardEntry(String name, long amount, double unitValue) {
        double totalValue() {
            return amount * unitValue;
        }
    }

    /**
     * @param rewards     the parsed corpse rewards (name -> amount)
     * @param priceLookup name -> unit price; return null/NaN for unknown items.
     *                    Wire this to your existing bazaar price cache. If a
     *                    price can't be found the item's amount is used as a
     *                    stand-in "value" so something sane still wins.
     */
    public CorpseOpeningScreen(Map<String, Long> rewards, Function<String, Double> priceLookup) {
        super(Component.literal("Vanguard Corpse Loot"));

        List<RewardEntry> entries = new ArrayList<>();  
        for (Map.Entry<String, Long> e : rewards.entrySet()) {  
            Double price = priceLookup == null ? null : priceLookup.apply(e.getKey());  
            // No price? Fall back to the loot table's rarity value so rare drops still rank high.  
            double unitValue = (price != null && !price.isNaN() && price > 0)  
                ? price : rarityValueFor(e.getKey());  
            entries.add(new RewardEntry(e.getKey(), e.getValue(), unitValue));  
        }  
        if (entries.isEmpty()) {  
            entries.add(new RewardEntry("Nothing", 1, 0.0));  
        }  
  
        RewardEntry best = entries.get(0);  
        for (RewardEntry e : entries) {  
            if (e.totalValue() > best.totalValue()) best = e;  
        }  
        this.priceLookup = priceLookup;
        this.winner = best;                 // truthful: this is what you actually got  
        this.jackpot = jackpotFromTable(priceLookup);  // the flashy near-miss pulled from the full table  
        this.totalProfit = coinValueOf(rewards, priceLookup);  
        this.reelItems = buildReel(winner, jackpot);  
  
        // Land slightly off-center toward the jackpot side so the jackpot slot  
        // (right before the winner) stays peeking at the pointer's edge - the  
        // "you juuust about had it" frame. No randomness: tease every time.  
        this.jitterPx = -(SLOT_WIDTH * 0.22);  
        this.finalScrollOffset = WINNER_INDEX * SLOT_STEP + (SLOT_WIDTH / 2.0) + jitterPx;
    }

    private static double scoreOf(RewardEntry e) {  
        // If prices are unknown, unitValue is 1.0 for everything, so this ranks by amount.  
        return e.unitValue() > 1.0 ? e.totalValue() : e.amount();  
    }

    private static List<RewardEntry> buildReel(RewardEntry landed, RewardEntry jackpot) {  
        List<RewardEntry> pool = lootTableEntries();   // whole Vanguard table as filler  
        Random rnd = new Random();  
        List<RewardEntry> reel = new ArrayList<>(REEL_LENGTH);  
        for (int i = 0; i < REEL_LENGTH; i++) {  
            reel.add(pool.get(rnd.nextInt(pool.size())));  
        }  
        reel.set(WINNER_INDEX, landed);  
        // Jackpot rides the two slots right before the landing slot, so during  
        // the slow crawl it climbs to the pointer... then slips past. Max tease.  
        reel.set(WINNER_INDEX - 1, jackpot);  
        reel.set(WINNER_INDEX - 2, jackpot);  
        return reel;  
    }

    // ---- lifecycle ----------------------------------------------------

    @Override
    protected void init() {
        startTimeMs = System.currentTimeMillis();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Only allow escape to close once the reel has landed.
        return landedAtMs > 0;
    }

    // ---- input ----------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        handleSkipOrClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        handleSkipOrClose();
        return true;
    }

    private void handleSkipOrClose() {
        if (landedAtMs > 0) {
            closeThisScreen();
        } else {
            skipped = true;
        }
    }

    private void closeThisScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    // ---- render ------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gg, mouseX, mouseY, partialTick);

        // Fully cover the HUD/world behind us.
        gg.fill(0, 0, width, height, 0xEE0B0B12);

        long now = System.currentTimeMillis();
        long elapsed = now - startTimeMs;
        double t = skipped ? 1.0 : Math.min(1.0, elapsed / (double) SPIN_DURATION_MS);

        if (t >= 1.0 && landedAtMs < 0) {
            landedAtMs = now;
        }

        double eased = easeOutQuint(t);
        double scrollOffset = finalScrollOffset * eased;

        int centerX = width / 2;
        int reelY = height / 2 - SLOT_HEIGHT / 2;

        gg.enableScissor(0, reelY - 4, width, reelY + SLOT_HEIGHT + 4);
        for (int i = 0; i < reelItems.size(); i++) {
            int slotX = (int) (centerX + i * SLOT_STEP - scrollOffset) - SLOT_WIDTH / 2;
            if (slotX + SLOT_STEP < 0 || slotX > width) continue; // off-screen, skip drawing
            drawSlot(gg, reelItems.get(i), slotX, reelY, i == WINNER_INDEX && t >= 1.0);
        }
        gg.disableScissor();

        // Fade edges so the reel doesn't hard-clip.
        gg.fillGradient(0, reelY, width / 2 - 160, reelY + SLOT_HEIGHT, 0xFF0B0B12, 0x000B0B12);
        gg.fillGradient(width / 2 + 160, reelY, width, reelY + SLOT_HEIGHT, 0x000B0B12, 0xFF0B0B12);

        // Center pointer.
        int pointerColor = 0xFFFFD54A;
        gg.fill(centerX - 2, reelY - 14, centerX + 2, reelY + SLOT_HEIGHT + 14, pointerColor);
        gg.fill(centerX - 8, reelY - 14, centerX + 8, reelY - 10, pointerColor);
        gg.fill(centerX - 8, reelY + SLOT_HEIGHT + 10, centerX + 8, reelY + SLOT_HEIGHT + 14, pointerColor);

        if (landedAtMs > 0) {  
            String resultLine = "You got: " + winner.name() + (winner.amount() > 1 ? " x" + winner.amount() : "");  
            drawCentered(gg, resultLine, centerX, reelY + SLOT_HEIGHT + 34, 0xFFFF5555);  
  
            if (totalProfit > 0) {  
                drawCentered(gg, "Profit: " + Format.compact(totalProfit) + " coins",  
                    centerX, reelY + SLOT_HEIGHT + 50, 0xFF55FF55);  
            }  
  
            drawCentered(gg, "SO close to " + jackpot.name() + "...",  
                centerX, reelY + SLOT_HEIGHT + 66, 0xFFFFD54A);  
            drawCentered(gg, "Click or press any key to continue",  
                centerX, reelY + SLOT_HEIGHT + 82, 0xFFAAAAAA);  
  
            if (now - landedAtMs >= AUTO_CLOSE_AFTER_MS) {  
                closeThisScreen();  
            }  
        } else {
            drawCentered(gg, "Opening Vanguard Corpse...", centerX, reelY - 34, 0xFFFFFFFF);
        }
    }

    /** 26.2 dropped the drawCenteredString helper, so this centers text manually via font.width(). */
    private void drawCentered(GuiGraphicsExtractor gg, String text, int centerX, int y, int color) {
        int textWidth = font.width(text);
        gg.text(font, text, centerX - textWidth / 2, y, color, true);
    }

    private void drawSlot(GuiGraphicsExtractor gg, RewardEntry entry, int x, int y, boolean highlightAsWinner) {
        int bg = colorFor(entry);
        int border = highlightAsWinner ? 0xFFFFD54A : 0x33FFFFFF;

        gg.fill(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, bg);
        gg.fill(x, y, x + SLOT_WIDTH, y + 2, border);
        gg.fill(x, y + SLOT_HEIGHT - 2, x + SLOT_WIDTH, y + SLOT_HEIGHT, border);
        gg.fill(x, y, x + 2, y + SLOT_HEIGHT, border);
        gg.fill(x + SLOT_WIDTH - 2, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, border);

        int textY = y + SLOT_HEIGHT / 2 - 10;
        String name = font.plainSubstrByWidth(entry.name(), SLOT_WIDTH - 12);
        drawCentered(gg, name, x + SLOT_WIDTH / 2, textY, 0xFFFFFFFF);

        if (entry.amount() > 1) {
            drawCentered(gg, "x" + entry.amount(), x + SLOT_WIDTH / 2, textY + 12, 0xFFCCCCCC);
        }
    }

    /** Deterministic-ish color per item name so repeats in the reel look consistent, tinted by relative value. */
    private int colorFor(RewardEntry entry) {  
        double relative = jackpot.totalValue() > 0  
            ? Math.min(1.0, entry.totalValue() / jackpot.totalValue())  
            : (entry == jackpot ? 1.0 : 0.3);

        // Low value -> gray, high value -> deep purple/gold, similar to rarity banding.
        int lowR = 0x3A, lowG = 0x3A, lowB = 0x40;
        int highR = 0x5A, highG = 0x2D, highB = 0x8C;

        int r = (int) (lowR + (highR - lowR) * relative);
        int g = (int) (lowG + (highG - lowG) * relative);
        int b = (int) (lowB + (highB - lowB) * relative);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** easeOutBack: fast spin that decelerates and slightly overshoots before settling on the target. */
    private static double easeOutQuint(double t) {  
        double u = 1 - t;  
        return 1 - u * u * u * u * u;  
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}