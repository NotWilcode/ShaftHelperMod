package dev.shafthelper.mixin;

import dev.shafthelper.network.NetworkSequenceTracker;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;  
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements NetworkSequenceTracker {

    @Unique
    private record ShaftHelperPendingAction(ServerboundPlayerActionPacket.Action action,
                                             BlockPos position,
                                             Direction face,
                                             long sentNanoTime) {}

    @Unique
    private final ConcurrentHashMap<Integer, ShaftHelperPendingAction> shaftHelper$pendingActions =
            new ConcurrentHashMap<>();

    @Inject(method = "send", at = @At("HEAD"))
    private void shaftHelper$onSend(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundPlayerActionPacket action) {
            long sentWallMs = System.currentTimeMillis();
            long sentNanoTime = System.nanoTime();
            shaftHelper$pendingActions.put(action.getSequence(), new ShaftHelperPendingAction(
                action.getAction(), action.getPos(), action.getDirection(), sentNanoTime));

            Runnable clientAction = () ->
                    dev.shafthelper.client.MiningCalculator.onClientActionSent(
                            action.getAction(), action.getPos(), sentWallMs, sentNanoTime);
            if (Minecraft.getInstance().isSameThread()) {
                clientAction.run();
            } else {
                Minecraft.getInstance().execute(clientAction);
            }

            if (shaftHelper$pendingActions.size() > 64) {
                shaftHelper$pendingActions.clear();
            }
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void shaftHelper$onReceive( ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci ) {  
        if (packet instanceof ClientboundBlockChangedAckPacket ack) {  
            trackAck(ack.sequence());
        }  
  
        if (packet instanceof ClientboundBlockUpdatePacket update) {  
            BlockPos tracked = dev.shafthelper.client.MiningCalculator.getMiningBlock();
  
            if (tracked != null  
                    && update.getPos().equals(tracked)  
                    && update.getBlockState().isAir()) {  
  
                Minecraft.getInstance().execute(() -> {
                    if (update.getPos().equals(dev.shafthelper.client.MiningCalculator.getMiningBlock())) {
                        dev.shafthelper.client.MiningCalculator.onBlockMined();
                        dev.shafthelper.client.EfficiencyDisplay.onBlockMined();
                    }
                });
            }  
        }  
  
        if (packet instanceof ClientboundSetTimePacket time) {  
            dev.shafthelper.client.ServerStats.onServerTimeUpdate(time.gameTime());  
        }  
    }

    @Override
    public void trackSent(int sequence) {
        // Packet-specific tracking is performed in shaftHelper$onSend.
    }

    @Override
    public void trackAck(int sequence) {
        ShaftHelperPendingAction pending = shaftHelper$pendingActions.remove(sequence);
        if (pending == null) {
            return;
        }

        long acknowledgedNanoTime = System.nanoTime();
        long rtt = Math.round((acknowledgedNanoTime - pending.sentNanoTime()) / 1_000_000.0);

        if (rtt > 0 && rtt < 2000) {
            dev.shafthelper.client.ServerStats.addPing(rtt);
        }

        Minecraft.getInstance().execute(() ->
                dev.shafthelper.client.MiningCalculator.onServerActionAcknowledged(
                        pending.action(), pending.position(), acknowledgedNanoTime));
    }
}