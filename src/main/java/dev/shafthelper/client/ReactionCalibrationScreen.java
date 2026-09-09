package dev.shafthelper.client;

import dev.shafthelper.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Arms an in-mining calibration session and displays its learned lost-time offset. */
public final class ReactionCalibrationScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private final ModConfig config = ShaftTracker.config();
    private EditBox adjustmentBox;

    public ReactionCalibrationScreen() {
        super(Component.literal("Mining Calibration"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 90;

        adjustmentBox = new EditBox(font, centerX - 45, top + 116, 90, 20,
            Component.literal("Lost-time adjustment"));
        adjustmentBox.setValue(Integer.toString(config.reactionTimeAdjustment));
        adjustmentBox.setMaxLength(5);
        addRenderableWidget(adjustmentBox);

        addRenderableWidget(Button.builder(Component.literal("Start mining calibration"), button -> startCalibration())
            .bounds(centerX - 90, top + 48, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save adjustment"), button -> saveAdjustment())
            .bounds(centerX - 65, top + 146, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(centerX - 35, height - 30, 70, 20).build());
    }

    private void startCalibration() {
        int samples = MiningCalculator.beginMiningCalibration();
        minecraft.setScreen(null);
        minecraft.gui.setOverlayMessage(Component.literal(
            "Mining calibration armed: mine " + samples + " blocks normally."), false);
    }

    private void saveAdjustment() {
        config.reactionTimeAdjustment = parseAdjustment();
        ShaftTracker.saveConfig();
    }

    private int parseAdjustment() {
        try {
            return Math.clamp(Integer.parseInt(adjustmentBox.getValue()), -500, 500);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int top = height / 2 - 90;
        int left = centerX - PANEL_WIDTH / 2;
        int bg = config.themeBg;
        int border = config.themeBorder;
        int text = config.themeText;
        int accent = config.themeAccent;

        graphics.fill(0, 0, width, height, 0xDD000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 178, bg);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, border);
        graphics.fill(left, top + 177, left + PANEL_WIDTH, top + 178, border);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        String title = "Mining Calibration";
        graphics.text(font, title, centerX - font.width(title) / 2, top + 12, accent, true);
        String line1 = "Only consecutive ping-glider blocks are measured.";
        String line2 = "Keep attack held while moving between tracked blocks.";
        graphics.text(font, line1, centerX - font.width(line1) / 2, top + 27, text, true);
        graphics.text(font, line2, centerX - font.width(line2) / 2, top + 38, text, true);
        String label = "Manual lost-time adjustment (ms)";
        graphics.text(font, label, centerX - font.width(label) / 2, top + 101, text, true);
        String current = "Current learned offset: " + config.configuredPing + "ms";
        graphics.text(font, current, centerX - font.width(current) / 2, top + 171, text, true);
    }

    @Override
    public void onClose() {
        saveAdjustment();
        super.onClose();
    }
}
