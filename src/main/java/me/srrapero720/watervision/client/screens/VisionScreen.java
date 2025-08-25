package me.srrapero720.watervision.client.screens;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlStateManager;
import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.client.screens.widgets.FadeBackground;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class VisionScreen extends Screen {
    private static final DateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "video_texture");

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("GMT-00:00"));
    }

    private final boolean stretch;
    private final boolean controls;
    private final boolean exit;
    // PLAYER
    private static VideoPlayer videoPlayer;
    private static TextureWrapper textureWrapper;

    // STATE
    private Status status = Status.OPENING_GAME;
    private final FadeBackground gameBackground;
    private final FadeBackground videoBackground;

    public VisionScreen(final URI uri, final int volume, final float speed, final boolean stretch, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        super(Component.literal("WaterVision"));
        this.stretch = stretch;
        this.controls = controls;
        this.exit = exit;

        this.gameBackground = new FadeBackground(gameFadeDuration);
        this.videoBackground = new FadeBackground(videoFadeDuration);
        this.videoBackground.forceFadeIn();

        if (videoPlayer == null) {
            videoPlayer = new VideoPlayer(PlayerAPI.getFactory(), runable -> Minecraft.getInstance().execute(() -> {
                runable.run();
                GlStateManager._bindTexture(0);
            }));
            textureWrapper = new TextureWrapper(videoPlayer.texture(), videoPlayer.width(), videoPlayer.height());
            Minecraft.getInstance().getTextureManager().register(TEXTURE, textureWrapper);
        }

        videoPlayer.setVolume(Mth.clamp(volume, 0, 100));
        videoPlayer.setSpeed(Mth.clamp(speed, 0.1f, 3f));

        videoPlayer.startPaused(uri);
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int pMouseX, final int pMouseY, final float partialTick) {
        this.gameBackground.render(guiGraphics, this.width, this.height, this.status != Status.CLOSING_GAME, partialTick);

        if (this.status == Status.OPENING_VIDEO || this.status == Status.CLOSING_VIDEO) {
            videoPlayer.preRender();
            if (this.stretch) {
                WaterVisionClient.internal$blit(guiGraphics, TEXTURE, 1, 0, 0, 0, 0, this.width, this.height);
            } else {
                final AspectRatioDimension dim = this.render$getAspectRatio(this.width, this.height, videoPlayer.width(), videoPlayer.height());
                WaterVisionClient.internal$blit(guiGraphics, TEXTURE, 1, dim.x, dim.y, 0, 0, dim.width, dim.height);
            }
        }

        if (this.status != Status.OPENING_GAME && this.status != Status.CLOSING_GAME) {
            this.videoBackground.render(guiGraphics, this.width, this.height, this.status == Status.CLOSING_VIDEO, partialTick);
        }

        if (this.status == Status.OPENING_GAME || this.status == Status.CLOSING_VIDEO || this.status == Status.CLOSING_GAME || videoPlayer.isBuffering() || videoPlayer.isLoading()) {
            this.render$loadingIcon(guiGraphics, partialTick);
        }

        // DEBUG
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            if (!videoPlayer.isSafeUse()) return;
            guiGraphics.drawString(this.font, String.format("State: %s", videoPlayer.getStateName()), 0, (this.height / 2) - 12, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Time: %s (%s) / %s (%s)", FORMAT.format(new Date(videoPlayer.getTime())), videoPlayer.getTime(), FORMAT.format(new Date(videoPlayer.getDuration())), videoPlayer.getDuration()), 0, (this.height / 2), 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Media Duration: %s (%s)", FORMAT.format(new Date(videoPlayer.getMediaInfoDuration())), videoPlayer.getMediaInfoDuration()), 0, (this.height / 2) + 12, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Orchestrator Status: %s", this.status.name()), 0, (this.height / 2) + 24, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Video Size: %sx%s", videoPlayer.width(), videoPlayer.height()), 0, (this.height / 2) + 36, 0xFFFFFF);
        }
    }

    private void render$loadingIcon(final GuiGraphics graphics, final float partialTick) {
        WaterVisionClient.internal$blit(graphics, WaterVision.LOADING_ANIM_TEXTURE, 1, this.width - 40, this.height - 40, 0, 0, 40, 40);
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

    @Override
    public void tick() {
        switch (this.status) {
            case OPENING_GAME -> {
                if (this.gameBackground.isFadedIn() && videoPlayer.isSafeUse() && videoPlayer.isReady()) {
                    this.status = Status.OPENING_VIDEO;
                    videoPlayer.play();
                }
            }
            case OPENING_VIDEO -> {
                if (this.videoBackground.isFadedOut() && (videoPlayer.isEnded() || videoPlayer.isStopped() || videoPlayer.isBroken())) {
                    this.status = Status.CLOSING_VIDEO;
                }
            }
            case CLOSING_VIDEO -> {
                if (this.videoBackground.isFadedIn()) {
                    this.status = Status.CLOSING_GAME;
                }
            }
            case CLOSING_GAME -> {
                if (this.gameBackground.isFadedOut()) {
                    this.onClose();
                }
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.exit;
    }

    @Override
    public void onClose() {
        if (videoPlayer.isSafeUse() && videoPlayer.isPlaying()) {
            videoPlayer.stop();
            return;
        }
        if (this.status != Status.CLOSING_GAME) {
            return;
        }
        Minecraft.getInstance().getSoundManager().resume();
        super.onClose();
    }


    public record AspectRatioDimension(int x, int y, int width, int height) { }

    public enum Status {
        OPENING_GAME,
        OPENING_VIDEO,
        CLOSING_VIDEO,
        CLOSING_GAME,
    }
}
