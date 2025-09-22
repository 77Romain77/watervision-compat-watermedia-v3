package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.VisionOverlay;
import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "stopServer", at = @At(value = "HEAD"))
    public void inject$stopServer(CallbackInfo ci) {
        VisionOverlay.onClientDisconnect();
    }
}
