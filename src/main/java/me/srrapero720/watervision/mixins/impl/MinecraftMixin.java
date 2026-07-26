package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.VisionOverlay;
import me.srrapero720.watervision.client.audio.CinematicAudioMute;
import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow private volatile boolean pause;

    @Inject(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.ISTORE, shift = At.Shift.AFTER))
    public void inject$pause(final boolean pRenderLevel, final CallbackInfo ci) {
        VisionOverlay.onClientPause(this.pause);

        final Minecraft minecraft = (Minecraft) (Object) this;
        final boolean cinematicScreenActive = minecraft.screen instanceof VisionScreen;
        final boolean stateChanged = CinematicAudioMute.setActive(cinematicScreenActive);

        // Safety net for unexpected screen replacement/disconnection paths where
        // VisionScreen.closeAndRelease() was not reached.
        if (stateChanged && !cinematicScreenActive) {
            minecraft.getSoundManager().resume();
        }
    }
}
