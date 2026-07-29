from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


path = Path("src/main/java/me/srrapero720/watervision/client/screens/VisionScreen.java")
text = path.read_text(encoding="utf-8")

text = replace_once(
    text,
    """    private static final int RESUME_DELAY_TICKS = 20;
    private static final int SKIP_HOLD_TICKS = 40;
    private static final int MAX_PLAYER_RECREATE_ATTEMPTS = 1;
""",
    """    private static final int RESUME_DELAY_TICKS = 20;
    private static final int RESUME_RETRY_DELAY_TICKS = 20;
    private static final int MAX_RESUME_ATTEMPTS = 5;
    private static final int MRL_RELOAD_TIMEOUT_TICKS = 100;
    private static final int MRL_FALLBACK_TIMEOUT_TICKS = 200;
    private static final int FFMPEG_READY_TIMEOUT_TICKS = 200;
    private static final int SKIP_HOLD_TICKS = 40;
    private static final int MAX_PLAYER_RECREATE_ATTEMPTS = 1;
""",
    "constants",
)

text = replace_once(
    text,
    """    private MRL mrl;
    private final FadeBackground gameBackground;
""",
    """    private MRL mrl;
    private int mrlWaitTicks;
    private boolean mrlReloadAttempted;
    private int ffmpegWaitTicks;
    private boolean mediaBackendFailureReported;
    private final FadeBackground gameBackground;
""",
    "MRL fields",
)

text = replace_once(
    text,
    """    private boolean resumeRequested;
    private int resumeDelayTicks;
    private boolean waitLogged;
""",
    """    private boolean resumeRequested;
    private int resumeDelayTicks;
    private int resumeAttempts;
    private int resumeRetryDelayTicks;
    private boolean waitLogged;
""",
    "resume fields",
)

old_prepare = """    private void tryCreatePlayer() {
        if (this.released || this.cacheDownloadFuture != null || this.videoPlayer != null || this.failedToCreatePlayer) return;

        final MRL.Status mrlStatus = this.mrl.status();
        if (mrlStatus == MRL.Status.FETCHING) return;

        if (mrlStatus == MRL.Status.EXPIRED || mrlStatus == MRL.Status.FORGOTTEN) {
            WaterVision.LOGGER.warn("WaterVision MRL renewed [{}]: status={}, uri={}", BUILD_TAG, mrlStatus, this.playbackUri);
            this.mrl = MediaAPI.mrl(this.playbackUri);
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
"""

