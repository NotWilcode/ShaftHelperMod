package dev.shafthelper.client;  
  
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;  
import com.google.gson.JsonParser;

import dev.shafthelper.config.ModConfig;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;  
import net.minecraft.network.chat.Component;  
  
/**  
 * Waypoints manager rendered as a grouped, inline-editable, scrollable table.  
 * Waypoints are grouped by their {@code group} field (e.g. "BP7") under a header  
 * row; each waypoint row exposes inline Name / X / Y / Z editors plus color and  
 * delete controls. An area selector in the top-right switches which island's  
 * waypoints are shown. Uses only vanilla widgets (no YACL).  
 */  
public final class WaypointsScreen extends Screen {  
  
    private static final int FIELD_HEIGHT = 18;  
    private static final int PANEL_PAD = 16;  
    private static final int HEADER_TOP = 14;  
  
    private static final int GROUP_HEADER_HEIGHT = 24;  
    private static final int ROW_HEIGHT = 22;  
    private static final int ROW_GAP = 2;  
  
    private static final int LABEL_COLOR = 0xFFE0E1DD;  
    private static final int HEADER_COLOR = 0xFF9DB4D0;  
    private static final int PANEL_BG = 0xE00D1B2A;  
    private static final int PANEL_BORDER = 0xFF1B263B;  
    private static final int LIST_BG = 0xD0152535;  
    private static final int LIST_BORDER = 0xFF2A3B55;  
    private static final int GROUP_BG = 0xFF1B2C42;  
  
    private final ModConfig config = ShaftTracker.config();  
  
    // Deferred text draws (label, x, y, color)  
    private final List<Text> texts = new ArrayList<>();  
  
    private int panelLeft, panelTop, panelRight, panelBottom, titleY;  
    private int listX, listY, listWidth, listHeight;  
    private int viewportTop, viewportBottom;  
  
    private int scrollOffset = 0;  
    private int contentHeight = 0;  
    private final java.util.Set<String> collapsedGroups = new java.util.HashSet<>();
  
    private AreaDetector.Area filterArea = AreaDetector.Area.DWARVEN_MINES;  
  
    private record Text(String value, int x, int y, int color) {}  
  
    public WaypointsScreen() {  
        super(Component.literal("Waypoints Manager"));  
    }  
  
