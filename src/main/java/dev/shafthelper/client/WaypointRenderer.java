package dev.shafthelper.client; 

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.shafthelper.core.AreaDetector;
import dev.shafthelper.core.Waypoint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.WaypointRenderTypeFactory;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class WaypointRenderer {  
    private static final int MAX_RENDER_DISTANCE = 64;  
    private static boolean initialized = false;  
    private static final List<Waypoint> visibleWaypoints = new ArrayList<>();  
    private static final RenderType THROUGH_LINES = WaypointRenderTypeFactory.createThroughLines();
    public static void register() {  
        if (!initialized) {  
            ClientTickEvents.END_CLIENT_TICK.register(WaypointRenderer::onTick);  
            LevelRenderEvents.END_MAIN.register(WaypointRenderer::onLevelRender);
            LevelRenderEvents.COLLECT_SUBMITS.register(WaypointRenderer::onCollectSubmits);
            initialized = true;  
        }  
    }   
  
    private static void onTick(Minecraft client) {  
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

        for (Waypoint wp : visibleWaypoints) {  
            float r = ((wp.color >> 16) & 0xFF) / 255.0f;  
            float g = ((wp.color >> 8) & 0xFF) / 255.0f;  
            float b = (wp.color & 0xFF) / 255.0f;  
            float a = 1.0f; 
  
            poseStack.pushPose();  
            poseStack.translate((float)(wp.x - cam.x), (float)(wp.y - cam.y), (float)(wp.z - cam.z));  
  
            drawLineBox(poseStack, lineBuffer, r, g, b, a); 
  
            poseStack.popPose();  
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
    
    private static void updateVisibleWaypoints(Minecraft client) {  
        visibleWaypoints.clear();  
        if (client.player == null) return;  
        var player = client.player;

        dev.shafthelper.config.ModConfig config = ShaftTracker.config();
        if (config == null || config.waypoints.isEmpty()) return;  
  
        Optional<AreaDetector.Area> currentArea = ShaftTracker.currentArea();  
        String currentIsland = AreaDetector.getDisplayName(currentArea.orElse(AreaDetector.Area.UNKNOWN));  

        String shaftCode = ShaftTracker.currentShaft()  
            .map(dev.shafthelper.core.ShaftDetector.Shaft::code)  
            .orElse("");
  
        for (Waypoint waypoint : config.waypoints) {  
            if (!waypoint.enabled) continue;  
            if (!waypoint.island.isEmpty()) {  
                if (!waypoint.island.equalsIgnoreCase(currentIsland)) continue;  
            }  
            if (!groupMatchesShaft(waypoint.group, shaftCode)) continue;
            double distance = waypoint.distanceTo(  
                player.getBlockX(),  
                player.getBlockY(),  
                player.getBlockZ()  
            );  
            if (distance > MAX_RENDER_DISTANCE) continue;  
            visibleWaypoints.add(waypoint);  
        }  
    } 

    private static boolean groupMatchesShaft(String group, String shaftCode) {  
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
    
        String name = Objects.requireNonNull(wp.getDisplayName());  
        submitter.submitNameTag(
            poseStack,
            new Vec3(0.5, 0.55, 0.5),
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
