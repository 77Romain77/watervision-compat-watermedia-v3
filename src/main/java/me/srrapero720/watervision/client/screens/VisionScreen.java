package me.srrapero720.watervision.client.screens;

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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class VisionScreen extends Screen {
    private static final String BUILD_TAG = "cinematic-ui-watermedia-023-streaming-proxy-safe";
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "video_texture");
    private static final int TIPS_AUTO_HIDE_TICKS = 200;
    private static final int VOLUME_OVERLAY_TICKS = 20;
    private static final int SEEK_OVERLAY_TICKS = 20;
    private static final int VOLUME_STEP = 5;
    private static final int SEEK_STEP_MS = 5000;
    private static final int SEEK_COOLDOWN_TICKS = 5;
    private static final int RESUME_DELAY_TICKS = 20;
    private static final int SKIP_HOLD_TICKS = 40;
    private static final int MAX_PLAYER_RECREATE_ATTEMPTS = 1;

    private final URI uri;
    private URI playbackUri;
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
    private boolean cacheFallbackAttempted;
    private boolean fullDownloadFallbackAttempted;
    private boolean backgroundCacheDownloadStarted;
    private CompletableFuture<URI> cacheDownloadFuture;

    public VisionScreen(final URI uri, final int volume, final float speed, final boolean stretch, final float gameFadeDuration, final float videoFadeDuration, final boolean controls, final boolean exit) {
        super(Component.literal("WaterVision"));
        this.uri = uri;
        this.playbackUri = uri;
        this.commandVolume = Mth.clamp(volume, 0, 100);
        this.speed = Mth.clamp(speed, 0.1f, 3f);
        this.stretch = stretch;
        this.controls = controls;
        this.exit = exit;
        this.clientVolume = VisionClientSettings.cinematicVolume();
        this.gameBackground = new FadeBackground(gameFadeDuration);
        this.videoBackground = new FadeBackground(videoFadeDuration);
        this.videoBackground.forceFadeIn();
        this.mrl = MediaAPI.getMRL(this.playbackUri);
        Minecraft.getInstance().getSoundManager().pause();
        WaterVision.LOGGER.info("WaterVision screen opened [{}] for {}", BUILD_TAG, this.uri);
        WaterVision.LOGGER.info("WaterVision cinematic volume [{}]: command={} client={} effective={} controls={} exit={}", BUILD_TAG, this.commandVolume, this.clientVolume, this.effectiveVolume(), this.controls, this.exit);
    }

    private void tryCreatePlayer() {
        if (this.released || this.cacheDownloadFuture != null || this.videoPlayer != null || this.failedToCreatePlayer) return;

        final MRL.Status mrlStatus = this.mrl.status();
        if (mrlStatus == MRL.Status.FETCHING) return;

        if (mrlStatus == MRL.Status.EXPIRED || mrlStatus == MRL.Status.FORGOTTEN) {
            WaterVision.LOGGER.warn("WaterVision MRL renewed [{}]: status={}, uri={}", BUILD_TAG, mrlStatus, this.playbackUri);
            this.mrl = MediaAPI.getMRL(this.playbackUri);
            return;
        }

        if (mrlStatus != MRL.Status.LOADED) {
            if (VisionStreamingProxy.isProxyUri(this.playbackUri) && this.startFullDownloadFallback("proxy-mrl-status-" + mrlStatus)) return;
            if (this.startCacheOrProxyFallback("mrl-status-" + mrlStatus)) return;
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia MRL failed before player creation [{}]: status={}, exception={}, uri={}", BUILD_TAG, mrlStatus, this.mrl.exception(), this.playbackUri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }

        this.videoPlayer = MediaAPI.createPlayer(this.mrl, this::createGfxEngine, this::createSfxEngine);
        if (this.videoPlayer == null) {
            if (VisionStreamingProxy.isProxyUri(this.playbackUri) && this.startFullDownloadFallback("proxy-create-player-null")) return;
            if (this.startCacheOrProxyFallback("create-player-null")) return;
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia failed to create a player [{}] for {}", BUILD_TAG, this.playbackUri);
            this.status = Status.CLOSING_VIDEO;
            return;
        }
        this.applyEffectiveVolume();
        this.videoPlayer.speed(this.speed);
        this.videoPlayer.repeat(false);
        if (!this.videoPlayer.startPaused()) {
            WaterVision.LOGGER.error("WaterMedia refused startPaused [{}] for {}", BUILD_TAG, this.playbackUri);
            if (VisionStreamingProxy.isProxyUri(this.playbackUri) && this.startFullDownloadFallback("proxy-start-paused-refused")) return;
            if (this.startCacheOrProxyFallback("start-paused-refused")) return;
            this.releasePlayerOnly();
            this.failedToCreatePlayer = true;
            this.status = Status.CLOSING_VIDEO;
            return;
        }
        this.textureWrapper = new TextureWrapper(() -> (int) this.videoPlayer.texture());
        Minecraft.getInstance().getTextureManager().register(TEXTURE, this.textureWrapper);
        this.videoPaused = false;
        this.resumeDelayTicks = 0;
        WaterVision.LOGGER.info("WaterVision player created [{}] for {}", BUILD_TAG, this.playbackUri);
    }

    private GLEngine createGfxEngine() {
        return MediaAPI.glEngine(Thread.currentThread(), Minecraft.getInstance());
    }

    private ALEngine createSfxEngine() {
        return MediaAPI.alEngine();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.tickCacheFallback();
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
                WaterVision.LOGGER.info("WaterVision first video texture rendered [{}]: wmStatus={}, texture={}, size={}x{}, orchestrator={}, uri={}, originalUri={}",
                        BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.status, this.playbackUri, this.uri);
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
        if (this.cacheDownloadFuture != null) {
            final String text = "Chargement vidéo...";
            graphics.drawString(this.font, text, startX - this.font.width(text) - 8, y - 2, 0xDDDDDD);
        }
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
        int lines = 2;
        if (this.controls) lines += 2;
        if (this.exit) lines++;
        return lines;
    }

    private void renderTips(final GuiGraphics graphics, final int x, final int y) {
        final String[] lines = new String[5];
        int count = 0;
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
        for (int i = 0; i < count; i++) graphics.drawString(this.font, lines[i], x, y + i * 11, 0xDDDDDD);
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
        this.tickCacheFallback();
        this.tryCreatePlayer();
        if (this.tipsVisible && this.tipsTicksLeft > 0 && --this.tipsTicksLeft <= 0) this.tipsVisible = false;
        if (this.volumeOverlayTicks > 0) this.volumeOverlayTicks--;
        if (this.seekOverlayTicks > 0) this.seekOverlayTicks--;
        if (this.seekCooldownTicks > 0) this.seekCooldownTicks--;
        this.tickSkipHold();

        if (this.videoPlayer != null && !this.isVideoReady() && this.status == Status.OPENING_GAME) {
            this.waitingTicks++;

            if (this.videoPlayer.error()) {
                WaterVision.LOGGER.error("WaterVision player entered ERROR before first texture [{}]: wmStatus={}, texture={}, size={}x{}, uri={}, originalUri={}",
                        BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri, this.uri);
                if (this.startFullDownloadFallback("proxy-error-before-first-texture")) return;
                if (this.startCacheOrProxyFallback("player-error-before-first-texture")) return;
                this.status = Status.CLOSING_VIDEO;
                return;
            }

            if (this.waitingTicks >= 60 && this.isEndedOrStoppedBeforeFirstTexture()) {
                if (this.recreatePlayerOnce("ended-or-stopped-before-first-texture")) return;
                this.logTerminalAfterMaxRecreates();
            }
            if (this.waitingTicks == 20 || this.waitingTicks == 100 || this.waitingTicks == 200 || this.waitingTicks == 400) {
                WaterVision.LOGGER.warn("WaterVision waiting for first video frame [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
            }
            if (this.waitingTicks >= 400 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
            }
        }

        if (this.videoPlayer != null && !this.resumeRequested) {
            if (this.resumeDelayTicks < RESUME_DELAY_TICKS) {
                this.resumeDelayTicks++;
                if (this.resumeDelayTicks == 1) WaterVision.LOGGER.info("WaterVision delayed resume armed [{}]: delayTicks={}, uri={}", BUILD_TAG, RESUME_DELAY_TICKS, this.playbackUri);
            } else {
                this.videoPlayer.resume();
                this.resumeRequested = true;
                WaterVision.LOGGER.info("WaterVision player resume requested [{}] after {} ticks for {}", BUILD_TAG, this.resumeDelayTicks, this.playbackUri);
            }
        }

        switch (this.status) {
            case OPENING_GAME -> {
                if (this.gameBackground.isFadedIn() && this.isVideoReady()) {
                    WaterVision.LOGGER.info("WaterVision transition OPENING_GAME -> OPENING_VIDEO [{}]: wmStatus={}, texture={}, size={}x{}, uri={}",
                            BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
                    this.status = Status.OPENING_VIDEO;
                    this.waitingTicks = 0;
                    WaterVision.LOGGER.info("WaterVision first frame ready [{}] for {}", BUILD_TAG, this.playbackUri);
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

    private boolean isEndedOrStoppedBeforeFirstTexture() {
        return this.videoPlayer != null && !this.firstTextureRenderLogged && this.videoPlayer.texture() == 0 && (this.videoPlayer.ended() || this.videoPlayer.stopped());
    }

    private boolean recreatePlayerOnce(final String reason) {
        if (this.playerRecreateAttempts >= MAX_PLAYER_RECREATE_ATTEMPTS || this.videoPlayer == null) return false;
        this.playerRecreateAttempts++;
        WaterVision.LOGGER.warn("WaterVision recreating player [{}]: attempt={}/{}, reason={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, MAX_PLAYER_RECREATE_ATTEMPTS, reason, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
        this.releasePlayerOnly();
        this.videoPlayer = null;
        final String base = this.playbackUri.toString();
        final String retryUrl = base + (base.contains("?") ? "&" : "?") + "wvRetry=" + this.playerRecreateAttempts + "&wvTime=" + System.nanoTime();
        this.playbackUri = URI.create(retryUrl);
        this.mrl = MediaAPI.getMRL(this.playbackUri);
        this.resetPlayerStateForNewMrl();
        return true;
    }

    private void logTerminalAfterMaxRecreates() {
        if (this.terminalAfterMaxLogged || this.videoPlayer == null) return;
        this.terminalAfterMaxLogged = true;
        WaterVision.LOGGER.error("WaterVision terminal player before first texture after max recreates [{}]: attempts={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
    }

    private boolean startCacheOrProxyFallback(final String reason) {
        if (this.cacheFallbackAttempted || !this.isRemoteHttpUri(this.playbackUri) || VisionStreamingProxy.isProxyUri(this.playbackUri)) return false;
        this.cacheFallbackAttempted = true;
        WaterVision.LOGGER.warn("WaterVision remote playback failed before first texture [{}], trying cache/proxy fallback: reason={}, uri={}", BUILD_TAG, reason, this.playbackUri);
        this.releasePlayerOnly();
        this.failedToCreatePlayer = false;
        this.status = Status.OPENING_GAME;
        this.resetPlayerStateForNewMrl();
        final URI remoteUri = this.uri;
        this.cacheDownloadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return this.resolveCacheOrStreamingProxy(remoteUri);
            } catch (final Exception exception) {
                throw new CompletionException(exception);
            }
        });
        return true;
    }

    private boolean startFullDownloadFallback(final String reason) {
        if (this.fullDownloadFallbackAttempted || !VisionStreamingProxy.isProxyUri(this.playbackUri)) return false;
        this.fullDownloadFallbackAttempted = true;
        WaterVision.LOGGER.warn("WaterVision streaming proxy failed [{}], waiting for full cache download fallback: reason={}, proxyUri={}, originalUri={}", BUILD_TAG, reason, this.playbackUri, this.uri);
        this.releasePlayerOnly();
        this.failedToCreatePlayer = false;
        this.status = Status.OPENING_GAME;
        this.resetPlayerStateForNewMrl();
        final URI remoteUri = this.uri;
        this.cacheDownloadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return this.downloadRemoteToCache(remoteUri);
            } catch (final Exception exception) {
                throw new CompletionException(exception);
            }
        });
        return true;
    }

    private void tickCacheFallback() {
        if (this.cacheDownloadFuture == null || !this.cacheDownloadFuture.isDone()) return;
        final CompletableFuture<URI> completedFuture = this.cacheDownloadFuture;
        this.cacheDownloadFuture = null;
        try {
            final URI fallbackUri = completedFuture.join();
            this.playbackUri = fallbackUri;
            this.mrl = MediaAPI.getMRL(this.playbackUri);
            this.failedToCreatePlayer = false;
            this.status = Status.OPENING_GAME;
            this.resetPlayerStateForNewMrl();
            WaterVision.LOGGER.info("WaterVision fallback ready [{}]: originalUri={}, fallbackUri={}", BUILD_TAG, this.uri, this.playbackUri);
        } catch (final CompletionException exception) {
            WaterVision.LOGGER.error("WaterVision fallback failed [{}]: uri={}", BUILD_TAG, this.uri, exception.getCause() == null ? exception : exception.getCause());
            this.failedToCreatePlayer = true;
            this.status = Status.CLOSING_VIDEO;
        }
    }

    private URI resolveCacheOrStreamingProxy(final URI remoteUri) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Path cacheDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("watervision-cache");
        Files.createDirectories(cacheDirectory);
        final Path cacheFile = this.cacheFile(cacheDirectory, remoteUri);
        final Path metadataFile = this.metadataFile(cacheDirectory, remoteUri);
        final CacheMetadata remoteMetadata = this.fetchRemoteMetadata(remoteUri);

        if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) > 0L) {
            final long cachedSize = Files.size(cacheFile);
            final CacheMetadata cachedMetadata = this.readCacheMetadata(metadataFile);
            if (remoteMetadata == null) {
                WaterVision.LOGGER.warn("WaterVision cache hit without remote validation [{}]: uri={}, file={}, bytes={}", BUILD_TAG, remoteUri, cacheFile, cachedSize);
                return cacheFile.toUri();
            }
            if (cachedMetadata != null && cachedMetadata.matches(remoteMetadata, cachedSize)) {
                WaterVision.LOGGER.info("WaterVision cache hit validated [{}]: uri={}, file={}, metadata={}", BUILD_TAG, remoteUri, cacheFile, remoteMetadata);
                return cacheFile.toUri();
            }
            WaterVision.LOGGER.info("WaterVision cache stale [{}], switching to streaming proxy: uri={}, file={}, cachedMetadata={}, remoteMetadata={}, cachedBytes={}",
                    BUILD_TAG, remoteUri, cacheFile, cachedMetadata, remoteMetadata, cachedSize);
            Files.deleteIfExists(cacheFile);
            Files.deleteIfExists(metadataFile);
        }

        final URI proxyUri = VisionStreamingProxy.proxy(remoteUri);
        this.startBackgroundCacheDownload(remoteUri);
        WaterVision.LOGGER.info("WaterVision streaming proxy fallback ready [{}]: originalUri={}, proxyUri={}", BUILD_TAG, remoteUri, proxyUri);
        return proxyUri;
    }

    private void startBackgroundCacheDownload(final URI remoteUri) {
        if (this.backgroundCacheDownloadStarted) return;
        this.backgroundCacheDownloadStarted = true;
        CompletableFuture.runAsync(() -> {
            try {
                final URI localUri = this.downloadRemoteToCache(remoteUri);
                WaterVision.LOGGER.info("WaterVision background cache download completed [{}]: originalUri={}, localUri={}", BUILD_TAG, remoteUri, localUri);
            } catch (final Exception exception) {
                WaterVision.LOGGER.warn("WaterVision background cache download failed [{}]: originalUri={}", BUILD_TAG, remoteUri, exception);
            }
        });
    }

    private URI downloadRemoteToCache(final URI remoteUri) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Path cacheDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("watervision-cache");
        Files.createDirectories(cacheDirectory);
        final Path cacheFile = this.cacheFile(cacheDirectory, remoteUri);
        final Path metadataFile = this.metadataFile(cacheDirectory, remoteUri);
        final CacheMetadata remoteMetadata = this.fetchRemoteMetadata(remoteUri);

        if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) > 0L) {
            final long cachedSize = Files.size(cacheFile);
            final CacheMetadata cachedMetadata = this.readCacheMetadata(metadataFile);
            if (remoteMetadata == null) return cacheFile.toUri();
            if (cachedMetadata != null && cachedMetadata.matches(remoteMetadata, cachedSize)) return cacheFile.toUri();
            Files.deleteIfExists(cacheFile);
            Files.deleteIfExists(metadataFile);
        }

        final Path tempFile = cacheDirectory.resolve(cacheFile.getFileName().toString() + "." + System.nanoTime() + ".download");
        Files.deleteIfExists(tempFile);
        WaterVision.LOGGER.info("WaterVision downloading video to cache [{}]: uri={}, file={}", BUILD_TAG, remoteUri, cacheFile);
        final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        final HttpRequest request = HttpRequest.newBuilder(remoteUri)
                .GET()
                .header("User-Agent", "WaterVision/0.1 cache-fallback")
                .header("Accept", "video/mp4,video/*,*/*")
                .build();
        final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(tempFile);
            throw new IOException("HTTP " + response.statusCode() + " while downloading " + remoteUri);
        }
        final CacheMetadata downloadedMetadata = remoteMetadata != null ? remoteMetadata : this.metadataFromResponse(response);
        final long downloadedBytes = Files.size(tempFile);
        if (downloadedMetadata.contentLength() >= 0L && downloadedBytes != downloadedMetadata.contentLength()) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Incomplete download for " + remoteUri + ": got " + downloadedBytes + " bytes, expected " + downloadedMetadata.contentLength());
        }
        Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        this.writeCacheMetadata(metadataFile, remoteUri, downloadedMetadata);
        WaterVision.LOGGER.info("WaterVision cache download completed [{}]: uri={}, file={}, bytes={}, metadata={}",
                BUILD_TAG, remoteUri, cacheFile, Files.size(cacheFile), downloadedMetadata);
        return cacheFile.toUri();
    }

    private Path cacheFile(final Path cacheDirectory, final URI remoteUri) throws NoSuchAlgorithmException {
        return cacheDirectory.resolve("media-" + this.sha256(remoteUri.toString()) + this.safeExtension(remoteUri));
    }

    private Path metadataFile(final Path cacheDirectory, final URI remoteUri) throws NoSuchAlgorithmException {
        return cacheDirectory.resolve(this.cacheFile(cacheDirectory, remoteUri).getFileName().toString() + ".meta");
    }

    private CacheMetadata fetchRemoteMetadata(final URI remoteUri) {
        try {
            final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            final HttpRequest request = HttpRequest.newBuilder(remoteUri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "WaterVision/0.1 cache-validator")
                    .header("Accept", "video/mp4,video/*,*/*")
                    .build();
            final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                WaterVision.LOGGER.warn("WaterVision remote metadata check failed [{}]: status={}, uri={}", BUILD_TAG, response.statusCode(), remoteUri);
                return null;
            }
            return this.metadataFromResponse(response);
        } catch (final Exception exception) {
            WaterVision.LOGGER.warn("WaterVision remote metadata check failed [{}]: uri={}", BUILD_TAG, remoteUri, exception);
            return null;
        }
    }

    private CacheMetadata metadataFromResponse(final HttpResponse<?> response) {
        final String etag = response.headers().firstValue("ETag").orElse("");
        final String lastModified = response.headers().firstValue("Last-Modified").orElse("");
        final long contentLength = this.parseLong(response.headers().firstValue("Content-Length").orElse("-1"), -1L);
        return new CacheMetadata(etag, lastModified, contentLength);
    }

    private CacheMetadata readCacheMetadata(final Path metadataFile) {
        if (!Files.isRegularFile(metadataFile)) return null;
        final Properties properties = new Properties();
        try (final var input = Files.newInputStream(metadataFile)) {
            properties.load(input);
            final String etag = properties.getProperty("etag", "");
            final String lastModified = properties.getProperty("lastModified", "");
            final long contentLength = this.parseLong(properties.getProperty("contentLength", "-1"), -1L);
            return new CacheMetadata(etag, lastModified, contentLength);
        } catch (final IOException exception) {
            WaterVision.LOGGER.warn("WaterVision cache metadata read failed [{}]: file={}", BUILD_TAG, metadataFile, exception);
            return null;
        }
    }

    private void writeCacheMetadata(final Path metadataFile, final URI remoteUri, final CacheMetadata metadata) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty("url", remoteUri.toString());
        if (metadata != null) {
            properties.setProperty("etag", metadata.etag());
            properties.setProperty("lastModified", metadata.lastModified());
            properties.setProperty("contentLength", Long.toString(metadata.contentLength()));
        }
        try (final var output = Files.newOutputStream(metadataFile)) {
            properties.store(output, "WaterVision cache metadata");
        }
    }

    private long parseLong(final String value, final long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (final Exception ignored) {
            return fallback;
        }
    }

    private String safeExtension(final URI remoteUri) {
        final String path = remoteUri.getPath();
        if (path == null) return ".mp4";
        final int slash = path.lastIndexOf('/');
        final int dot = path.lastIndexOf('.');
        if (dot <= slash || dot < 0 || dot >= path.length() - 1) return ".mp4";
        final String extension = path.substring(dot).toLowerCase(Locale.ROOT);
        if (extension.length() > 12 || !extension.matches("\\.[a-z0-9]+")) return ".mp4";
        return extension;
    }

    private String sha256(final String value) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean isRemoteHttpUri(final URI uri) {
        if (uri == null || uri.getScheme() == null) return false;
        final String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private void resetPlayerStateForNewMrl() {
        this.resumeRequested = false;
        this.resumeDelayTicks = 0;
        this.waitLogged = false;
        this.terminalAfterMaxLogged = false;
        this.firstTextureRenderLogged = false;
        this.videoPaused = false;
        this.seekCooldownTicks = 0;
        this.waitingTicks = 0;
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
            WaterVision.LOGGER.info("WaterVision skip hold completed [{}] for {}", BUILD_TAG, this.playbackUri);
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
        final boolean shouldPause = !this.videoPlayer.paused();
        boolean success = shouldPause ? this.videoPlayer.pause() : this.videoPlayer.resume();
        if (!success) success = this.videoPlayer.togglePlay();
        if (success) {
            this.videoPaused = shouldPause;
            WaterVision.LOGGER.info("WaterVision playback control [{}]: paused={} uri={}", BUILD_TAG, this.videoPaused, this.playbackUri);
        } else {
            WaterVision.LOGGER.warn("WaterVision playback control [{}] failed: paused={} uri={}", BUILD_TAG, shouldPause, this.playbackUri);
        }
    }

    private void seekRelativeControl(final int direction) {
        if (!this.controls || this.videoPlayer == null || !this.isVideoReady() || this.status != Status.OPENING_VIDEO) return;
        if (this.seekCooldownTicks > 0 || this.videoPlayer.ended() || this.videoPlayer.stopped() || this.videoPlayer.error() || !this.videoPlayer.canSeek()) return;
        boolean success = direction > 0 ? this.videoPlayer.forward() : this.videoPlayer.rewind();
        if (!success) success = this.videoPlayer.skipTime((long) direction * SEEK_STEP_MS);
        if (success) {
            this.seekCooldownTicks = SEEK_COOLDOWN_TICKS;
            this.seekOverlayDirection = direction;
            this.seekOverlayTicks = SEEK_OVERLAY_TICKS;
            WaterVision.LOGGER.info("WaterVision seek control [{}]: direction={} uri={}", BUILD_TAG, direction, this.playbackUri);
        } else {
            WaterVision.LOGGER.warn("WaterVision seek control [{}] failed: direction={} uri={}", BUILD_TAG, direction, this.playbackUri);
        }
    }

    private int effectiveVolume() {
        return Mth.clamp(Math.round(this.commandVolume * (this.clientVolume / 100.0f)), 0, 100);
    }

    private void applyEffectiveVolume() {
        if (this.videoPlayer != null) this.videoPlayer.volume(this.effectiveVolume());
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
        if (this.cacheDownloadFuture != null && !this.cacheDownloadFuture.isDone()) this.cacheDownloadFuture.cancel(true);
        this.cacheDownloadFuture = null;
        Minecraft.getInstance().getSoundManager().resume();
        this.releasePlayerOnly();
        this.released = true;
    }

    public record AspectRatioDimension(int x, int y, int width, int height) { }

    private record CacheMetadata(String etag, String lastModified, long contentLength) {
        private boolean matches(final CacheMetadata remote, final long cachedFileSize) {
            if (remote == null) return false;
            if (remote.contentLength() >= 0L && cachedFileSize != remote.contentLength()) return false;
            if (!remote.etag().isBlank()) return remote.etag().equals(this.etag());
            if (!remote.lastModified().isBlank()) return remote.lastModified().equals(this.lastModified());
            return remote.contentLength() >= 0L && cachedFileSize == remote.contentLength();
        }
    }

    public enum Status {
        OPENING_GAME,
        OPENING_VIDEO,
        CLOSING_VIDEO,
        CLOSING_GAME,
    }
}
