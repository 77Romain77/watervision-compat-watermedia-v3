package me.srrapero720.watervision.mixins.impl;

import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.client.screens.VisionScreen;
import me.srrapero720.watervision.common.config.WaterVisionServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(VisionScreen.class)
public abstract class VisionScreenFallbackMessageMixin {

    @Shadow(remap = false)
    @Final
    private URI uri;

    @Unique
    private boolean watervision$fallbackMessageShown;

    @Inject(method = "closeAndRelease()V", at = @At("RETURN"), remap = false)
    private void watervision$showBrowserFallbackMessage(final CallbackInfo ci) {
        if (this.watervision$fallbackMessageShown) return;
        this.watervision$fallbackMessageShown = true;

        try {
            if (!WaterVisionServerConfig.FALLBACK_MESSAGE_ENABLED.get()) return;

            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || this.uri == null) return;

            final String url = this.uri.toString();
            final MutableComponent message = this.watervision$parseMessage(
                    WaterVisionServerConfig.FALLBACK_MESSAGE_JSON.get()
            );
            if (message == null) return;

            final String hoverText = WaterVisionServerConfig.FALLBACK_HOVER_TEXT.get();
            message.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal(hoverText == null ? "" : hoverText)
                    ))
            );

            minecraft.gui.getChat().addMessage(message);
            WaterVision.LOGGER.debug("WaterVision browser fallback message displayed for {}", url);
        } catch (final Exception exception) {
            WaterVision.LOGGER.error("Failed to display WaterVision browser fallback message for {}", this.uri, exception);
        }
    }

    @Unique
    private MutableComponent watervision$parseMessage(final String configuredJson) {
        try {
            final MutableComponent configured = Component.Serializer.fromJson(configuredJson);
            if (configured != null) return configured;
        } catch (final Exception exception) {
            WaterVision.LOGGER.warn("Invalid fallback_message.message_json in WaterVision server config; using the default message", exception);
        }

        try {
            return Component.Serializer.fromJson(WaterVisionServerConfig.DEFAULT_FALLBACK_MESSAGE_JSON);
        } catch (final Exception exception) {
            WaterVision.LOGGER.error("Failed to parse WaterVision default browser fallback message", exception);
            return null;
        }
    }
}
