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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements NetworkSequenceTracker {

    @Unique
    private final ConcurrentHashMap<Integer, Long> shaftHelper$pendingActions =
            new ConcurrentHashMap<>();

    @Inject(method = "send", at = @At("HEAD"))
    private void shaftHelper$onSend(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundPlayerActionPacket action) {
            trackSent(action.getSequence());
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void shaftHelper$onReceive( ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci ) {  
        if (packet instanceof ClientboundBlockChangedAckPacket ack) {  
            trackAck(ack.sequence());          // ground-truth network RTT -> ServerStats.addPing  
        }  
  
        if (packet instanceof ClientboundBlockUpdatePacket update) {  
            BlockPos tracked = dev.shafthelper.client.MiningCalculator.getCurrentBlock();  
  
            if (tracked != null  
                    && update.getPos().equals(tracked)  
                    && update.getBlockState().isAir()) {  
  
                long start = dev.shafthelper.client.MiningCalculator.getMineStartWallMs();  
                if (start > 0) {  
                    long elapsed = System.currentTimeMillis() - start;  
                    dev.shafthelper.client.ServerStats.calibrateFromBreak(  
                        dev.shafthelper.client.MiningCalculator.getEstimatedTicks(), elapsed);  
                }  
  
                dev.shafthelper.client.MiningCalculator.onBlockMined();  
                dev.shafthelper.client.EfficiencyDisplay.onBlockMined();  
            }  
        }  
  
        if (packet instanceof ClientboundSetTimePacket time) {  
            dev.shafthelper.client.ServerStats.onServerTimeUpdate(time.gameTime());  
        }  
    }

    @Override
    public void trackSent(int sequence) {
        shaftHelper$pendingActions.put(sequence, System.nanoTime());

        if (shaftHelper$pendingActions.size() > 64) {
            shaftHelper$pendingActions.clear();
        }
    }

    @Override
    public void trackAck(int sequence) {
        Long sent = shaftHelper$pendingActions.remove(sequence);
        if (sent == null) {
            return;
        }

        long rtt = Math.round((System.nanoTime() - sent) / 1_000_000.0);

        if (rtt > 0 && rtt < 2000) {
            dev.shafthelper.client.ServerStats.addPing(rtt);
        }
    }
}