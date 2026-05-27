package me.srrapero720.watervision.client.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.client.screens.widgets.FadeBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLLoader;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.GLEngine;
import org.watermedia.api.media.players.MediaPlayer;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class VisionScreen extends Screen {
    private static final String BUILD_TAG = "clean-no-reopen-no-poll";
    private static final DateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "video_texture");

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("GMT-00:00"));
    }

    private final URI uri;
    private final int volume;
    private final float speed;
    private final boolean stretch;
    private final boolean controls;
    private final boolean exit;

    private final MRL mrl;
    private MediaPlayer videoPlayer;
    private TextureWrapper textureWrapper;
    private boolean failedToCreatePlayer;
    private boolean released;
    private boolean resumeRequested;
    private boolean waitLogged;
    private int waitingTicks;

    private Status status = Status.OPENING_GAME;
    private final FadeBackground gameBackground;
    private final FadeBackground videoBackground;

    public VisionScreen(final URI uri, final int volume, final float speed, final boolean stretch, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        super(Component.literal("WaterVision"));
        this.uri = uri;
        this.volume = Mth.clamp(volume, 0, 100);
        this.speed = Mth.clamp(speed, 0.1f, 3f);
        this.stretch = stretch;
        this.controls = controls;
        this.exit = exit;

        this.gameBackground = new FadeBackground(gameFadeDuration);
        this.videoBackground = new FadeBackground(videoFadeDuration);
        this.videoBackground.forceFadeIn();

        this.mrl = MediaAPI.getMRL(uri.toString());
        Minecraft.getInstance().getSoundManager().pause();
        WaterVision.LOGGER.info("WaterVision screen opened [{}] for {}", BUILD_TAG, this.uri);
    }

    private void tryCreatePlayer() {
        if (this.released || this.videoPlayer != null || this.failedToCreatePlayer || !this.mrl.ready()) {
            return;
        }

        if (this.mrl.error()) {
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("Failed to load media resource: {}", this.uri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }

        this.videoPlayer = this.mrl.createPlayer(this.createGfxEngine(), this.createSfxEngine());
        if (this.videoPlayer == null) {
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia v3 failed to create a player for: {}", this.uri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }

        this.videoPlayer.volume(this.volume);
        this.videoPlayer.speed(this.speed);
        this.videoPlayer.repeat(false);

        this.textureWrapper = new TextureWrapper(() -> (int) this.videoPlayer.texture());
        Minecraft.getInstance().getTextureManager().register(TEXTURE, this.textureWrapper);
        this.videoPlayer.startPaused();
        WaterVision.LOGGER.info("WaterVision player created [{}] for {}", BUILD_TAG, this.uri);
    }

    private GLEngine createGfxEngine() {
        return new GLEngine.Builder(Thread.currentThread(), Minecraft.getInstance())
                .setGenTexture(GlStateManager::_genTexture)
                .setBindTexture((target, texture) -> GlStateManager._bindTexture(texture))
                .setTexParameter(GlStateManager::_texParameter)
                .setPixelStore(GlStateManager::_pixelStore)
                .setDelTexture(GlStateManager::_deleteTexture)
                .build();
    }

    private ALEngine createSfxEngine() {
        return ALEngine.buildDefault();
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int pMouseX, final int pMouseY, final float partialTick) {
        this.tryCreatePlayer();

        this.gameBackground.render(guiGraphics, this.width, this.height, this.status != Status.CLOSING_GAME, partialTick);

        final boolean videoReady = this.isVideoReady();
        if (this.videoPlayer != null && videoReady && (this.status == Status.OPENING_VIDEO || this.status == Status.CLOSING_VIDEO)) {
            if (this.stretch) {
                WaterVisionClient.internal$blit(guiGraphics, TEXTURE, 1, 0, 0, 0, 0, this.width, this.height);
            } else {
                final AspectRatioDimension dim = this.render$getAspectRatio(this.width, this.height, this.videoPlayer.width(), this.videoPlayer.height());
                WaterVisionClient.internal$blit(guiGraphics, TEXTURE, 1, dim.x, dim.y, 0, 0, dim.width, dim.height);
            }
        }

        if (this.status != Status.OPENING_GAME && this.status != Status.CLOSING_GAME) {
            this.videoBackground.render(guiGraphics, this.width, this.height, this.status == Status.CLOSING_VIDEO, partialTick);
        }

        if (!videoReady && this.gameBackground.isFadedIn() && this.status != Status.CLOSING_GAME) {
            this.renderLoadingIndicator(guiGraphics);
        }

        if (!FMLLoader.isProduction() && this.videoPlayer != null) {
            guiGraphics.drawString(this.font, String.format("State: %s", this.videoPlayer.status().name()), 0, (this.height / 2) - 12, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Time: %s (%s) / %s (%s)", FORMAT.format(new Date(this.videoPlayer.time())), this.videoPlayer.time(), FORMAT.format(new Date(this.videoPlayer.duration())), this.videoPlayer.duration()), 0, (this.height / 2), 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Media Duration: %s (%s)", FORMAT.format(new Date(this.videoPlayer.duration())), this.videoPlayer.duration()), 0, (this.height / 2) + 12, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Orchestrator Status: %s", this.status.name()), 0, (this.height / 2) + 24, 0xFFFFFF);
            guiGraphics.drawString(this.font, String.format("Video Size: %sx%s", this.videoPlayer.width(), this.videoPlayer.height()), 0, (this.height / 2) + 36, 0xFFFFFF);
        }
    }

    private boolean isVideoReady() {
        return this.videoPlayer != null && this.videoPlayer.texture() != 0 && this.videoPlayer.width() > 0 && this.videoPlayer.height() > 0;
    }

    private void renderLoadingIndicator(final GuiGraphics graphics) {
        final int y = this.height - 28;
        final int startX = this.width - 56;
        final int phase = (WaterVision.getTicks() / 6) % 4;

        for (int i = 0; i < 4; i++) {
            final int alpha = i == phase ? 255 : 90;
            final int color = ((alpha & 255) << 24) | 0xFFFFFF;
            final int x = startX + i * 10;
            graphics.fill(x, y, x + 5, y + 5, color);
        }
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

        return new AspectRatioDimension((screenWidth - renderWidth) / 2, (screenHeight - renderHeight) / 2, renderWidth, renderHeight);
    }

    @Override
    public void renderBackground(final GuiGraphics guiGraphics) {}

    @Override
    public void tick() {
        this.tryCreatePlayer();

        if (this.videoPlayer != null && !this.resumeRequested) {
            this.videoPlayer.resume();
            this.resumeRequested = true;
            WaterVision.LOGGER.info("WaterVision player resume requested [{}] for {}", BUILD_TAG, this.uri);
        }

        if (this.videoPlayer != null && !this.isVideoReady() && this.status == Status.OPENING_GAME) {
            this.waitingTicks++;
            if (this.waitingTicks == 20 || this.waitingTicks == 100 || this.waitingTicks == 200) {
                WaterVision.LOGGER.warn("WaterVision waiting for first video frame [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
            }
            if (this.waitingTicks >= 200 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
            }
        }

        switch (this.status) {
            case OPENING_GAME -> {
                if (this.gameBackground.isFadedIn() && this.isVideoReady()) {
                    this.status = Status.OPENING_VIDEO;
                    this.waitingTicks = 0;
                    WaterVision.LOGGER.info("WaterVision first frame ready [{}] for {}", BUILD_TAG, this.uri);
                }
            }
            case OPENING_VIDEO -> {
                if (this.videoPlayer != null && this.videoBackground.isFadedOut() && (this.videoPlayer.ended() || this.videoPlayer.stopped() || this.videoPlayer.error())) {
                    this.status = Status.CLOSING_VIDEO;
                }
            }
            case CLOSING_VIDEO -> {
                if (this.videoBackground.isFadedIn()) this.status = Status.CLOSING_GAME;
            }
            case CLOSING_GAME -> {
                if (this.gameBackground.isFadedOut()) {
                    this.closeAndRelease();
                    super.onClose();
                }
            }
        }
    }

    private void releasePlayerOnly() {
        if (this.videoPlayer != null) {
            this.videoPlayer.release();
            this.videoPlayer = null;
        }
        this.textureWrapper = null;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.exit;
    }

    @Override
    public void onClose() {
        if (this.status != Status.CLOSING_GAME) {
            if (this.videoPlayer != null && !this.videoPlayer.stopped() && !this.videoPlayer.ended() && !this.videoPlayer.error()) this.videoPlayer.stop();
            this.status = Status.CLOSING_VIDEO;
            return;
        }
        this.closeAndRelease();
        super.onClose();
    }

    private void closeAndRelease() {
        if (this.released) return;
        Minecraft.getInstance().getSoundManager().resume();
        this.releasePlayerOnly();
        this.released = true;
    }

    public record AspectRatioDimension(int x, int y, int width, int height) { }

    public enum Status {
        OPENING_GAME,
        OPENING_VIDEO,
        CLOSING_VIDEO,
        CLOSING_GAME,
    }
}
