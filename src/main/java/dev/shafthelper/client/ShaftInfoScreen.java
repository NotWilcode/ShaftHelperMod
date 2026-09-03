package dev.shafthelper.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Short overview shown from the main menu. Detailed calculations remain in ShaftGuideScreen. */
public final class ShaftInfoScreen extends Screen {

    private static final int PANEL_WIDTH = 310;
    private static final int LINE_GAP = 3;
    private static final List<String> LINES = List.of(
        "Shaft Helper",
        "Client-side tools for Hypixel SkyBlock gemstone mineshafts.",
        "",
        "Live tools",
        "- Shaft profitability and corpse comparisons",
        "- Mining ticks, ping, TPS, and debug block details",
        "- Pristine profit tracking and session shaft history",
        "- HUDs, themes, timers, and configurable layouts",
        "",
        "Waypoint tools",
        "- Personal waypoints with grouping and ordered navigation",
        "- Built-in corpse locations loaded with the mod",
        "- Toggle corpse locations in Config > Waypoints",
        "",
        "Data sources",
        "Stats come from the tab list. Prices come from Bazaar data.",
        "Run /shaft for the main menu, or /shaft help for the guide."
    );

    public ShaftInfoScreen() {
        super(Component.literal("Shaft Helper Info"));
    }

    @Override
    protected void init() {
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 45, this.height - 34, 90, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = 24;
        int height = LINES.size() * (this.font.lineHeight + LINE_GAP) + 16;
        int bg = ShaftTracker.config().themeBg;
        int border = ShaftTracker.config().themeBorder;
        int text = ShaftTracker.config().themeText;
        int accent = ShaftTracker.config().themeAccent;

        graphics.fill(left, top, left + PANEL_WIDTH, top + height, bg);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, border);
        graphics.fill(left, top + height - 1, left + PANEL_WIDTH, top + height, border);
        graphics.fill(left, top, left + 1, top + height, border);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + height, border);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = top + 8;
        for (int i = 0; i < LINES.size(); i++) {
            String line = LINES.get(i);
            if (!line.isEmpty()) {
                int color = i == 0 || line.equals("Live tools") || line.equals("Waypoint tools") || line.equals("Data sources")
                    ? accent : text;
                graphics.text(this.font, line, left + 10, y, color, true);
            }
            y += this.font.lineHeight + LINE_GAP;
        }
    }
}