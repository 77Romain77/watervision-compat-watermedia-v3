package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.client.screens.VisionScreen;
import me.srrapero720.watervision.compat.watermedia.WaterMediaDecoderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.players.MediaPlayer;

import java.net.URI;

@Mixin(value = VisionScreen.class, remap = false)
public abstract class VisionScreenDecoderFallbackMixin {

    /** Five seconds at 20 TPS: enough for metadata and the first GPU upload. */
    @Unique
    private static final int WATERVISION_SOFTWARE_FALLBACK_TICKS = 100;

    @Shadow
    private MediaPlayer videoPlayer;

    @Shadow
    private int waitingTicks;

    @Shadow
    private VisionScreen.Status status;

    @Shadow
    private URI playbackUri;

    @Shadow
    private MRL mrl;

    @Shadow
    private int mrlWaitTicks;

    @Shadow
    private boolean mrlReloadAttempted;

    @Shadow
    private boolean failedToCreatePlayer;

    @Shadow
    private int playerRecreateAttempts;

    @Unique
    private boolean watervision$softwareFallbackAttempted;

    @Invoker("releasePlayerOnly")
    protected abstract void watervision$releasePlayerOnly();

    @Invoker("resetPlayerStateForNewMrl")
    protected abstract void watervision$resetPlayerStateForNewMrl();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void watervision$retryWithoutHardwareDecoder(final CallbackInfo ci) {
        if (this.status != VisionScreen.Status.OPENING_GAME
                || this.videoPlayer == null
                || this.videoPlayer.texture() != 0
                || this.watervision$softwareFallbackAttempted
                || WaterMediaDecoderCompat.softwareForcedForSession()) {
            return;
        }

        final boolean playerError = this.videoPlayer.error();
        final boolean metadataReady = this.videoPlayer.width() > 0 && this.videoPlayer.height() > 0;
        if (!playerError && (!metadataReady || this.waitingTicks < WATERVISION_SOFTWARE_FALLBACK_TICKS)) {
            return;
        }

        this.watervision$softwareFallbackAttempted = true;
        final Boolean hardwareState = WaterMediaDecoderCompat.hardwareAccelerationState(this.videoPlayer);
        if (Boolean.FALSE.equals(hardwareState)) {
            WaterVision.LOGGER.warn(
                    "WaterVision texture is missing while WaterMedia already reports software decoding; keeping normal network/cache fallback: status={}, size={}x{}, uri={}",
                    this.videoPlayer.status(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
            return;
        }

        if (!WaterMediaDecoderCompat.forceSoftwareForSession()) {
            return;
        }

        WaterVision.LOGGER.warn(
                "WaterVision detected decoded video without an OpenGL texture; recreating the player with software decoding: status={}, hwAccel={}, waitTicks={}, size={}x{}, uri={}",
                this.videoPlayer.status(), hardwareState, this.waitingTicks, this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);

        this.watervision$releasePlayerOnly();
        this.failedToCreatePlayer = false;
        this.status = VisionScreen.Status.OPENING_GAME;
        this.mrl = MediaAPI.mrl(this.playbackUri);
        this.mrlWaitTicks = 0;
        this.mrlReloadAttempted = false;
        this.playerRecreateAttempts = 0;
        this.watervision$resetPlayerStateForNewMrl();

        // Do not let the original tick run its network/proxy timeout logic against
        // the player that has just been released. A fresh software player is made
        // by VisionScreen on the next client tick.
        ci.cancel();
    }
}
