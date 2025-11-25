package me.srrapero720.watervision.client.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.common.commands.EngineType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.watermedia.api.media.platforms.ALEngine;
import org.watermedia.api.media.platforms.GLEngine;
import org.watermedia.api.media.players.FFMediaPlayer;
import org.watermedia.api.media.players.MediaPlayer;
import org.watermedia.api.media.players.VLMediaPlayer;
import org.watermedia.api.util.MathUtil;
import org.watermedia.tools.functions.TexSubImage2DFunction;

import java.net.URI;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class QuickVisionScreen extends Screen {
    private static final DateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "quick_video_texture");
    private static final GLEngine GL_ENGINE = new GLEngine.Builder()
            .setGenTexture(GlStateManager::_genTexture)
            .setBindTexture((target, texture) -> GlStateManager._bindTexture(texture))
            .setTexParameter(GlStateManager::_texParameter)
            .setPixelStore(GlStateManager::_pixelStore)
            .setDelTexture(GlStateManager::_deleteTexture)
            .setTexImage2D((target, level, internalFormat, width, height, border, format, type, pixels) -> {
                    GlStateManager._texImage2D(target, level, internalFormat, width, height, border, format, type, pixels.asIntBuffer());
            })
            .setTexSubImage2D(GL11::glTexSubImage2D)
            .build();
    private static final ALEngine AL_ENGINE = new ALEngine.Builder().build();

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("GMT-00:00"));
    }

    private final boolean stretch;
    private final boolean controls;
    private final boolean exit;
    // PLAYER
    private final MediaPlayer videoPlayer;
    private final TextureWrapper textureWrapper;

    // STATE
    public QuickVisionScreen(final URI uri, final int volume, final float speed, final boolean stretch, final boolean controls, final boolean exit, EngineType engineType) {
        super(Component.literal("WaterVision"));
        this.stretch = stretch;
        this.controls = controls;
        this.exit = exit;

        this.videoPlayer = switch (engineType) {
            case VLC -> new VLMediaPlayer(uri, Minecraft.getInstance().gameThread, Minecraft.getInstance(), GL_ENGINE, AL_ENGINE, true, true);
            case FFMPEG -> new FFMediaPlayer(uri, Minecraft.getInstance().gameThread, Minecraft.getInstance(), GL_ENGINE, AL_ENGINE, true, true);
        };
        this.videoPlayer.volume(Mth.clamp(volume, 0, 100));
        this.videoPlayer.speed(Mth.clamp(speed, 0.1f, 3f));
        this.videoPlayer.repeat(false);

        this.textureWrapper = new TextureWrapper(this.videoPlayer.texture());
        Minecraft.getInstance().getTextureManager().register(TEXTURE, this.textureWrapper);
        this.videoPlayer.start();
        Minecraft.getInstance().getSoundManager().pause();
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int pMouseX, final int pMouseY, final float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, MathUtil.argb(200, 0, 0, 0)); // Black background with variable alpha
        if (this.stretch) {
            this.render$blit(guiGraphics, TEXTURE, 1, 0, 0, 0, 0, this.width, this.height);
        } else {
            final AspectRatioDimension dim = this.render$getAspectRatio(this.width, this.height, this.videoPlayer.width(), this.videoPlayer.height());
            this.render$blit(guiGraphics, TEXTURE, 1, dim.x, dim.y, 0, 0, dim.width, dim.height);
        }

        // DEBUG
        guiGraphics.drawString(this.font, String.format("State: %s", this.videoPlayer.status().name()), 0, (this.height / 2) - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, String.format("Time: %s (%s) / %s (%s)", FORMAT.format(new Date(this.videoPlayer.time())), this.videoPlayer.time(), FORMAT.format(new Date(this.videoPlayer.duration())), this.videoPlayer.duration()), 0, (this.height / 2), 0xFFFFFF);
        guiGraphics.drawString(this.font, String.format("Media Duration: %s (%s)", FORMAT.format(new Date(this.videoPlayer.duration())), this.videoPlayer.duration()), 0, (this.height / 2) + 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, String.format("Orchestrator Status: %s", "N/A"), 0, (this.height / 2) + 24, 0xFFFFFF);
        guiGraphics.drawString(this.font, String.format("Video Size: %sx%s", this.videoPlayer.width(), this.videoPlayer.height()), 0, (this.height / 2) + 36, 0xFFFFFF);
    }

    private AspectRatioDimension render$getAspectRatio(final int screenWidth, final int screenHeight, final int videoWidth, final int videoHeight) {
        final float containerAspectRatio = (float) screenWidth / (float) screenHeight;
        final float videoAspectRatio = (float) videoWidth / (float) videoHeight;

        final int renderWidth, renderHeight;

        if (videoAspectRatio > containerAspectRatio) {
            renderWidth = screenWidth;
            renderHeight = (int) (screenWidth / videoAspectRatio);
        } else {
            renderWidth = (int) (screenHeight * videoAspectRatio);
            renderHeight = screenHeight;
        }

        final int offsetX = (screenWidth - renderWidth) / 2;
        final int offsetY = (screenHeight - renderHeight) / 2;

        return new AspectRatioDimension(offsetX, offsetY, renderWidth, renderHeight);
    }

    private void render$blit(final GuiGraphics graphics, final ResourceLocation texture, final float alpha, final int x, final int y, final int offsetX, final int offsetY, final int width, final int height) {
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
//        final int tex = Minecraft.getInstance().textureManager.getTexture(texture).getId();
        RenderSystem.bindTexture(textureWrapper.getId());
        RenderSystem.setShaderTexture(0, textureWrapper.getId());
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

    @Override
    public void renderBackground(final GuiGraphics guiGraphics) {}

    @Override
    public void tick() {
        if (this.videoPlayer.ended()) {
            this.onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.exit;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().getSoundManager().resume();
        this.videoPlayer.release();
        super.onClose();
    }


    public record AspectRatioDimension(int x, int y, int width, int height) { }
}
