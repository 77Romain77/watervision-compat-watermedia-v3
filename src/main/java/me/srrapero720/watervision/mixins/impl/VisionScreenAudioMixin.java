package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.client.audio.CinematicAudioMute;
import me.srrapero720.watervision.client.screens.VisionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(VisionScreen.class)
public class VisionScreenAudioMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void watervision$enableCinematicMute(final URI uri,
                                                 final int volume,
                                                 final float speed,
                                                 final boolean stretch,
                                                 final float gameFadeDuration,
                                                 final float videoFadeDuration,
                                                 final boolean controls,
                                                 final boolean exit,
                                                 final CallbackInfo ci) {
        CinematicAudioMute.setActive(true);
    }

    @Inject(method = "closeAndRelease", at = @At("HEAD"))
    private void watervision$disableCinematicMute(final CallbackInfo ci) {
        CinematicAudioMute.setActive(false);
    }
}
