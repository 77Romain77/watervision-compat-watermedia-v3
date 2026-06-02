package me.srrapero720.watervision.client.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import me.srrapero720.watervision.client.VisionClientSettings;
import me.srrapero720.watervision.client.render.TextureWrapper;
import me.srrapero720.watervision.client.screens.widgets.FadeBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.GLEngine;
import org.watermedia.api.media.players.MediaPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.function.Supplier;

public class VisionScreen extends Screen {
    private static final String BUILD_TAG = "cinematic-ui-watermedia-017-debug";
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "video_texture");
    private static final int TIPS_AUTO_HIDE_TICKS = 200;
    private static final int VOLUME_OVERLAY_TICKS = 20;
    private static final int SEEK_OVERLAY_TICKS = 20;
    private static final int VOLUME_STEP = 5;
    private static final int SEEK_STEP_MS = 5000;
    private static final int SEEK_COOLDOWN_TICKS = 5;
    private static final int RESUME_DELAY_TICKS = 3;
    private static final int EARLY_RECOVERY_UNTIL_TICK = 10;
    private static final int SKIP_HOLD_TICKS = 40;
    private static final int MAX_PLAYER_RECREATE_ATTEMPTS = 4;

    private final URI uri;
    private final int commandVolume;
    private final float speed;
    private final boolean stretch;
    private final boolean controls;
    private final boolean exit;
    private MRL mrl;
    private final FadeBackground gameBackground;
    private final FadeBackground videoBackground;

    private MediaPlayer videoPlayer;
    private TextureWrapper textureWrapper;
    private Status status = Status.OPENING_GAME;
    private boolean failedToCreatePlayer;
    private boolean released;
    private boolean resumeRequested;
    private int resumeDelayTicks;
    private boolean waitLogged;
    private boolean terminalAfterMaxLogged;
    private boolean recoveryAttempted;
    private int playerRecreateAttempts;
    private boolean firstTextureRenderLogged;
    private int waitingTicks;
    private boolean tipsVisible = true;
    private int tipsTicksLeft = TIPS_AUTO_HIDE_TICKS;
    private int volumeOverlayTicks;
    private int seekOverlayTicks;
    private int seekOverlayDirection;
    private int seekCooldownTicks;
    private int clientVolume;
    private boolean videoPaused;
    private boolean skipHolding;
    private int skipHoldTicks;
    private boolean skipTriggered;

    public VisionScreen(final URI uri, final int volume, final float speed, final boolean stretch, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        super(Component.literal("WaterVision"));
        this.uri = uri;
        this.commandVolume = Mth.clamp(volume, 0, 100);
        this.speed = Mth.clamp(speed, 0.1f, 3f);
        this.stretch = stretch;
        this.controls = controls;
        this.exit = exit;
        this.clientVolume = VisionClientSettings.cinematicVolume();
        this.gameBackground = new FadeBackground(gameFadeDuration);
        this.videoBackground = new FadeBackground(videoFadeDuration);
        this.videoBackground.forceFadeIn();
        this.mrl = MediaAPI.getMRL(uri.toString());
        Minecraft.getInstance().getSoundManager().pause();
        WaterVision.LOGGER.info("WaterVision screen opened [{}] for {}", BUILD_TAG, this.uri);
        WaterVision.LOGGER.info("WaterVision cinematic volume [{}]: command={} client={} effective={} controls={} exit={}", BUILD_TAG, this.commandVolume, this.clientVolume, this.effectiveVolume(), this.controls, this.exit);
    }

    private void tryCreatePlayer() {
        if (this.released || this.videoPlayer != null || this.failedToCreatePlayer || !this.mrl.ready()) return;
        if (this.mrlHasErrorCompat()) {
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia MRL failed before player creation [{}]: exception={}, uri={}", BUILD_TAG, this.mrlExceptionCompat(), this.uri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }
        this.videoPlayer = this.createCompatiblePlayer();
        if (this.videoPlayer == null) {
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia failed to create a player [{}] for {}", BUILD_TAG, this.uri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }
        this.applyEffectiveVolume();
        this.videoPlayer.speed(this.speed);
        this.videoPlayer.repeat(false);
        this.textureWrapper = new TextureWrapper(() -> (int) this.videoPlayer.texture());
        Minecraft.getInstance().getTextureManager().register(TEXTURE, this.textureWrapper);
        this.videoPlayer.startPaused();
        this.videoPaused = false;
        this.resumeDelayTicks = 0;
        WaterVision.LOGGER.info("WaterVision player created [{}] for {}", BUILD_TAG, this.uri);
    }

    private boolean mrlHasErrorCompat() {
        try {
            final Method method = this.mrl.getClass().getMethod("hasError");
            final Object result = method.invoke(this.mrl);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private Object mrlExceptionCompat() {
        try {
            final Method method = this.mrl.getClass().getMethod("exception");
            return method.invoke(this.mrl);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private MediaPlayer createCompatiblePlayer() {
        try {
            final Method method = MediaAPI.class.getMethod("createPlayer", MRL.class, Supplier.class, Supplier.class);
            final Object player = method.invoke(null, this.mrl, (Supplier<GLEngine>) this::createGfxEngine, (Supplier<ALEngine>) this::createSfxEngine);
            WaterVision.LOGGER.info("WaterVision using MediaAPI.createPlayer compatibility path [{}]", BUILD_TAG);
            return (MediaPlayer) player;
        } catch (final NoSuchMethodException ignored) {
        } catch (final Throwable throwable) {
            WaterVision.LOGGER.error("WaterVision failed to create player through MediaAPI.createPlayer [{}]", BUILD_TAG, throwable);
            return null;
        }
        try {
            final Class<?> gfxClass = Class.forName("org.watermedia.api.media.engines.GFXEngine");
            final Class<?> sfxClass = Class.forName("org.watermedia.api.media.engines.SFXEngine");
            final Method method = this.mrl.getClass().getMethod("createPlayer", gfxClass, sfxClass);
            final Object player = method.invoke(this.mrl, this.createGfxEngine(), this.createSfxEngine());
            WaterVision.LOGGER.info("WaterVision using MRL.createPlayer compatibility path [{}]", BUILD_TAG);
            return (MediaPlayer) player;
        } catch (final Throwable throwable) {
            WaterVision.LOGGER.error("WaterVision failed to create player through MRL.createPlayer [{}]", BUILD_TAG, throwable);
            return null;
        }
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
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.tryCreatePlayer();
        this.gameBackground.render(graphics, this.width, this.height, this.status != Status.CLOSING_GAME, partialTick);
        final boolean videoReady = this.isVideoReady();
        if (this.videoPlayer != null && videoReady && (this.status == Status.OPENING_VIDEO || this.status == Status.CLOSING_VIDEO)) {
            if (this.stretch) {
                WaterVisionClient.internal$blit(graphics, TEXTURE, 1, 0, 0, 0, 0, this.width, this.height);
            } else {
                final AspectRatioDimension dim = this.render$getAspectRatio(this.width, this.height, this.videoPlayer.width(), this.videoPlayer.height());
                WaterVisionClient.internal$blit(graphics, TEXTURE, 1, dim.x, dim.y, 0, 0, dim.width, dim.height);
            }
            if (!this.firstTextureRenderLogged) {
                this.firstTextureRenderLogged = true;
                WaterVision.LOGGER.info("WaterVision first video texture rendered [{}]: wmStatus={}, texture={}, size={}x{}, orchestrator={}, uri={}",
                        BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.status, this.uri);
            }
        }
        if (this.status != Status.OPENING_GAME && this.status != Status.CLOSING_GAME) {
            this.videoBackground.render(graphics, this.width, this.height, this.status == Status.CLOSING_VIDEO, partialTick);
        }
        if (!videoReady && this.gameBackground.isFadedIn() && this.status != Status.CLOSING_GAME) this.renderLoadingIndicator(graphics);
        this.renderCinematicUi(graphics);
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

    private void renderCinematicUi(final GuiGraphics graphics) {
        int bottomOffset = 16;
        if (this.volumeOverlayTicks > 0) {
            this.renderVolumeOverlay(graphics, 16, this.height - bottomOffset - 24);
            bottomOffset += 34;
        }
        if (this.tipsVisible) this.renderTips(graphics, 16, this.height - bottomOffset - this.tipsHeight());
        if (this.controls && this.seekOverlayTicks > 0) this.renderSeekOverlay(graphics);
        if (this.controls && this.videoPaused && this.status == Status.OPENING_VIDEO) this.renderPauseOverlay(graphics);
        if (this.exit && this.skipHolding && this.skipHoldTicks > 0 && !this.skipTriggered) this.renderSkipOverlay(graphics);
    }

    private int tipsHeight() {
        return this.tipsLineCount() * 11 + 4;
    }

    private int tipsLineCount() {
        int lines = 3;
        if (this.controls) lines += 2;
        if (this.exit) lines++;
        return lines;
    }

    private void renderTips(final GuiGraphics graphics, final int x, final int y) {
        final String[] lines = new String[6];
        int count = 0;
        lines[count++] = "Tips :";
        lines[count++] = "Masquer : touche K";
        lines[count++] = "Régler le volume : ↑ / ↓";
        if (this.controls) {
            lines[count++] = "Pause : Espace";
            lines[count++] = "Avancer / reculer : ← / →";
        }
        if (this.exit) lines[count++] = "Passer : maintenir Échap";

        int maxTextWidth = 0;
        for (int i = 0; i < count; i++) maxTextWidth = Math.max(maxTextWidth, this.font.width(lines[i]));
        final int width = Math.max(80, maxTextWidth + 10);
        final int height = this.tipsHeight();
        graphics.fill(x - 5, y - 5, x + width, y + height, 0xAA000000);
        for (int i = 0; i < count; i++) graphics.drawString(this.font, lines[i], x, y + i * 11, i == 0 ? 0xFFFFFF : 0xDDDDDD);
    }

    private void renderVolumeOverlay(final GuiGraphics graphics, final int x, final int y) {
        final int width = 146;
        final int height = 22;
        final int barWidth = 104;
        final int filled = Math.round(barWidth * (this.clientVolume / 100.0f));
        graphics.fill(x - 5, y - 5, x + width, y + height, 0xAA000000);
        graphics.drawString(this.font, "Volume vidéo", x, y, 0xFFFFFF);
        graphics.fill(x, y + 13, x + barWidth, y + 17, 0xFF555555);
        graphics.fill(x, y + 13, x + filled, y + 17, 0xFFFFFFFF);
        graphics.drawString(this.font, this.clientVolume + "%", x + barWidth + 8, y + 9, 0xFFFFFF);
    }

    private void renderSeekOverlay(final GuiGraphics graphics) {
        final boolean forward = this.seekOverlayDirection > 0;
        final String text = forward ? "+5s" : "-5s";
        final int boxWidth = 52;
        final int boxHeight = 28;
        final int x = forward ? this.width - boxWidth - 32 : 32;
        final int y = this.height / 2 - boxHeight / 2;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        graphics.drawString(this.font, text, x + (boxWidth - this.font.width(text)) / 2, y + 10, 0xFFFFFF);
    }

    private void renderPauseOverlay(final GuiGraphics graphics) {
        final String text = "Pause";
        final int boxWidth = 68;
        final int boxHeight = 24;
        final int x = (this.width - boxWidth) / 2;
        final int y = this.height - boxHeight - 34;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        graphics.drawString(this.font, text, x + (boxWidth - this.font.width(text)) / 2, y + 8, 0xFFFFFF);
    }

    private void renderSkipOverlay(final GuiGraphics graphics) {
        final String text = "Passer";
        final int boxWidth = 78;
        final int boxHeight = 28;
        final int x = this.width - boxWidth - 18;
        final int y = this.height - boxHeight - 18;
        final int progressWidth = Math.round((boxWidth - 16) * (this.skipHoldTicks / (float) SKIP_HOLD_TICKS));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        graphics.drawString(this.font, text, x + (boxWidth - this.font.width(text)) / 2, y + 6, 0xFFFFFF);
        graphics.fill(x + 8, y + 20, x + boxWidth - 8, y + 24, 0xFF555555);
        graphics.fill(x + 8, y + 20, x + 8 + progressWidth, y + 24, 0xFFFFFFFF);
    }

    private AspectRatioDimension render$getAspectRatio(final int screenWidth, final int screenHeight, final int videoWidth, final int videoHeight) {
        final float containerAspectRatio = (float) screenWidth / (float) screenHeight;
        final float videoAspectRatio = (float) videoWidth / (float) videoHeight;
        final int renderWidth;
        final int renderHeight;
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
    public void renderBackground(final GuiGraphics graphics) {}

    @Override
    public void tick() {
        this.tryCreatePlayer();
        if (this.tipsVisible && this.tipsTicksLeft > 0 && --this.tipsTicksLeft <= 0) this.tipsVisible = false;
        if (this.volumeOverlayTicks > 0) this.volumeOverlayTicks--;
        if (this.seekOverlayTicks > 0) this.seekOverlayTicks--;
        if (this.seekCooldownTicks > 0) this.seekCooldownTicks--;
        this.tickSkipHold();

        if (this.videoPlayer != null && !this.isVideoReady() && this.status == Status.OPENING_GAME) {
            this.waitingTicks++;
            if (this.waitingTicks <= EARLY_RECOVERY_UNTIL_TICK) {
                this.kickWaterMediaDecodeThreadsIfNeeded("early-waiting-" + this.waitingTicks);
            }
            if (this.waitingTicks >= 40 && this.isTerminalBeforeFirstTexture()) {
                if (this.recreatePlayerOnce("terminal-before-first-texture")) return;
                this.logTerminalAfterMaxRecreates();
            }
            if (this.waitingTicks == 20 || this.waitingTicks == 100 || this.waitingTicks == 200 || this.waitingTicks == 400) {
                WaterVision.LOGGER.warn("WaterVision waiting for first video frame [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
            }
            if (this.waitingTicks >= 400 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
            }
        }

        if (this.videoPlayer != null && !this.resumeRequested) {
            if (this.resumeDelayTicks < RESUME_DELAY_TICKS) {
                this.resumeDelayTicks++;
                if (this.resumeDelayTicks == 1) WaterVision.LOGGER.info("WaterVision delayed resume armed [{}]: delayTicks={}, uri={}", BUILD_TAG, RESUME_DELAY_TICKS, this.uri);
            } else {
                this.videoPlayer.resume();
                this.resumeRequested = true;
                WaterVision.LOGGER.info("WaterVision player resume requested [{}] after {} ticks for {}", BUILD_TAG, this.resumeDelayTicks, this.uri);
            }
        }

        switch (this.status) {
            case OPENING_GAME -> {
                if (this.gameBackground.isFadedIn() && this.isVideoReady()) {
                    WaterVision.LOGGER.info("WaterVision transition OPENING_GAME -> OPENING_VIDEO [{}]: wmStatus={}, texture={}, size={}x{}, uri={}",
                            BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
                    this.status = Status.OPENING_VIDEO;
                    this.waitingTicks = 0;
                    WaterVision.LOGGER.info("WaterVision first frame ready [{}] for {}", BUILD_TAG, this.uri);
                }
            }
            case OPENING_VIDEO -> {
                if (this.videoPlayer != null && this.videoBackground.isFadedOut() && (this.videoPlayer.ended() || this.videoPlayer.stopped() || this.videoPlayer.error())) this.status = Status.CLOSING_VIDEO;
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

    private boolean isTerminalBeforeFirstTexture() {
        return this.videoPlayer != null && !this.firstTextureRenderLogged && this.videoPlayer.texture() == 0 && (this.videoPlayer.ended() || this.videoPlayer.stopped() || this.videoPlayer.error());
    }

    private boolean recreatePlayerOnce(final String reason) {
        if (this.playerRecreateAttempts >= MAX_PLAYER_RECREATE_ATTEMPTS || this.videoPlayer == null) return false;
        this.playerRecreateAttempts++;
        WaterVision.LOGGER.warn("WaterVision recreating player [{}]: attempt={}/{}, reason={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, MAX_PLAYER_RECREATE_ATTEMPTS, reason, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
        this.releasePlayerOnly();
        this.videoPlayer = null;
        final String base = this.uri.toString();
        final String retryUrl = base + (base.contains("?") ? "&" : "?") + "wvRetry=" + this.playerRecreateAttempts + "&wvTime=" + System.nanoTime();
        this.mrl = MediaAPI.getMRL(retryUrl);
        this.resumeRequested = false;
        this.resumeDelayTicks = 0;
        this.recoveryAttempted = false;
        this.waitLogged = false;
        this.terminalAfterMaxLogged = false;
        this.videoPaused = false;
        this.seekCooldownTicks = 0;
        this.waitingTicks = 0;
        return true;
    }

    private void logTerminalAfterMaxRecreates() {
        if (this.terminalAfterMaxLogged || this.videoPlayer == null) return;
        this.terminalAfterMaxLogged = true;
        WaterVision.LOGGER.error("WaterVision terminal player before first texture after max recreates [{}]: attempts={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
    }

    private void tickSkipHold() {
        if (!this.exit || this.skipTriggered || this.status == Status.CLOSING_VIDEO || this.status == Status.CLOSING_GAME) {
            this.skipHolding = false;
            this.skipHoldTicks = 0;
            return;
        }
        final long window = Minecraft.getInstance().getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ESCAPE)) {
            this.skipHolding = false;
            this.skipHoldTicks = 0;
            return;
        }
        this.skipHolding = true;
        this.skipHoldTicks++;
        if (this.skipHoldTicks >= SKIP_HOLD_TICKS) {
            this.skipTriggered = true;
            WaterVision.LOGGER.info("WaterVision skip hold completed [{}] for {}", BUILD_TAG, this.uri);
            this.requestCloseVideo();
        }
    }

    private void requestCloseVideo() {
        if (this.videoPlayer != null && !this.videoPlayer.stopped() && !this.videoPlayer.ended() && !this.videoPlayer.error()) this.videoPlayer.stop();
        this.videoPaused = false;
        this.status = Status.CLOSING_VIDEO;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_K) {
            this.toggleTips();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.adjustClientVolume(VOLUME_STEP);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.adjustClientVolume(-VOLUME_STEP);
            return true;
        }
        if (this.controls && keyCode == GLFW.GLFW_KEY_SPACE) {
            this.togglePlaybackControl();
            return true;
        }
        if (this.controls && keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.seekRelativeControl(1);
            return true;
        }
        if (this.controls && keyCode == GLFW.GLFW_KEY_LEFT) {
            this.seekRelativeControl(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.exit) this.skipHolding = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !this.skipTriggered) {
            this.skipHolding = false;
            this.skipHoldTicks = 0;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void toggleTips() {
        this.tipsVisible = !this.tipsVisible;
        this.tipsTicksLeft = this.tipsVisible ? TIPS_AUTO_HIDE_TICKS : 0;
        WaterVision.LOGGER.info("WaterVision tips toggled [{}]: visible={}", BUILD_TAG, this.tipsVisible);
    }

    private void adjustClientVolume(final int delta) {
        final int newVolume = VisionClientSettings.clampVolume(this.clientVolume + delta);
        if (newVolume != this.clientVolume) {
            this.clientVolume = newVolume;
            VisionClientSettings.setCinematicVolume(this.clientVolume);
            this.applyEffectiveVolume();
            WaterVision.LOGGER.info("WaterVision cinematic volume changed [{}]: command={} client={} effective={}", BUILD_TAG, this.commandVolume, this.clientVolume, this.effectiveVolume());
        }
        this.volumeOverlayTicks = VOLUME_OVERLAY_TICKS;
    }

    private void togglePlaybackControl() {
        if (!this.controls || this.videoPlayer == null || !this.isVideoReady() || this.status != Status.OPENING_VIDEO) return;
        final boolean shouldPause = !this.isPlayerPausedCompat();
        final boolean success = shouldPause ? this.videoPlayer.pause() : this.videoPlayer.resume();
        if (success) {
            this.videoPaused = shouldPause;
            WaterVision.LOGGER.info("WaterVision playback control [{}]: paused={} uri={}", BUILD_TAG, this.videoPaused, this.uri);
        } else {
            WaterVision.LOGGER.warn("WaterVision playback control [{}] failed: paused={} uri={}", BUILD_TAG, shouldPause, this.uri);
        }
    }

    private boolean isPlayerPausedCompat() {
        try {
            return this.videoPlayer != null && "PAUSED".equals(this.videoPlayer.status().name());
        } catch (final Throwable ignored) {
            return this.videoPaused;
        }
    }

    private void seekRelativeControl(final int direction) {
        if (!this.controls || this.videoPlayer == null || !this.isVideoReady() || this.status != Status.OPENING_VIDEO) return;
        if (this.seekCooldownTicks > 0 || this.videoPlayer.ended() || this.videoPlayer.stopped() || this.videoPlayer.error()) return;
        boolean success = direction > 0
                ? this.invokePlayerBooleanMethod("forward") || this.invokePlayerBooleanMethod("foward") || this.invokePlayerBooleanMethod("skipTime", long.class, SEEK_STEP_MS)
                : this.invokePlayerBooleanMethod("rewind") || this.invokePlayerBooleanMethod("skipTime", long.class, -SEEK_STEP_MS);
        if (success) {
            this.seekCooldownTicks = SEEK_COOLDOWN_TICKS;
            this.seekOverlayDirection = direction;
            this.seekOverlayTicks = SEEK_OVERLAY_TICKS;
            WaterVision.LOGGER.info("WaterVision seek control [{}]: direction={} uri={}", BUILD_TAG, direction, this.uri);
        } else {
            WaterVision.LOGGER.warn("WaterVision seek control [{}] failed: direction={} uri={}", BUILD_TAG, direction, this.uri);
        }
    }

    private boolean invokePlayerBooleanMethod(final String name, final Object... args) {
        if (this.videoPlayer == null) return false;
        final Class<?>[] parameterTypes = new Class<?>[args.length / 2];
        final Object[] values = new Object[args.length / 2];
        for (int i = 0; i < args.length; i += 2) {
            parameterTypes[i / 2] = (Class<?>) args[i];
            values[i / 2] = args[i + 1];
        }
        try {
            final Method method = this.videoPlayer.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            final Object result = method.invoke(this.videoPlayer, values);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private int effectiveVolume() {
        return Mth.clamp(Math.round(this.commandVolume * (this.clientVolume / 100.0f)), 0, 100);
    }

    private void applyEffectiveVolume() {
        if (this.videoPlayer != null) this.videoPlayer.volume(this.effectiveVolume());
    }

    private void kickWaterMediaDecodeThreadsIfNeeded(final String stage) {
        if (this.videoPlayer == null || this.recoveryAttempted) return;
        try {
            final Object player = this.videoPlayer;
            final boolean lifecycleAlive = this.isThreadAlive(this.getFieldValue(player, "lifecycleThread"));
            final boolean demuxAlive = this.isThreadAlive(this.getFieldValue(player, "demuxThread"));
            final boolean videoMissing = this.isPresent(this.getFieldValue(player, "videoCodecContext")) && !this.isThreadAlive(this.getFieldValue(player, "videoDecodeThread"));
            final boolean audioMissing = this.isPresent(this.getFieldValue(player, "audioCodecContext")) && !this.isThreadAlive(this.getFieldValue(player, "audioDecodeThread"));
            if ((!lifecycleAlive && !demuxAlive) || (!videoMissing && !audioMissing)) return;
            this.recoveryAttempted = true;
            final Method ensureDecodeThreads = player.getClass().getDeclaredMethod("ensureDecodeThreads");
            ensureDecodeThreads.setAccessible(true);
            ensureDecodeThreads.invoke(player);
            WaterVision.LOGGER.warn("WaterVision WM_RECOVERY [{}] stage={} invoked ensureDecodeThreads once: lifecycleAlive={}, demuxAlive={}, videoMissing={}, audioMissing={}, uri={}", BUILD_TAG, stage, lifecycleAlive, demuxAlive, videoMissing, audioMissing, this.uri);
        } catch (final Throwable throwable) {
            this.recoveryAttempted = true;
            WaterVision.LOGGER.warn("WaterVision WM_RECOVERY [{}] stage={} failed to invoke ensureDecodeThreads", BUILD_TAG, stage, throwable);
        }
    }

    private Object getFieldValue(final Object target, final String name) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (final NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (final Throwable throwable) {
                return null;
            }
        }
        return null;
    }

    private boolean isPresent(final Object value) {
        return value != null;
    }

    private boolean isThreadAlive(final Object value) {
        return value instanceof final Thread thread && thread.isAlive() && !thread.isInterrupted();
    }

    private void releasePlayerOnly() {
        if (this.videoPlayer != null) {
            this.videoPlayer.release();
            this.videoPlayer = null;
        }
        this.videoPaused = false;
        this.seekCooldownTicks = 0;
        this.textureWrapper = null;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.status != Status.CLOSING_GAME) {
            this.requestCloseVideo();
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