new_prepare = """    private boolean attemptPlaybackFallback(final String reason) {
        if (VisionStreamingProxy.isProxyUri(this.playbackUri) && this.startFullDownloadFallback(reason)) return true;
        return this.startCacheOrProxyFallback(reason);
    }

    private void failBeforePlayer(final String reason, final MRL.Status mrlStatus) {
        this.failedToCreatePlayer = true;
        WaterVision.LOGGER.error("WaterVision player preparation failed [{}]: reason={}, mrlStatus={}, exception={}, uri={}",
                BUILD_TAG, reason, mrlStatus, this.mrl == null ? null : this.mrl.exception(), this.playbackUri);
        this.status = Status.CLOSING_VIDEO;
    }

    private boolean reloadMrl(final String reason) {
        if (this.mrl == null) return false;
        this.mrlReloadAttempted = true;
        this.mrlWaitTicks = 0;
        try {
            this.mrl.reload();
            WaterVision.LOGGER.warn("WaterVision MRL reload requested [{}]: reason={}, uri={}", BUILD_TAG, reason, this.playbackUri);
            return true;
        } catch (final RuntimeException exception) {
            WaterVision.LOGGER.error("WaterVision MRL reload failed [{}]: reason={}, uri={}", BUILD_TAG, reason, this.playbackUri, exception);
            return false;
        }
    }

    private boolean ensureMediaBackendReady() {
        if (MediaAPI.ffmpegError()) {
            this.failMediaBackend("ffmpeg-error");
            return false;
        }
        if (MediaAPI.ffmpegLoaded()) {
            this.ffmpegWaitTicks = 0;
            return true;
        }
        this.ffmpegWaitTicks++;
        if (this.ffmpegWaitTicks == 1 || this.ffmpegWaitTicks == FFMPEG_READY_TIMEOUT_TICKS / 2) {
            WaterVision.LOGGER.warn("WaterVision waiting for FFmpeg [{}]: ticks={}, uri={}", BUILD_TAG, this.ffmpegWaitTicks, this.playbackUri);
        }
        if (this.ffmpegWaitTicks >= FFMPEG_READY_TIMEOUT_TICKS) this.failMediaBackend("ffmpeg-load-timeout");
        return false;
    }

    private void failMediaBackend(final String reason) {
        if (!this.mediaBackendFailureReported) {
            Minecraft.getInstance().getChatListener().handleSystemMessage(
                    Component.literal("Impossible de lire la cinématique : WaterMedia/FFmpeg n'est pas disponible."), true);
            this.mediaBackendFailureReported = true;
        }
        this.failedToCreatePlayer = true;
        WaterVision.LOGGER.error("WaterVision media backend unavailable [{}]: reason={}, ffmpegLoaded={}, ffmpegError={}, uri={}",
                BUILD_TAG, reason, MediaAPI.ffmpegLoaded(), MediaAPI.ffmpegError(), this.playbackUri);
        this.status = Status.CLOSING_VIDEO;
    }

    private void tryCreatePlayer() {
        if (this.released || this.cacheDownloadFuture != null || this.videoPlayer != null || this.failedToCreatePlayer) return;
        if (!this.ensureMediaBackendReady()) return;
        if (this.mrl == null) {
            this.failBeforePlayer("mrl-null", null);
            return;
        }

        final MRL.Status mrlStatus = this.mrl.status();
        if (mrlStatus == MRL.Status.FETCHING) {
            this.mrlWaitTicks++;
            if (!this.mrlReloadAttempted && this.mrlWaitTicks >= MRL_RELOAD_TIMEOUT_TICKS) {
                if (!this.reloadMrl("fetch-timeout")) {
                    if (this.attemptPlaybackFallback("mrl-reload-failed")) return;
                    this.failBeforePlayer("mrl-reload-failed", mrlStatus);
                }
                return;
            }
            if (this.mrlReloadAttempted && this.mrlWaitTicks >= MRL_FALLBACK_TIMEOUT_TICKS) {
                if (this.attemptPlaybackFallback("mrl-fetch-timeout-after-reload")) return;
                this.failBeforePlayer("mrl-fetch-timeout-after-reload", mrlStatus);
            }
            return;
        }

        if (mrlStatus == MRL.Status.EXPIRED || mrlStatus == MRL.Status.FORGOTTEN) {
            if (!this.mrlReloadAttempted) {
                if (!this.reloadMrl("status-" + mrlStatus)) {
                    if (this.attemptPlaybackFallback("mrl-regeneration-failed-" + mrlStatus)) return;
                    this.failBeforePlayer("mrl-regeneration-failed", mrlStatus);
                }
                return;
            }
            this.mrlWaitTicks++;
            if (this.mrlWaitTicks < MRL_FALLBACK_TIMEOUT_TICKS) return;
            if (this.attemptPlaybackFallback("mrl-regeneration-timeout-" + mrlStatus)) return;
            this.failBeforePlayer("mrl-regeneration-timeout", mrlStatus);
            return;
        }

        this.mrlWaitTicks = 0;
        if (mrlStatus != MRL.Status.LOADED) {
            if (this.attemptPlaybackFallback("mrl-status-" + mrlStatus)) return;
            this.failBeforePlayer("mrl-status-" + mrlStatus, mrlStatus);
            return;
        }
        this.mrlReloadAttempted = false;
"""
text = replace_once(text, old_prepare, new_prepare, "player preparation")

