package net.minecraft.client.renderer.rendertype;

import java.util.Objects;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.WaypointPipelineFactory;

import dev.shafthelper.mixin.RenderPipelinesInvoker;
import net.minecraft.client.renderer.RenderPipelines;

public final class WaypointRenderTypeFactory {
    public static RenderType createThroughLines() {
        RenderPipeline pipeline = RenderPipelinesInvoker.shafthelper$register(
            WaypointPipelineFactory.create(RenderPipelines.LINES_TRANSLUCENT)
        );
        assignIrisLinesProgram(Objects.requireNonNull(pipeline));
        RenderSetup setup = RenderSetup.builder(Objects.requireNonNull(pipeline))
            .bufferSize(RenderType.SMALL_BUFFER_SIZE)
            .createRenderSetup();
        return RenderType.create("shafthelper_waypoint_lines_through", setup);
    }

    private static void assignIrisLinesProgram(RenderPipeline pipeline) {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
            Class<?> irisProgramClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object linesProgram = irisProgramClass.getField("LINES").get(null);
            irisApiClass.getMethod("assignPipeline", RenderPipeline.class, irisProgramClass)
                .invoke(irisApi, pipeline, linesProgram);
        } catch (ReflectiveOperationException ignored) {
            // Iris is optional; vanilla rendering does not need a program assignment.
        }
    }

    private WaypointRenderTypeFactory() {}
}