    @Override  
    protected void init() {  
        texts.clear();  
  
        panelLeft = PANEL_PAD;  
        panelRight = this.width - PANEL_PAD;  
        panelTop = HEADER_TOP;  
        panelBottom = this.height - PANEL_PAD;  
        titleY = PANEL_PAD;  
  
        // Area selector (top-right of the panel)  
        addAreaSelector();  
  
        // List viewport  
        listX = panelLeft + 8;  
        listWidth = (panelRight - panelLeft) - 16;  
        listY = panelTop + 40;  
        listHeight = (panelBottom - 24) - listY;  
        viewportTop = listY;  
        viewportBottom = listY + listHeight;  
  
        buildRows();  
  
        // Bottom buttons  
        int bottomY = panelBottom + 6;  
        int bw = 90;  
        int gap = 8;  
        int totalW = bw * 4 + gap * 3;  
        int startX = (this.width - totalW) / 2;  
  
        addRenderableWidget(Button.builder(Component.literal("New Group"), b -> addNewGroup())  
            .bounds(startX, bottomY, bw, FIELD_HEIGHT).build());  
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> importWaypoints())  
            .bounds(startX + (bw + gap), bottomY, bw, FIELD_HEIGHT).build());  
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> exportWaypoints())  
            .bounds(startX + (bw + gap) * 2, bottomY, bw, FIELD_HEIGHT).build());  
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())  
            .bounds(startX + (bw + gap) * 3, bottomY, bw, FIELD_HEIGHT).build());  
    }  
  
    private void addAreaSelector() {  
        int bw = 130;  
        int x = panelRight - PANEL_PAD - bw;  
        int y = panelTop + 10;  
        String label = "Area: " + AreaDetector.getDisplayName(filterArea);  
        addRenderableWidget(Button.builder(Component.literal(label), b -> {  
            filterArea = (filterArea == AreaDetector.Area.DWARVEN_MINES)  
                ? AreaDetector.Area.MINESHAFTS  
                : AreaDetector.Area.DWARVEN_MINES;  
            scrollOffset = 0;  
            rebuild();  
        }).bounds(x, y, bw, FIELD_HEIGHT).build());  
    }  
  
    /**  
     * Builds the grouped rows. Only widgets whose row is inside the visible  
     * viewport are actually created; everything else is skipped so scrolling a  
     * long list stays cheap. Row Y positions are offset by {@link #scrollOffset}.  
     */  
    private void buildRows() {  
        Map<String, List<Waypoint>> groups = getGroupedWaypoints();  
  
        int virtualY = 0; // position relative to the top of the scrollable content  
  
        for (Map.Entry<String, List<Waypoint>> entry : groups.entrySet()) {  
            String groupName = entry.getKey();  
            List<Waypoint> members = entry.getValue();  
  
            // --- Group header row ---  
            int headerScreenY = viewportTop + virtualY - scrollOffset;  
            if (isVisible(headerScreenY, GROUP_HEADER_HEIGHT)) {  
                addGroupHeader(groupName, members, headerScreenY);  
            }  
            virtualY += GROUP_HEADER_HEIGHT;  
  
            // --- Waypoint rows (skipped entirely when the group is collapsed) --- 
            if (collapsedGroups.contains(groupName)) {  
                for (Waypoint wp : members) {  
                    int rowScreenY = viewportTop + virtualY - scrollOffset;  
                    if (isVisible(rowScreenY, ROW_HEIGHT)) {  
                        addWaypointRow(wp, rowScreenY);  
                    }  
                    virtualY += ROW_HEIGHT + ROW_GAP;  
                }  
            } 
  
            virtualY += 6; // gap between groups  
        }  
  
        contentHeight = virtualY;  
    }  
  
    private boolean isVisible(int screenY, int rowHeight) {  
        return screenY + rowHeight > viewportTop && screenY < viewportBottom;  
    }  
  
    private void addGroupHeader(String groupName, List<Waypoint> members, int y) {  
        int x = listX + 4;  
        int rightEdge = listX + listWidth - 4;  
    
        // Collapse/expand toggle (far left of the header)  
        int arrowW = 14;  
        boolean collapsed = !collapsedGroups.contains(groupName);  
        addRenderableWidget(Button.builder(  
            Component.literal(collapsed ? "\u25B6" : "\u25BC"), b -> {  
                if (!collapsedGroups.remove(groupName)) {  
                    collapsedGroups.add(groupName);  
                }  
                rebuild();  
            }).bounds(x, y + 3, arrowW, FIELD_HEIGHT).build());  
    
        // Editable group name (renames every member's group) — shifted right past the arrow  
        int nameX = x + arrowW + 4;  
        EditBox nameBox = new EditBox(this.font, nameX, y + 3, 120, FIELD_HEIGHT, Component.empty());  
        nameBox.setMaxLength(40);  
        nameBox.setValue(groupName);  
        nameBox.setResponder(val -> {  
            String newName = val.trim();  
            for (Waypoint wp : members) wp.group = newName;  
            ShaftTracker.saveConfig();  
        });  
        addRenderableWidget(nameBox); 
  
        // Delete-group button (top-right of the header)  
        int delW = 20;  
        int delX = rightEdge - delW;  
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {  
            config.waypoints.removeAll(members);  
            ShaftTracker.saveConfig();  
            rebuild();  
        }).bounds(delX, y + 3, delW, FIELD_HEIGHT).build());  
  
        // New-waypoint-in-group button  
        int newW = 100;  
        int newX = delX - 6 - newW;  
        addRenderableWidget(Button.builder(Component.literal("New Waypoint"), b -> {  
            Minecraft client = Minecraft.getInstance();  
            int px = 0, py = 0, pz = 0;  
            if (client.player != null) {  
                px = client.player.getBlockX();  
                py = client.player.getBlockY() - 1; // under the player's feet  
                pz = client.player.getBlockZ();  
            }  
            Waypoint wp = new Waypoint("Waypoint", px, py, pz, groupName,  
                AreaDetector.getDisplayName(filterArea));  
            config.waypoints.add(wp);  
            ShaftTracker.saveConfig();  
            rebuild();  
        }).bounds(newX, y + 3, newW, FIELD_HEIGHT).build());
    }  
  
    private void addWaypointRow(Waypoint wp, int y) {  
        int x = listX + 8;  
        int boxY = y + 2;  
  
        // Name  
        int nameW = 110;  
        EditBox nameBox = new EditBox(this.font, x, boxY, nameW, FIELD_HEIGHT, Component.empty());  
        nameBox.setMaxLength(30);  
        nameBox.setValue(wp.name == null ? "" : wp.name);  
        nameBox.setResponder(val -> { wp.name = val; ShaftTracker.saveConfig(); });  
        addRenderableWidget(nameBox);  
        x += nameW + 10;  
  
        // X / Y / Z with labels  
        x = addCoordField(x, y, boxY, "X", () -> wp.x, v -> wp.x = v);  
        x = addCoordField(x, y, boxY, "Y", () -> wp.y, v -> wp.y = v);  
        x = addCoordField(x, y, boxY, "Z", () -> wp.z, v -> wp.z = v);  
  
        // Right-aligned color + delete controls  
        int rightEdge = listX + listWidth - 4;  
  
        int delW = 20;  
        int delX = rightEdge - delW;  
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {  
            config.waypoints.remove(wp);  
            ShaftTracker.saveConfig();  
            rebuild();  
        }).bounds(delX, boxY, delW, FIELD_HEIGHT).build());  
  
        int colW = 60;  
        int colX = delX - 6 - colW;  
        addRenderableWidget(Button.builder(Component.literal("Color"), b -> {  
            wp.color = cycleColor(wp.color);  
            ShaftTracker.saveConfig();  
        }).bounds(colX, boxY, colW, FIELD_HEIGHT).build());  
    }  
  
    private interface IntGetter { int get(); }  
    private interface IntSetter { void set(int v); }  
  
    private int addCoordField(int x, int rowY, int boxY, String label,  
                              IntGetter getter, IntSetter setter) {  
        texts.add(new Text(label, x, boxY + 5, LABEL_COLOR));  
        int labelW = this.font.width(label) + 4;  
        int boxX = x + labelW;  
        int boxW = 46;  
  
        EditBox box = new EditBox(this.font, boxX, boxY, boxW, FIELD_HEIGHT, Component.empty());  
        box.setMaxLength(8);  
        box.setValue(String.valueOf(getter.get()));  
        box.setResponder(val -> {  
            try {  
                setter.set(Integer.parseInt(val.trim()));  
                ShaftTracker.saveConfig();  
            } catch (NumberFormatException ignored) {  
                // keep last valid value until a parseable number is typed  
            }  
        });  
        addRenderableWidget(box);  
  
        return boxX + boxW + 10;  
    }  
  
    /** Cycles through a small palette so the color button is usable without a picker. */  
    private static final int[] PALETTE = {  
        0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0x7F00FF, 0xFFFFFF  
    };  
  
    private int cycleColor(int current) {  
        for (int i = 0; i < PALETTE.length; i++) {  
            if (PALETTE[i] == (current & 0xFFFFFF)) {  
                return PALETTE[(i + 1) % PALETTE.length];  
            }  
        }  
        return PALETTE[0];  
    }  
  
    private void addNewGroup() {  
        String base = "New Group";  
        Waypoint wp = new Waypoint("Waypoint", 0, 0, 0, base,  
            AreaDetector.getDisplayName(filterArea));  
        config.waypoints.add(wp);  
        ShaftTracker.saveConfig();  
        rebuild();  
    }  
  
    /** Filtered by current area, grouped by {@code group}, keeping groups/names sorted. */  
    private Map<String, List<Waypoint>> getGroupedWaypoints() {  
        String areaName = AreaDetector.getDisplayName(filterArea);  
  
        List<Waypoint> filtered = new ArrayList<>();  
        for (Waypoint wp : config.waypoints) {  
            if (areaName.equalsIgnoreCase(wp.island)) filtered.add(wp);  
        }  
        filtered.sort(Comparator  
            .comparing((Waypoint w) -> w.group == null ? "" : w.group)  
            .thenComparing(w -> w.name == null ? "" : w.name));  
  
        Map<String, List<Waypoint>> groups = new LinkedHashMap<>();  
        for (Waypoint wp : filtered) {  
            String key = (wp.group == null || wp.group.isEmpty()) ? "Ungrouped" : wp.group;  
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(wp);  
        }  
        return groups;  
    }  
  
    private void importWaypoints() {  
        Minecraft client = Minecraft.getInstance();  
        if (client.keyboardHandler == null) return;  
        String clipboard = client.keyboardHandler.getClipboard();  
        if (clipboard == null || clipboard.trim().isEmpty()) return;  
        try {  
            Gson gson = new GsonBuilder().create();  
            JsonElement root = JsonParser.parseString(clipboard);  
    
            // Normalize to an array so single-object and array inputs share one path  
            JsonArray elements = new JsonArray();  
            if (root.isJsonArray()) {  
                elements = root.getAsJsonArray();  
            } else if (root.isJsonObject()) {  
                elements.add(root);  
            }  
    
            boolean addedAny = false;  
            for (JsonElement el : elements) {  
                if (!el.isJsonObject()) continue;  
                JsonObject obj = el.getAsJsonObject();  
    
                Waypoint wp;  
                if (isSkyBlockFormat(obj)) {  
                    SbWaypoint sb = gson.fromJson(obj, SbWaypoint.class);  
                    wp = fromSbFormat(sb);  
                } else {  
                    wp = gson.fromJson(obj, Waypoint.class);  
                    if (wp != null) wp.setId(UUID.randomUUID().toString());  
                }  
    
                if (wp != null && wp.name != null && !wp.name.isEmpty()) {  
                    config.waypoints.add(wp);  
                    addedAny = true;  
                }  
            }  
    
            if (addedAny) {  
                ShaftTracker.saveConfig();  
                rebuild();  
            }  
        } catch (Exception ignored) {  
            // invalid JSON  
        }  
    }  
    
    private boolean isSkyBlockFormat(JsonObject obj) {  
        return obj.has("options") || obj.has("r") || obj.has("g") || obj.has("b");  
    }
  
    private void exportWaypoints() {  
        Gson gson = new GsonBuilder().setPrettyPrinting().create();  
        String json = gson.toJson(config.waypoints);  
        Minecraft client = Minecraft.getInstance();  
        if (client.keyboardHandler != null) client.keyboardHandler.setClipboard(json);  
    }  
  
    @Override  
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {  
        if (mouseX >= listX && mouseX <= listX + listWidth  
            && mouseY >= viewportTop && mouseY <= viewportBottom) {  
            int step = (int) (-scrollY * ROW_HEIGHT);  
            int maxScroll = Math.max(0, contentHeight - listHeight);  
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + step));  
            rebuild();  
            return true;  
        }  
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);  
    }  
  
    private void rebuild() {  
        this.clearWidgets();  
        this.init();  
    }  
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {  
        // Panel background + border  
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);  
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_BORDER);  
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_BORDER);  
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_BORDER);  
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_BORDER);  
  
        // List viewport background  
        graphics.fill(listX, viewportTop, listX + listWidth, viewportBottom, LIST_BG);  
        graphics.fill(listX, viewportTop, listX + listWidth, viewportTop + 1, LIST_BORDER);  
        graphics.fill(listX, viewportBottom - 1, listX + listWidth, viewportBottom, LIST_BORDER);  
  
        // Group header + color-swatch backing strips (drawn behind the widgets)  
        drawGroupBackings(graphics);  
  
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);  
  
        // Title  
        String title = this.title.getString();  
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, titleY, LABEL_COLOR, true);  
  
        // Deferred field labels (X:/Y:/Z:)  
        for (Text t : texts) {  
            graphics.text(this.font, t.value(), t.x(), t.y(), t.color(), true);  
        }  
    }  
  
    /**  
     * Re-walks the same virtual layout used in {@link #buildRows()} to paint a  
     * subtle header strip and each waypoint's color swatch, clipped to the  
     * viewport. Kept in sync with buildRows()'s Y arithmetic.  
     */  
    private void drawGroupBackings(GuiGraphicsExtractor graphics) {  
        Map<String, List<Waypoint>> groups = getGroupedWaypoints();  
        int virtualY = 0;  
  
        for (Map.Entry<String, List<Waypoint>> entry : groups.entrySet()) {  
            int headerScreenY = viewportTop + virtualY - scrollOffset;  
            if (isVisible(headerScreenY, GROUP_HEADER_HEIGHT)) {  
                int top = Math.max(headerScreenY, viewportTop);  
                int bottom = Math.min(headerScreenY + GROUP_HEADER_HEIGHT, viewportBottom);  
                if (bottom > top) graphics.fill(listX + 1, top, listX + listWidth - 1, bottom, GROUP_BG);  
            }  
            virtualY += GROUP_HEADER_HEIGHT;  
  
            if (collapsedGroups.contains(entry.getKey())) {  
                for (Waypoint wp : entry.getValue()) {  
                    int rowScreenY = viewportTop + virtualY - scrollOffset;  
                    if (isVisible(rowScreenY, ROW_HEIGHT)) {  
                        int sw = 4;  
                        int top = Math.max(rowScreenY + 2, viewportTop);  
                        int bottom = Math.min(rowScreenY + ROW_HEIGHT - 2, viewportBottom);  
                        if (bottom > top) {  
                            graphics.fill(listX + 2, top, listX + 2 + sw, bottom, 0xFF000000 | (wp.color & 0xFFFFFF));  
                        }  
                    }  
                    virtualY += ROW_HEIGHT + ROW_GAP;  
                }  
            } 
            virtualY += 6;  
        }  
    }  

    // Wayport Importing Formation, matching SkyBlock format
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
        wp.x = (int) Math.round(-s.x);   // un-negate  
        wp.y = (int) Math.round(s.y);  
        wp.z = (int) Math.round(-s.z);   // un-negate  
        wp.color = color;  
        wp.enabled = true;  
        wp.group = "Imported";
        wp.island = AreaDetector.getDisplayName(filterArea);
        wp.setId(UUID.randomUUID().toString());  
        return wp;  
    }
  
    @Override  
    public void onClose() {  
        ShaftTracker.saveConfig();  
        super.onClose();  
    }  
}