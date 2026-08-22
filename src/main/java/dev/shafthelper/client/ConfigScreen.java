package dev.shafthelper.client;  
  
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;  

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.shafthelper.client.widget.StyledButton;
import dev.shafthelper.client.widget.StyledSlider;
import dev.shafthelper.client.widget.StyledToggle;
import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Cold;
import dev.shafthelper.core.Waypoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
  
public final class ConfigScreen extends Screen {  
  
    private static final int FIELD_WIDTH = 100;  
    private static final int FIELD_HEIGHT = 20;  
    private static final int ROW_HEIGHT = 24;  
    private static final int HEADER_HEIGHT = 14;  
    private static final int SECTION_GAP = 10;  
    private static final int PANEL_PAD = 12;  
  
    private static final int LABEL_COLOR = 0xffe0e1dd;  
    private static final int HEADER_COLOR = 0xff778da9;  
    private static final int PANEL_BG = 0xe00d1b2a;  
    private static final int PANEL_BORDER = 0xff1b263b;  
  
    private final ModConfig config = ShaftTracker.config();  

    private final List<String> presetFiles = new ArrayList<>();  
    private int selectedPreset = 0;
  
    // Tab state (class-level, NOT inside init())  
    private enum Tab { STATS, HUD, OPTIONS, WAYPOINTS }  
    private Tab activeTab = Tab.STATS;  
    private long tabSwitchTime = 0L; // for animation  
    private static final long ANIM_MS = 160L;
  
    private final List<Text> labels = new ArrayList<>();  
    private final List<Text> headers = new ArrayList<>();  
    private final List<Integer> dividers = new ArrayList<>();  
    private int panelLeft, panelTop, panelRight, panelBottom, bgLeft, bgTop, bgRight, bgBottom, titleY, labelRight, fieldX;  
    private final List<net.minecraft.client.gui.components.AbstractWidget> tabContent = new ArrayList<>();
    private final List<EditBox> numberFields = new ArrayList<>();  
  
    // Needed by both panel-geometry and buildStats(), so it's a field.  
    private String[] statLabels;  
  
    private record Text(String value, int x, int y, int color) {}  
  
    public ConfigScreen() {  
        super(Component.literal("Shaft Helper Settings"));  
    }  

    private float animEase() {  
        if (tabSwitchTime == 0L) return 1f;  
        float t = Math.clamp((System.currentTimeMillis() - tabSwitchTime) / (float) ANIM_MS, 0f, 1f);  
        return t * t * (3f - 2f * t);  
    }
    // scale the alpha channel of an 0xAARRGGBB color  
    private static int withAlpha(int color, float f) {  
        int a = (int) (((color >>> 24) & 0xFF) * Math.clamp(f, 0f, 1f));  
        return (a << 24) | (color & 0xFFFFFF);  
    }
    
