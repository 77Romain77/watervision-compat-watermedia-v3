package me.srrapero720.watervision;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

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

        graphics.drawSpecial(multiBufferSource -> {
            final VertexConsumer bufferbuilder = multiBufferSource.getBuffer(RenderType.guiTextured(texture));
            final Matrix4f matrix4f = graphics.pose().last().pose();
            bufferbuilder.addVertex(matrix4f, pX1, pY1, pBlitOffset).setColor(0xFFFFFFFF).setUv(pMinU, pMinV);
            bufferbuilder.addVertex(matrix4f, pX1, pY2, pBlitOffset).setColor(0xFFFFFFFF).setUv(pMinU, pMaxV);
            bufferbuilder.addVertex(matrix4f, pX2, pY2, pBlitOffset).setColor(0xFFFFFFFF).setUv(pMaxU, pMaxV);
            bufferbuilder.addVertex(matrix4f, pX2, pY1, pBlitOffset).setColor(0xFFFFFFFF).setUv(pMaxU, pMinV);
        });
    }
}
