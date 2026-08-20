package dev.shafthelper.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionMixin {

    @Inject(method = "destroyBlock", at = @At(value = "RETURN"), cancellable = true)
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            // Referencing these directly in the block block is safe 
            // as long as the class names are not triggered in static initialization.
            // If it continues to throw, use an Interface Bridge like we did for Connection!
            dev.shafthelper.client.EfficiencyDisplay.onBlockMined();
            dev.shafthelper.client.MiningCalculator.onBlockMined();
        }
    }
}