text = replace_once(
    text,
    """        this.applyEffectiveVolume();
        this.videoPlayer.speed(this.speed);
        this.videoPlayer.repeat(false);
        if (!this.videoPlayer.startPaused()) {
            WaterVision.LOGGER.error("WaterMedia refused startPaused [{}] for {}", BUILD_TAG, this.playbackUri);
            if (VisionStreamingProxy.isProxyUri(this.playbackUri) && this.startFullDownloadFallback("proxy-start-paused-refused")) return;
            if (this.startCacheOrProxyFallback("start-paused-refused")) return;
""",
    """        this.applyEffectiveVolume();
        if (Float.compare(this.speed, 1.0f) != 0) {
            if (!this.videoPlayer.canSpeed()) {
                WaterVision.LOGGER.warn("WaterMedia does not support speed changes [{}]: requested={}, uri={}", BUILD_TAG, this.speed, this.playbackUri);
            } else if (!this.videoPlayer.speed(this.speed) && Float.compare(this.videoPlayer.speed(), this.speed) != 0) {
                WaterVision.LOGGER.warn("WaterMedia refused speed [{}]: requested={}, actual={}, uri={}", BUILD_TAG, this.speed, this.videoPlayer.speed(), this.playbackUri);
            }
        }
        if (!this.videoPlayer.repeat(false) && this.videoPlayer.repeat()) {
            WaterVision.LOGGER.warn("WaterMedia refused repeat=false [{}] for {}", BUILD_TAG, this.playbackUri);
        }
        if (!this.videoPlayer.startPaused()) {
            WaterVision.LOGGER.error("WaterMedia refused startPaused [{}] for {}", BUILD_TAG, this.playbackUri);
            if (this.attemptPlaybackFallback("start-paused-refused")) return;
""",
    "player configuration",
)

text = replace_once(
    text,
    """    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.tickCacheFallback();
        this.tryCreatePlayer();
        this.gameBackground.render(graphics, this.width, this.height, this.status != Status.CLOSING_GAME, partialTick);
""",
    """    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.gameBackground.render(graphics, this.width, this.height, this.status != Status.CLOSING_GAME, partialTick);
""",
    "render polling",
)

text = replace_once(
    text,
    """            if (this.waitingTicks >= 400 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
            }
""",
    """            if (this.waitingTicks >= 400 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
                if (this.attemptPlaybackFallback("first-frame-timeout")) return;
                this.releasePlayerOnly();
                this.failedToCreatePlayer = true;
                this.status = Status.CLOSING_VIDEO;
                return;
            }
""",
    "first-frame timeout",
)

text = replace_once(
    text,
    """        if (this.videoPlayer != null && !this.resumeRequested) {
            if (this.resumeDelayTicks < RESUME_DELAY_TICKS) {
                this.resumeDelayTicks++;
                if (this.resumeDelayTicks == 1) WaterVision.LOGGER.info("WaterVision delayed resume armed [{}]: delayTicks={}, uri={}", BUILD_TAG, RESUME_DELAY_TICKS, this.playbackUri);
            } else {
                this.videoPlayer.resume();
                this.resumeRequested = true;
                WaterVision.LOGGER.info("WaterVision player resume requested [{}] after {} ticks for {}", BUILD_TAG, this.resumeDelayTicks, this.playbackUri);
            }
        }
""",
    """        if (!this.tickDelayedResume()) return;
""",
    "delayed resume",
)

text = replace_once(
    text,
    """    private boolean isEndedOrStoppedBeforeFirstTexture() {
""",
    """    private boolean tickDelayedResume() {
        if (this.videoPlayer == null || this.resumeRequested) return true;
        if (this.resumeDelayTicks < RESUME_DELAY_TICKS) {
            this.resumeDelayTicks++;
            if (this.resumeDelayTicks == 1) WaterVision.LOGGER.info("WaterVision delayed resume armed [{}]: delayTicks={}, uri={}", BUILD_TAG, RESUME_DELAY_TICKS, this.playbackUri);
            return true;
        }
        if (this.resumeRetryDelayTicks > 0) {
            this.resumeRetryDelayTicks--;
            return true;
        }
        if (this.videoPlayer.loading() || this.videoPlayer.buffering() || this.videoPlayer.waiting()) {
            this.resumeRetryDelayTicks = 5;
            return true;
        }

        this.resumeAttempts++;
        if (this.videoPlayer.resume() || this.videoPlayer.playing()) {
            this.resumeRequested = true;
            WaterVision.LOGGER.info("WaterVision player resumed [{}]: attempt={}, delayTicks={}, uri={}", BUILD_TAG, this.resumeAttempts, this.resumeDelayTicks, this.playbackUri);
            return true;
        }

        WaterVision.LOGGER.warn("WaterVision resume refused [{}]: attempt={}/{}, status={}, uri={}",
                BUILD_TAG, this.resumeAttempts, MAX_RESUME_ATTEMPTS, this.videoPlayer.status(), this.playbackUri);
        if (this.resumeAttempts < MAX_RESUME_ATTEMPTS) {
            this.resumeRetryDelayTicks = RESUME_RETRY_DELAY_TICKS;
            return true;
        }
        if (this.attemptPlaybackFallback("resume-refused-after-retries")) return false;
        this.releasePlayerOnly();
        this.failedToCreatePlayer = true;
        this.status = Status.CLOSING_VIDEO;
        return false;
    }

    private boolean isEndedOrStoppedBeforeFirstTexture() {
""",
    "resume helper",
)

