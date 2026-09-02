package dev.shafthelper.mixin;  
  
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;  
import net.minecraft.world.InteractionHand;  
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;  
import net.minecraft.world.phys.BlockHitResult;  
import org.spongepowered.asm.mixin.Mixin;  
import org.spongepowered.asm.mixin.injection.At;  
import org.spongepowered.asm.mixin.injection.Inject;  
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;  
  
@Mixin(MultiPlayerGameMode.class)  
public class ClientPlayerInteractionMixin {  
  
    @Inject(method = "destroyBlock", at = @At(value = "RETURN"), cancellable = true)  
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {  
        if (cir.getReturnValue()) {  
            dev.shafthelper.client.EfficiencyDisplay.onBlockMined();  
            dev.shafthelper.client.MiningCalculator.onBlockMined();  
        }  
    }  
  
    // Right-click with item in hand (deploying the lantern)  
    @Inject(method = "useItem", at = @At("HEAD"))  
    private void shaftHelper$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {  
        dev.shafthelper.client.PlayerTimers.onDeployableUse(player.getItemInHand(hand));  
    }  
  
    // Right-click against a block (deploying onto a surface)  
    @Inject(method = "useItemOn", at = @At("HEAD"))  
    private void shaftHelper$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {  
        dev.shafthelper.client.PlayerTimers.onDeployableUse(player.getItemInHand(hand));  
    }  
}