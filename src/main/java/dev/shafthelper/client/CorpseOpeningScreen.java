package dev.shafthelper.client;

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
    private final RewardEntry winner;
    private final double finalScrollOffset;
    private final double jitterPx;

    private long startTimeMs = -1;
    private long landedAtMs = -1;
    private boolean skipped = false;

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
            double unitValue = (price != null && !price.isNaN() && price > 0) ? price : 1.0;
            entries.add(new RewardEntry(e.getKey(), e.getValue(), unitValue));
        }
        if (entries.isEmpty()) {
            entries.add(new RewardEntry("Nothing", 1, 0.0));
        }

        RewardEntry best = entries.get(0);
        for (RewardEntry e : entries) {
            // If prices are unknown for everything, unitValue falls back to 1.0
            // for all entries, so this effectively ranks by amount instead.
            double bestScore = best.unitValue() > 1.0 ? best.totalValue() : best.amount();
            double score = e.unitValue() > 1.0 ? e.totalValue() : e.amount();
            if (score > bestScore) best = e;
        }
        this.winner = best;

        this.reelItems = buildReel(entries, winner);

        Random rnd = new Random();
        // Land somewhere within the middle 60% of the slot, not dead center,
        // so it doesn't feel mechanically identical every time.
        this.jitterPx = (rnd.nextDouble() - 0.5) * (SLOT_WIDTH * 0.6);
        this.finalScrollOffset = WINNER_INDEX * SLOT_STEP + (SLOT_WIDTH / 2.0) + jitterPx;
    }

    private static List<RewardEntry> buildReel(List<RewardEntry> pool, RewardEntry winner) {
        Random rnd = new Random();
        List<RewardEntry> reel = new ArrayList<>(REEL_LENGTH);
        for (int i = 0; i < REEL_LENGTH; i++) {
            reel.add(pool.get(rnd.nextInt(pool.size())));
        }
        reel.set(WINNER_INDEX, winner);
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

        double eased = easeOutBack(t);
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
            drawCentered(gg, resultLine, centerX, reelY + SLOT_HEIGHT + 34, 0xFFFFD54A);
            drawCentered(gg, "Click or press any key to continue", centerX, reelY + SLOT_HEIGHT + 50, 0xFFAAAAAA);

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
        double relative = winner.totalValue() > 0
            ? Math.min(1.0, entry.totalValue() / winner.totalValue())
            : (entry == winner ? 1.0 : 0.3);

        // Low value -> gray, high value -> deep purple/gold, similar to rarity banding.
        int lowR = 0x3A, lowG = 0x3A, lowB = 0x40;
        int highR = 0x5A, highG = 0x2D, highB = 0x8C;

        int r = (int) (lowR + (highR - lowR) * relative);
        int g = (int) (lowG + (highG - lowG) * relative);
        int b = (int) (lowB + (highB - lowB) * relative);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** easeOutBack: fast spin that decelerates and slightly overshoots before settling on the target. */
    private static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}