    @Override  
    protected void init() {  
        tabContent.clear();
        labels.clear();  
        headers.clear();  
        dividers.clear();  
        numberFields.clear();
        loadPresetFiles();
  
        int centerShift = (90 + 8) /2; // half of (tab column width + OUTER_PAD); ~49
        labelRight = this.width / 2 - 5 + centerShift;  
        fieldX = this.width / 2 + 5 + centerShift;  
  
        statLabels = new String[] {  
            "Mining Speed", "Mining Fortune", "Gemstone Fortune", "Gemstone Spread", "Proffesional Level (max 140)", "Pristine",  
            "Cold Resistance (max " + Cold.MAX_COLD_RESISTANCE + ")",  
            "Efficiency % (default " + (int) Cold.DEFAULT_EFFICIENCY + ")"  
        };  
        int maxLabelW = 0;  
        for (String s : statLabels) maxLabelW = Math.max(maxLabelW, this.font.width(s));  
        panelLeft = labelRight - maxLabelW - PANEL_PAD;  
        panelRight = fieldX + FIELD_WIDTH + PANEL_PAD;  
  
        // Height of the currently-visible tab only (one section shows at a time).  
        int contentHeight = contentHeightFor(activeTab);  
        int y = 80 + PANEL_PAD;  
        panelTop = y - PANEL_PAD;  
        titleY = panelTop - HEADER_HEIGHT;  
  
        // --- Left tab column (always built) ---  
        int tabX = panelLeft - 90;  
        int tabY = panelTop;  
        for (Tab t : Tab.values()) {  
            StyledButton tabButton = new StyledButton(tabX, tabY, 80, FIELD_HEIGHT,  
                Component.literal(tabName(t)), btn -> switchTab(t)); 
            tabButton.active = (t != activeTab); // grey out current tab  
            addRenderableWidget(tabButton);  
            tabY += ROW_HEIGHT;  
        }  
  
        // --- Active tab content ---  
        int endY = switch (activeTab) {  
            case STATS -> buildStats(y);  
            case HUD -> buildHUD(y);  
            case OPTIONS -> buildOptions(y);  
            case WAYPOINTS -> buildWaypoints(y);  
        };
  
        panelBottom = endY - (ROW_HEIGHT - FIELD_HEIGHT) + PANEL_PAD;  

        int OUTER_PAD = 8;  
        bgLeft   = (panelLeft - 90) - OUTER_PAD;                 // left of the tab column  
        bgTop    = titleY - OUTER_PAD;                           // above the title  
        bgRight  = panelRight + OUTER_PAD;  
        bgBottom = (panelBottom + 8 + FIELD_HEIGHT) + OUTER_PAD; // below the Done button  
    }  

