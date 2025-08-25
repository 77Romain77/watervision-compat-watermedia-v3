package me.srrapero720.watervision;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.client.screens.VisionScreen;
import me.srrapero720.watervision.common.commands.VisionCommands;
import me.srrapero720.watervision.common.network.VisionNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.watermedia.api.image.ImageAPI;

import java.net.URI;
import java.util.Objects;

public class WaterVisionClient implements ClientModInitializer {
    public static final int DEF_VOLUME = 100;
    public static final float DEF_SPEED = 1.0f;
    public static final boolean DEF_STRETCH = false;
    public static final float DEF_GAME_FADE_DURATION = 20.0f;
    public static final float DEF_VIDEO_FADE_DURATION = 20.0f;
    public static final boolean DEF_CONTROLS = true;
    public static final boolean DEF_EXIT = true;

    @Override
    public void onInitializeClient() {

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.getTextureManager().register(WaterVision.LOADING_ANIM_TEXTURE, new TextureWrapper.Renderer(ImageAPI.loadingGif("watervision")));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (WaterVision.ticks == Integer.MAX_VALUE) WaterVision.ticks = 0;
            WaterVision.ticks++;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                VisionCommands.registerClient(dispatcher)
        );

        VisionNetwork.initClient();
    }


    @Environment(EnvType.CLIENT)
    public static void openScreen(final URI uri, final int volume, final float speed, final boolean stretchVideo, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        Minecraft.getInstance().setScreen(new VisionScreen(uri, volume, speed, stretchVideo, gameFadeDuration, videoFadeDuration, controls, exit));
    }

    @Environment(EnvType.CLIENT)
    public static void closeScreen() {
        if (Minecraft.getInstance().screen instanceof VisionScreen) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Environment(EnvType.CLIENT)
    public static void openOverlay(final URI uri) {
        VisionOverlay.uri = uri;
    }

    @Environment(EnvType.CLIENT)
    public static void closeOverlay() {
        VisionOverlay.uri = null;
    }

    @Environment(EnvType.CLIENT)
    public static void internal$blit(final GuiGraphics graphics, final ResourceLocation texture, final float alpha, final int x, final int y, final int offsetX, final int offsetY, final int width, final int height) {
        final float pX1 = x;
        final float pX2 = x + width;
        final float pY1 = y;
        final float pY2 = y + height;
        final float pBlitOffset = 0.0f;
        final var pMinU = offsetX / width;
        final var pMaxU = (offsetX + width) / width;
        final var pMinV = offsetY / height;
        final var pMaxV = (offsetY + height) / height;

        RenderSystem.enableBlend();
        final int tex = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        RenderSystem.bindTexture(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        final Matrix4f matrix4f = graphics.pose().last().pose();
        final BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.addVertex(matrix4f, pX1, pY1, pBlitOffset).setUv(pMinU, pMinV);
        bufferbuilder.addVertex(matrix4f, pX1, pY2, pBlitOffset).setUv(pMinU, pMaxV);
        bufferbuilder.addVertex(matrix4f, pX2, pY2, pBlitOffset).setUv(pMaxU, pMaxV);
        bufferbuilder.addVertex(matrix4f, pX2, pY1, pBlitOffset).setUv(pMaxU, pMinV);
        BufferUploader.drawWithShader(Objects.requireNonNull(bufferbuilder.build()));
        RenderSystem.disableBlend();
    }
}
