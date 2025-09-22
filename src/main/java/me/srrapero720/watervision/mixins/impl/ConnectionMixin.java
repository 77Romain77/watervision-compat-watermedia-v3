package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.VisionOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ConnectionMixin {

    @Inject(method = "disconnect", at = @At("HEAD"))
    public void inject$onDisconnect(CallbackInfo ci) {
        VisionOverlay.onClientDisconnect();
    }
}
