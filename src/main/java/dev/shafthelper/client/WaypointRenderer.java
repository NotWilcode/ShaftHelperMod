package dev.shafthelper.client; 

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.joml.Matrix4fc;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.shafthelper.ShaftHelperClient;
import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Waypoint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.WaypointRenderTypeFactory;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class WaypointRenderer {  
    private static final float NAME_TAG_BASE_SCALE = 1.0f;
    private static final float NAME_TAG_DISTANCE_THRESHOLD = 12.0f;
    
    private static String activeOrderedId = null;  
    private static Waypoint activeTarget = null;   // for the nav line (fix 5)  
    private static Waypoint nextTarget = null;     // for the nav line (fix 5)  
    private static boolean jumpRequested = false;  // set by the keybind  
  
    private static final Map<Waypoint, Integer> displayColors = new HashMap<>();  
    private static boolean initialized = false;  
    private static final List<Waypoint> visibleWaypoints = new ArrayList<>();  
    private static final RenderType THROUGH_LINES = WaypointRenderTypeFactory.createThroughLines();  
  
    private static final KeyMapping.Category SHAFTHELPER_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("shafthelper", "shafthelper_category")
    );
    // Keybind: press to jump the active ordered target to the nearest waypoint.  
    public static final KeyMapping JUMP_KEY = new KeyMapping(  
        "key.shafthelper.jump_waypoint",  
        InputConstants.Type.KEYSYM,  
        GLFW.GLFW_KEY_N,  
        SHAFTHELPER_CATEGORY // Uses the object instead of a String
    );

    public static void register() {  
        if (!initialized) {  
            ClientTickEvents.END_CLIENT_TICK.register(WaypointRenderer::onTick);  
            LevelRenderEvents.END_MAIN.register(WaypointRenderer::onLevelRender);
            LevelRenderEvents.COLLECT_SUBMITS.register(WaypointRenderer::onCollectSubmits);
            initialized = true;  
        }  
    }   
  
    private static void onTick(Minecraft client) {  
        while (JUMP_KEY.consumeClick()) jumpRequested = true;  
        updateVisibleWaypoints(client);  
    } 
  
    private static void onLevelRender(LevelRenderContext context) {  
        if (visibleWaypoints.isEmpty()) return;  
  
        PoseStack poseStack = context.poseStack();  
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();  
        if (poseStack == null || bufferSource == null) return;  
  
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();  

        drawWaypointLines(poseStack, bufferSource, cam, THROUGH_LINES);
        bufferSource.endBatch(Objects.requireNonNull(THROUGH_LINES));
        drawWaypointLines(poseStack, bufferSource, cam, RenderTypes.linesTranslucent());
        bufferSource.endBatch(RenderTypes.linesTranslucent());
    }

    private static void drawWaypointLines(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cam, RenderType renderType) {
        VertexConsumer lineBuffer = bufferSource.getBuffer(Objects.requireNonNull(renderType));
        dev.shafthelper.config.ModConfig config = ShaftTracker.config();

        for (Waypoint wp : visibleWaypoints) {  
            int color = displayColors.getOrDefault(wp, wp.color);  
            float r = ((color >> 16) & 0xFF) / 255.0f;  
            float g = ((color >> 8) & 0xFF) / 255.0f;  
            float b = (color & 0xFF) / 255.0f;  
            float a = 1.0f;  
  
            poseStack.pushPose();  
            poseStack.translate((float)(wp.x - cam.x), (float)(wp.y - cam.y), (float)(wp.z - cam.z));  
  
            drawLineBox(poseStack, lineBuffer, r, g, b, a); 
  
            poseStack.popPose();  
        }  

        // Fix 5: line from the player's eyes to the active (and next) waypoint.  
        if (config != null && config.waypointLineEnabled && activeTarget != null) {  
            Vec3 from = Objects.requireNonNull(Minecraft.getInstance().player).position();  
            drawNavLine(poseStack, lineBuffer, cam, from, activeTarget, themedColor(config, 0));  
            if (nextTarget != null) {  
                drawNavLine(poseStack, lineBuffer, cam,  
                    new Vec3(activeTarget.x + 0.5, activeTarget.y + 0.5, activeTarget.z + 0.5),  
                    nextTarget, themedColor(config, 1));  
            }  
        }
    }

    private static int themedColor(dev.shafthelper.config.ModConfig config, int role) {
        return switch (role) {
            case 0 -> config.themeAccent;
            case 1 -> config.themeText;
            default -> config.themeTextOff;
        };
    }

    private static Integer orderOf(Waypoint wp) {  
        if (wp.name == null) return null;  
        try {  
            return Integer.parseInt(wp.name.trim());  
        } catch (NumberFormatException ignored) {  
            return null;  
        }  
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        if (visibleWaypoints.isEmpty()) return;

        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector submitter = context.submitNodeCollector();
        if (poseStack == null || submitter == null) return;

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        CameraRenderState cameraState = Minecraft.getInstance().gameRenderer
            .getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 playerPosition = Objects.requireNonNull(Minecraft.getInstance().player).position();
        for (Waypoint wp : visibleWaypoints) {
            drawLabel(poseStack, submitter, cameraState, playerPosition, cam, wp);
        }
    }

    private static void drawLineBox(PoseStack matrixStack, VertexConsumer buffer, float r, float g, float b, float a) {
        Matrix4fc mat = matrixStack.last().pose();

        // Bottom ring
        buffer.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(2.0f);

        // Top ring
        buffer.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(2.0f);

        // Vertical pillars
        buffer.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f);
        buffer.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f); 
        buffer.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(2.0f);
    }

    private static void drawNavLine(PoseStack poseStack, VertexConsumer buffer, Vec3 cam, Vec3 from, Waypoint to, int color) {  
        float r = ((color >> 16) & 0xFF) / 255.0f;  
        float g = ((color >> 8) & 0xFF) / 255.0f;  
        float b = (color & 0xFF) / 255.0f;  
        Matrix4fc mat = poseStack.last().pose();  
        float x1 = (float)(from.x - cam.x),        y1 = (float)(from.y - cam.y),        z1 = (float)(from.z - cam.z);  
        float x2 = (float)(to.x + 0.5 - cam.x),    y2 = (float)(to.y + 0.5 - cam.y),    z2 = (float)(to.z + 0.5 - cam.z);  
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;  
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);  
        if (len == 0) return;  
        nx /= len; ny /= len; nz /= len;  
        buffer.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1f).setNormal(nx, ny, nz).setLineWidth(2.0f);  
        buffer.addVertex(mat, x2, y2, z2).setColor(r, g, b, 1f).setNormal(nx, ny, nz).setLineWidth(2.0f);  
    }
    
    private static void updateVisibleWaypoints(Minecraft client) {  
        visibleWaypoints.clear();  
        displayColors.clear();  
        if (client.player == null) return;  
        var player = client.player;  
    
        dev.shafthelper.config.ModConfig config = ShaftTracker.config();  
        if (config == null) return;
    
        Optional<AreaDetector.Area> currentArea = ShaftTracker.currentArea();  
        String currentIsland = AreaDetector.getDisplayName(currentArea.orElse(AreaDetector.Area.UNKNOWN));  
    
        List<Waypoint> candidates = new ArrayList<>();  
        for (Waypoint waypoint : config.waypoints) {  
            if (!waypoint.enabled) continue;  
            if (!waypoint.island.isEmpty()) {  
                if (!waypoint.island.equalsIgnoreCase(currentIsland)) continue;  
            }  
            double distance = waypoint.distanceTo(  
                player.getBlockX(),  
                player.getBlockY(),  
                player.getBlockZ()  
            );  
            if (distance > config.orderedChunks) continue;  
            candidates.add(waypoint);  
        }  
        List<Waypoint> corpseWaypoints = new ArrayList<>();  
        if (config.corpseWaypointsEnabled) {  
            String shaftCode = ShaftTracker.currentShaft().map(shaft -> shaft.code()).orElse("");  
            for (Waypoint waypoint : ShaftHelperClient.corpseWaypoints()) {  
                if (!waypoint.island.isEmpty() && !waypoint.island.equalsIgnoreCase(currentIsland)) continue;  
                if (!groupMatchesShaft(waypoint.group, shaftCode)) continue;  
                if (CorpseFinder.isCandidateCleared(waypoint.x, waypoint.y, waypoint.z)) continue;  
                if (waypoint.distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ()) <= config.orderedChunks) {  
                    corpseWaypoints.add(waypoint);  
                 }  
            }  
        }  
        // Live detected corpses (real armor stands sent by the server).  
        for (Waypoint waypoint : CorpseFinder.detectedCorpses()) {  
            if (waypoint.distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ()) <= config.orderedChunks) {  
                corpseWaypoints.add(waypoint);  
            }  
        }

        Waypoint activeCorpse = CorpseFinder.activeCandidate();  
            if (activeCorpse != null) corpseWaypoints.add(activeCorpse);
    
        if (!config.orderedWaypointsEnabled) {  
            // Unchanged behavior: show all, use each waypoint's own color.  
            visibleWaypoints.addAll(candidates);  
            visibleWaypoints.addAll(corpseWaypoints);
            return;  
        }  
    
        // --- Ordered mode ---  
        List<Waypoint> ordered = new ArrayList<>();  
        for (Waypoint wp : candidates) {  
            if (orderOf(wp) != null) {  
                ordered.add(wp);  
            } else {  
                visibleWaypoints.add(wp);  
            }  
        }  
        ordered.sort(Comparator.comparingInt(WaypointRenderer::orderOf));  
  
        activeTarget = null;  
        nextTarget = null;  
  
        if (ordered.isEmpty()) {  
            activeOrderedId = null;  
            visibleWaypoints.addAll(corpseWaypoints);  
            return;  
        }  
  
        // Fix 2: jump-to-nearest on keybind press.  
        if (jumpRequested) {  
            jumpRequested = false;  
            Waypoint nearest = null;  
            double best = Double.MAX_VALUE;  
            for (Waypoint wp : ordered) {  
                double d = wp.distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ());  
                if (d < best) { best = d; nearest = wp; }  
            }  
            if (nearest != null) activeOrderedId = nearest.getId();  
        }  
  
        // Resolve the active waypoint by id (stable across the per-tick rebuild).  
        int activeIndex = -1;  
        for (int i = 0; i < ordered.size(); i++) {  
            if (ordered.get(i).getId().equals(activeOrderedId)) { activeIndex = i; break; }  
        }  
        // Not tracking anything valid yet -> start at the nearest.  
        if (activeIndex < 0) {  
            double best = Double.MAX_VALUE;  
            for (int i = 0; i < ordered.size(); i++) {  
                double d = ordered.get(i).distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ());  
                if (d < best) { best = d; activeIndex = i; }  
            }  
            activeOrderedId = ordered.get(activeIndex).getId();  
        }  
  
        // Advance when within range of the current target and a next one exists.  
        Waypoint current = ordered.get(activeIndex);  
        double distToCurrent = current.distanceTo(player.getBlockX(), player.getBlockY(), player.getBlockZ());  
        if (distToCurrent <= config.orderedDistance) {  
            activeIndex = (activeIndex + 1) % ordered.size();  
            activeOrderedId = ordered.get(activeIndex).getId();  
        } 
  
        // Fix 3: only render the current + next. Passed waypoints are dropped entirely.  
        activeTarget = ordered.get(activeIndex);  
        visibleWaypoints.add(activeTarget);  
        displayColors.put(activeTarget, themedColor(config, 0));  
        if (activeIndex + 1 < ordered.size()) {  
            nextTarget = ordered.get(activeIndex + 1);  
            visibleWaypoints.add(nextTarget);  
            displayColors.put(nextTarget, themedColor(config, 1));  
        }  
        visibleWaypoints.addAll(corpseWaypoints);  
    }

    public static boolean groupMatchesShaft(String group, String shaftCode) {  
        // No group -> always show (island-only, backward compatible)  
        if (group == null || group.isEmpty()) return true;  
    
        // Groups without an underscore aren't shaft-scoped -> always show  
        int gUnderscore = group.lastIndexOf('_');  
        if (gUnderscore < 0) return true;  
    
        // Group is shaft-scoped, but no shaft detected -> hide  
        if (shaftCode == null || shaftCode.isEmpty()) return false;  
    
        String groupVariant = group.substring(gUnderscore + 1);  
    
        // Wildcard on variant: "*_C" or "_C" -> match any shaft with that variant  
        if (group.startsWith("*_") || group.startsWith("_")) {  
            int sUnderscore = shaftCode.lastIndexOf('_');  
            String shaftVariant = (sUnderscore >= 0)  
                ? shaftCode.substring(sUnderscore + 1)  
                : shaftCode;  
            return groupVariant.equalsIgnoreCase(shaftVariant);  
        }  
    
        // Full code: exact match, e.g. "AMBE_1" only in AMBE_1  
        return group.equalsIgnoreCase(shaftCode);  
    }

    private static void drawLabel(PoseStack poseStack, SubmitNodeCollector submitter, CameraRenderState cameraState, Vec3 playerPosition, Vec3 cam, Waypoint wp) {
        poseStack.pushPose();  
        poseStack.translate((float)(wp.x - cam.x), (float)(wp.y - cam.y), (float)(wp.z - cam.z));

        double distanceToPlayer = playerPosition.distanceTo(new Vec3(wp.x, wp.y, wp.z));
        float distanceScale = (float) Math.max(NAME_TAG_DISTANCE_THRESHOLD, distanceToPlayer) / NAME_TAG_DISTANCE_THRESHOLD;
        float scale = NAME_TAG_BASE_SCALE * distanceScale;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.5F / scale, 0.5F / scale, 0.5F / scale);
    
        String name = Objects.requireNonNull(wp.getDisplayName());  
        submitter.submitNameTag(
            poseStack,
            Vec3.ZERO,
            0xFFFFFFFF,
            Component.literal(name),
            true,
            0xF000F0,
            playerPosition.distanceToSqr(wp.x, wp.y, wp.z),
            Objects.requireNonNull(cameraState)
        );
        poseStack.popPose();  
    }

    public static void close() {}
  
    private WaypointRenderer() {}  
}