old_recreate = """    private boolean recreatePlayerOnce(final String reason) {
        if (this.playerRecreateAttempts >= MAX_PLAYER_RECREATE_ATTEMPTS || this.videoPlayer == null) return false;
        this.playerRecreateAttempts++;
        WaterVision.LOGGER.warn("WaterVision recreating player [{}]: attempt={}/{}, reason={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, MAX_PLAYER_RECREATE_ATTEMPTS, reason, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
        this.releasePlayerOnly();
        this.videoPlayer = null;
        final String base = this.playbackUri.toString();
        final String retryUrl = base + (base.contains("?") ? "&" : "?") + "wvRetry=" + this.playerRecreateAttempts + "&wvTime=" + System.nanoTime();
        this.playbackUri = URI.create(retryUrl);
        this.mrl = MediaAPI.mrl(this.playbackUri);
        this.resetPlayerStateForNewMrl();
        return true;
    }
"""
new_recreate = """    private boolean recreatePlayerOnce(final String reason) {
        if (this.playerRecreateAttempts >= MAX_PLAYER_RECREATE_ATTEMPTS || this.videoPlayer == null) return false;
        this.playerRecreateAttempts++;
        WaterVision.LOGGER.warn("WaterVision recreating player [{}]: attempt={}/{}, reason={}, wmStatus={}, texture={}, size={}x{}, uri={}",
                BUILD_TAG, this.playerRecreateAttempts, MAX_PLAYER_RECREATE_ATTEMPTS, reason, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.playbackUri);
        this.releasePlayerOnly();
        if (!this.reloadMrl("player-recreate-" + reason)) {
            if (this.attemptPlaybackFallback("player-recreate-reload-failed")) return true;
            this.failBeforePlayer("player-recreate-reload-failed", this.mrl == null ? null : this.mrl.status());
            return true;
        }
        this.resetPlayerStateForNewMrl();
        return true;
    }
"""
text = replace_once(text, old_recreate, new_recreate, "player recreation")

text = replace_once(
    text,
    """            this.mrl = MediaAPI.mrl(this.playbackUri);
            this.failedToCreatePlayer = false;
""",
    """            this.mrl = MediaAPI.mrl(this.playbackUri);
            this.mrlWaitTicks = 0;
            this.mrlReloadAttempted = false;
            this.failedToCreatePlayer = false;
""",
    "fallback MRL reset",
)

text = replace_once(
    text,
    """        this.resumeRequested = false;
        this.resumeDelayTicks = 0;
        this.waitLogged = false;
""",
    """        this.resumeRequested = false;
        this.resumeDelayTicks = 0;
        this.resumeAttempts = 0;
        this.resumeRetryDelayTicks = 0;
        this.waitLogged = false;
""",
    "reset resume",
)

text = replace_once(
    text,
    """    private void requestCloseVideo() {
        if (this.videoPlayer != null && !this.videoPlayer.stopped() && !this.videoPlayer.ended() && !this.videoPlayer.error()) this.videoPlayer.stop();
        this.videoPaused = false;
""",
    """    private void requestCloseVideo() {
        if (this.videoPlayer != null && !this.videoPlayer.stopped() && !this.videoPlayer.ended() && !this.videoPlayer.error()) {
            final boolean stopped = this.videoPlayer.stop();
            if (!stopped && !this.videoPlayer.stopped()) {
                WaterVision.LOGGER.warn("WaterVision stop refused [{}]: status={}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.playbackUri);
            }
        }
        this.videoPaused = false;
""",
    "stop result",
)

text = replace_once(
    text,
    """        this.videoPaused = false;
        this.seekCooldownTicks = 0;
        this.textureWrapper = null;
""",
    """        this.videoPaused = false;
        this.resumeRequested = false;
        this.resumeAttempts = 0;
        this.resumeRetryDelayTicks = 0;
        this.seekCooldownTicks = 0;
        this.textureWrapper = null;
""",
    "release state",
)

path.write_text(text, encoding="utf-8")