    private void loadPresetFiles() {  
        presetFiles.clear();  
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("PresavedWaypoints");  
        if (!Files.isDirectory(dir)) return;  
        try (Stream<Path> files = Files.list(dir)) {  
            files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))  
                .map(p -> p.getFileName().toString())  
                .sorted(Comparator.naturalOrder())  
                .forEach(presetFiles::add);  
        } catch (Exception ignored) {  
            // directory unreadable — leave list empty  
        }  
        if (selectedPreset >= presetFiles.size()) selectedPreset = 0;  
    }
  
    private void switchTab(Tab t) {  
        if (t == activeTab) return;  
        activeTab = t;  
        tabSwitchTime = System.currentTimeMillis();  
        rebuildWidgets(); // clears widgets and re-runs init()  
    }  
  
    private static String tabName(Tab t) {  
        return switch (t) {  
            case STATS -> "Stats";  
            case HUD -> "HUD";  
            case OPTIONS -> "Options";  
            case WAYPOINTS -> "Waypoints";  
        };  
    }  
  
    private int contentHeightFor(Tab t) {  
        int rows = switch (t) {  
            case STATS -> 9;  
            case HUD -> 5;  
            case OPTIONS -> 4;  
            case WAYPOINTS -> 3;  
        };  
        return HEADER_HEIGHT + rows * ROW_HEIGHT;  
    }  
  
    private int buildStats(int y) {  
        y = section("Stats", y);  
        y = labeledInt(statLabels[0], y, config.miningSpeed, v -> config.miningSpeed = v);  
        y = labeledInt(statLabels[1], y, config.miningFortune, v -> config.miningFortune = v);  
        y = labeledInt(statLabels[2], y, config.gemstoneFortune, v -> config.gemstoneFortune = v);  
        y = labeledInt(statLabels[3], y, config.gemstoneSpread, v -> config.gemstoneSpread = v);  
        y = labeledInt(statLabels[4], y, config.proffesionalLevel, v -> config.proffesionalLevel = v); 
        y = labeledDouble(statLabels[5], y, config.pristine, 0, 100, v -> config.pristine = v);  
        y = labeledDouble(statLabels[6], y, config.coldRes, 0, Cold.MAX_COLD_RESISTANCE, v -> config.coldRes = v);  
        y = labeledDouble(statLabels[7], y, config.efficiency, 1, 100, v -> config.efficiency = v);  
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT, 
            config.goblinOmelette, v -> config.goblinOmelette = v)));  
        addLabel("Goblin Omelette", y);
        return y;  
    }  
  
    private int buildHUD(int y) {  
        y = section("HUD", y);  
        tabContent.add(addRenderableWidget(new StyledButton(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT, 
            Component.literal("HUD"), b -> moveHUDGui())));  
        addLabel("Move GUI", y);  
        y += ROW_HEIGHT;  
    
        tabContent.add(addRenderableWidget(new StyledToggle(  
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.trackerEnabled, v -> config.trackerEnabled = v)));  
        addLabel("Tracker overlay", y);  
        y += ROW_HEIGHT;  
    
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.logEnabled, v -> config.logEnabled = v)));  
        addLabel("Log overlay", y);  
        y += ROW_HEIGHT;  
    
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.profitEnabled, v -> config.profitEnabled = v)));  
        addLabel("Profit overlay", y);  
        y += ROW_HEIGHT;  
    
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.enableDebugOverlay, v -> config.enableDebugOverlay = v)));  
        addLabel("Debug overlay", y);  
        y += ROW_HEIGHT;  

        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.enableNetwork, v -> config.enableNetwork = v)));  
        addLabel("Network overlay", y);  
        y += ROW_HEIGHT;  
    
        return y;  
    }  
  
    private int buildOptions(int y) {  
        y = section("Options", y);  
        tabContent.add(addRenderableWidget(new StyledSlider(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
            config.benchmark, v -> config.benchmark = v)));  
        addLabel("Benchmark", y);  
        y += ROW_HEIGHT;
            
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.autoStats, v -> config.autoStats = v)));  
        addLabel("Auto-read stats", y);  
        y += ROW_HEIGHT;  
    
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.enableDustParticles, v -> config.enableDustParticles = v)));  
        addLabel("Power Coating / Glacial", y);  
        y += ROW_HEIGHT;  
    
        // Only if you add pingSoundAlert to ModConfig:  
        tabContent.add(addRenderableWidget(new StyledToggle(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            config.pingSoundAlert, v -> config.pingSoundAlert = v)));  
        addLabel("Ping Sound Alert", y);  
        y += ROW_HEIGHT;  
    
        return y;  
    }
  
    private int buildWaypoints(int y) {  
        y = section("Waypoints", y);  
        tabContent.add(addRenderableWidget(new StyledButton(
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
            Component.literal("Manage Waypoints"), b -> openWaypoints())));
        addLabel("Open waypoints manager", y);  
        y += ROW_HEIGHT;  
        // Glacite preset selector (cycles through files in config/PresavedWaypoints)  
        String presetLabel = presetFiles.isEmpty()  
            ? "No presets found"  
            : presetFiles.get(selectedPreset);  
        StyledButton selector = new StyledButton(  
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            Component.literal(presetLabel), b -> {  
                if (!presetFiles.isEmpty()) {  
                    selectedPreset = (selectedPreset + 1) % presetFiles.size();  
                    rebuildWidgets();  
                }  
            });  
        selector.active = !presetFiles.isEmpty();  
        tabContent.add(addRenderableWidget(selector));  
        addLabel("Glacite preset", y);  
        y += ROW_HEIGHT;  
    
        // Import the selected preset  
        StyledButton importBtn = new StyledButton(  
            fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,  
            Component.literal("Import Preset"), b -> importSelectedPreset());  
        importBtn.active = !presetFiles.isEmpty();  
        tabContent.add(addRenderableWidget(importBtn));  
        addLabel("Import selected preset", y);  
        y += ROW_HEIGHT;  
    
        return y;   
    }  
  
    private int section(String name, int y) {  
        if (!headers.isEmpty()) {  
            dividers.add(y - SECTION_GAP / 2);  
        }  
        headers.add(new Text(name, panelLeft + PANEL_PAD, y, HEADER_COLOR));  
        return y + HEADER_HEIGHT;  
    }  
  
    private void addLabel(String text, int y) {  
        labels.add(new Text(text, labelRight - this.font.width(text),  
            y + (FIELD_HEIGHT - 8) / 2, LABEL_COLOR));  
    }  
  
    private int labeledInt(String label, int y, int initial, IntConsumer setter) {  
        addIntField(fieldX, y, initial, setter);  
        addLabel(label, y);  
        return y + ROW_HEIGHT;  
    }  
  
    private int labeledDouble(String label, int y, double initial, double min, double max,  
                              DoubleConsumer setter) {  
        addDoubleField(fieldX, y, initial, min, max, setter);  
        addLabel(label, y);  
        return y + ROW_HEIGHT;  
    }  
  
    private void moveHUDGui() {  
        this.minecraft.setScreen(new HUDEditorScreen(this));  
    }  
  
    private void addIntField(int x, int y, int initial, IntConsumer setter) {  
        addNumberField(x, y, initial > 0 ? String.valueOf(initial) : "", value -> {  
            try {  
                setter.accept(Integer.parseInt(value.trim()));  
            } catch (NumberFormatException ignored) {  
                if (value.isBlank()) setter.accept(0);  
            }  
        });  
    }  
  
    private void addDoubleField(int x, int y, double initial, double min, double max,  
                                DoubleConsumer setter) {  
        addNumberField(x, y, initial > 0 ? trimmed(initial) : "", value -> {  
            try {  
                setter.accept(Math.clamp(Double.parseDouble(value.trim()), min, max));  
            } catch (NumberFormatException ignored) {  
                if (value.isBlank()) setter.accept(0);  
            }  
        });  
    }  

    private void addNumberField(int x, int y, String initial, Consumer<String> responder) {  
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty());  
        box.setMaxLength(10);  
        box.setValue(initial);  
        box.setResponder(responder);  
        numberFields.add(box);
        tabContent.add(addRenderableWidget(box));   // NO StyledField, NO setBordered(false)  
    }
  
    private static String trimmed(double value) {  
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);  
    }  
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {  
        float eased = animEase();  
        int xOff = (int) Math.round((1f - eased) * 24); // slide in from +24px  
    
        // Panel background + border (static)  
        graphics.fill(bgLeft, bgTop, bgRight, bgBottom, PANEL_BG);  
        graphics.fill(bgLeft, bgTop, bgRight, bgTop + 1, PANEL_BORDER);  
        graphics.fill(bgLeft, bgBottom - 1, bgRight, bgBottom, PANEL_BORDER);  
        graphics.fill(bgLeft, bgTop, bgLeft + 1, bgBottom, PANEL_BORDER);  
        graphics.fill(bgRight - 1, bgTop, bgRight, bgBottom, PANEL_BORDER); 
    
        // Slide content widgets before they're drawn by super  
        for (var w : tabContent) w.setX(fieldX + xOff);  

        for (EditBox f : numberFields) {  
            int l = f.getX() - 1, t = f.getY() - 1;  
            int r = f.getX() + f.getWidth() + 1, b = f.getY() + f.getHeight() + 1;  
            int col = f.isFocused() ? HEADER_COLOR : PANEL_BORDER;  // accent when focused  
            graphics.fill(l, t, r, t + 1, col);   // top  
            graphics.fill(l, b - 1, r, b, col);   // bottom  
            graphics.fill(l, t, l + 1, b, col);   // left  
            graphics.fill(r - 1, t, r, b, col);   // right  
        }   
    
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);  
    
        // Title stays with center of Gui bg  
        String title = this.title.getString();  
        graphics.text(this.font, title, (bgLeft + bgRight) / 2 - this.font.width(title) / 2, titleY, LABEL_COLOR, true);  
    
        // Dividers, headers, labels: slide + fade together with widgets  
        for (int dy : dividers) {  
            graphics.fill(panelLeft + PANEL_PAD + xOff, dy, panelRight - PANEL_PAD + xOff, dy + 1,  
                withAlpha(PANEL_BORDER, eased));  
        }  
        for (Text h : headers) {  
            graphics.text(this.font, h.value(), h.x() + xOff, h.y(), withAlpha(h.color(), eased), true);  
        }  
        for (Text l : labels) {  
            graphics.text(this.font, l.value(), l.x() + xOff, l.y(), withAlpha(l.color(), eased), true);  
        }  
    }
  
    @Override  
    public void onClose() {  
        config.pristine = Math.clamp(config.pristine, 0, 100);  
        ShaftTracker.saveConfig();  
        super.onClose();  
    }  
  
    private void openWaypoints() {  
        this.minecraft.setScreen(new WaypointsScreen());  
    }  

    private void importSelectedPreset() {  
        if (presetFiles.isEmpty()) return;  
        Path path = FabricLoader.getInstance().getConfigDir()  
            .resolve("PresavedWaypoints")  
            .resolve(presetFiles.get(selectedPreset));  
        try {  
            String json = Files.readString(path);  
            Gson gson = new GsonBuilder().create();  
            JsonElement root = JsonParser.parseString(json);  
    
            JsonArray elements = new JsonArray();  
            if (root.isJsonArray()) {  
                elements = root.getAsJsonArray();  
            } else if (root.isJsonObject()) {  
                elements.add(root);  
            }  
    
            String headerGroup = null;  
            String headerIsland = "Mineshafts";  
            boolean firstElement = true;  
            boolean addedAny = false;  
    
            for (JsonElement el : elements) {  
                if (!el.isJsonObject()) { firstElement = false; continue; }  
                JsonObject obj = el.getAsJsonObject();  
    
                // Header detection: first element with group/area but no coords/options  
                boolean isHeader = firstElement  
                    && (obj.has("group") || obj.has("area"))  
                    && !obj.has("x") && !obj.has("y") && !obj.has("z")  
                    && !obj.has("options");  
                firstElement = false;  
                if (isHeader) {  
                    if (obj.has("group")) headerGroup = obj.get("group").getAsString();  
                    if (obj.has("area")) {  
                        String a = AreaDetector.getDisplayName(  
                            AreaDetector.fromDisplayName(obj.get("area").getAsString()));  
                        if (a != null && !a.isEmpty()) headerIsland = a;  
                    }  
                    continue;  
                }  
    
                Waypoint wp;  
                if (isSkyBlockFormat(obj)) {  
                    SbWaypoint sb = gson.fromJson(obj, SbWaypoint.class);  
                    wp = fromSbFormat(sb);  
                } else {  
                    wp = gson.fromJson(obj, Waypoint.class);  
                    if (wp != null) wp.setId(UUID.randomUUID().toString());  
                }  
    
                if (wp != null && wp.name != null && !wp.name.isEmpty()) {  
                    if (headerGroup != null) wp.group = headerGroup;  
                    wp.island = headerIsland;  
                    config.waypoints.add(wp);  
                    addedAny = true;  
                }  
            }  
    
            if (addedAny) {  
                ShaftTracker.saveConfig();  
                rebuildWidgets();  
            }  
        } catch (Exception ignored) {  
            // missing file or invalid JSON — ignore  
        }  
    }  
    
    private boolean isSkyBlockFormat(JsonObject obj) {  
        return obj.has("options") || obj.has("r") || obj.has("g") || obj.has("b");  
    }  
    
    private static class SbWaypoint {  
        double x, y, z;  
        float r, g, b;  
        Options options;  
        static class Options { String name; }  
    }  
    
    private Waypoint fromSbFormat(SbWaypoint s) {  
        int color = ((int)(s.r * 255) << 16)  
                | ((int)(s.g * 255) << 8)  
                |  (int)(s.b * 255);  
        Waypoint wp = new Waypoint();  
        wp.name = (s.options != null) ? s.options.name : null;  
        wp.x = (int) Math.round(s.x);  
        wp.y = (int) Math.round(s.y);  
        wp.z = (int) Math.round(s.z);  
        wp.color = color;  
        wp.enabled = true;  
        wp.setId(UUID.randomUUID().toString());  
        return wp;  
    }
}