package me.srrapero720.watervision;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;

public class WaterVisionClient {
    public static final int DEF_VOLUME = 100;
    public static final float DEF_SPEED = 1.0f;
    public static final boolean DEF_STRETCH = false;
    public static final float DEF_GAME_FADE_DURATION = 20.0f;
    public static final float DEF_VIDEO_FADE_DURATION = 20.0f;
    public static final boolean DEF_CONTROLS = true;
    public static final boolean DEF_EXIT = true;


    @OnlyIn(Dist.CLIENT)
    public static void openScreen(final URI uri, final int volume, final float speed, final boolean stretchVideo, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        Minecraft.getInstance().setScreen(new VisionScreen(uri, volume, speed, stretchVideo, gameFadeDuration, videoFadeDuration, controls, exit));
    }

    @OnlyIn(Dist.CLIENT)
    public static void closeScreen() {
        if (Minecraft.getInstance().screen instanceof VisionScreen) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void openOverlay(final URI uri) {
        VisionOverlay.uri = uri;
    }

    @OnlyIn(Dist.CLIENT)
    public static void closeOverlay() {
        VisionOverlay.uri = null;
    }

    @OnlyIn(Dist.CLIENT)
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
        final int tex = Minecraft.getInstance().textureManager.getTexture(texture).getId();
        RenderSystem.bindTexture(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        final Matrix4f matrix4f = graphics.pose().last().pose();
        final BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(matrix4f, pX1, pY1, pBlitOffset).uv(pMinU, pMinV).endVertex();
        bufferbuilder.vertex(matrix4f, pX1, pY2, pBlitOffset).uv(pMinU, pMaxV).endVertex();
        bufferbuilder.vertex(matrix4f, pX2, pY2, pBlitOffset).uv(pMaxU, pMaxV).endVertex();
        bufferbuilder.vertex(matrix4f, pX2, pY1, pBlitOffset).uv(pMaxU, pMinV).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.disableBlend();
    }
}
