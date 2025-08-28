package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.VisionOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At(value = "TAIL"))
    private static void inject$render(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        VisionOverlay.onRenderOverlayPost(guiGraphics);
    }
}
