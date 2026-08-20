package dev.shafthelper.mixin;  

import dev.shafthelper.config.ModConfig;  
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleEngine.class})  
public class ParticleEngineMixin {  
    @Inject(
        method = "createParticle", 
        at = @At("HEAD"), 
        cancellable = true
    )  
    private void onCreateParticle(ParticleOptions options, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {  
        // Prevent early class loading by making sure we only process this logic 
        // if a DUST particle is explicitly being evaluated during active gameplay.
        if (options != null && options.getType() == ParticleTypes.DUST) {  
            // Moving the ShaftTracker evaluation down here prevents it from loading during boot!
            ModConfig config = dev.shafthelper.client.ShaftTracker.config();  
            if (config != null && !config.enableDustParticles) {  
                cir.setReturnValue(null);  
            }
        }
    }  
}