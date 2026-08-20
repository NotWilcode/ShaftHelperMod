package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class WaypointPipelineFactory {
    public static RenderPipeline create(RenderPipeline base) {
        return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("shafthelper", "pipeline/waypoint_lines_through"))
            .withShaderDefine("shad")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();
    }

    private WaypointPipelineFactory() {}
}
