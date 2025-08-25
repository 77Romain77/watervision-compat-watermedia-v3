package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.VisionOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Shadow @Final private LayeredDraw layers;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void inject$init(Minecraft minecraft, CallbackInfo ci) {
        this.layers.add(new VisionOverlay());
    }
}
