package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.client.audio.CinematicAudioMute;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "calculateVolume", at = @At("HEAD"), cancellable = true)
    private void watervision$muteMinecraftSounds(final float volume, final SoundSource source, final CallbackInfoReturnable<Float> cir) {
        if (CinematicAudioMute.isActive()) {
            cir.setReturnValue(0.0F);
        }
    }
}
