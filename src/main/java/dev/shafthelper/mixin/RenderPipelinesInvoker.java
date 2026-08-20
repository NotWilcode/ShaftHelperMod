package dev.shafthelper.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesInvoker {
    @Invoker("register")
    static RenderPipeline shafthelper$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
