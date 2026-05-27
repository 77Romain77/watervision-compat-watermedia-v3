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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Supplier;

public class VisionScreen extends Screen {
    private static final String BUILD_TAG = "wm-kick-decode-no-reopen";
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
    private boolean firstFrameDiagnosticsLogged;
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

        this.videoPlayer = this.createCompatiblePlayer();
        if (this.videoPlayer == null) {
            this.failedToCreatePlayer = true;
            WaterVision.LOGGER.error("WaterMedia failed to create a player [{}] for: {}", BUILD_TAG, this.uri);
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
        this.logWaterMediaDiagnostics("player-created");
    }

    private MediaPlayer createCompatiblePlayer() {
        try {
            final Method newCreatePlayer = MediaAPI.class.getMethod("createPlayer", MRL.class, Supplier.class, Supplier.class);
            final Object player = newCreatePlayer.invoke(null, this.mrl, (Supplier<GLEngine>) this::createGfxEngine, (Supplier<ALEngine>) this::createSfxEngine);
            WaterVision.LOGGER.info("WaterVision using MediaAPI.createPlayer compatibility path [{}]", BUILD_TAG);
            return (MediaPlayer) player;
        } catch (final NoSuchMethodException ignored) {
            // Older WaterMedia API. Fall back to MRL#createPlayer below.
        } catch (final Throwable throwable) {
            WaterVision.LOGGER.error("WaterVision failed to create player through MediaAPI.createPlayer [{}]", BUILD_TAG, throwable);
            return null;
        }

        try {
            final Class<?> gfxEngineClass = Class.forName("org.watermedia.api.media.engines.GFXEngine");
            final Class<?> sfxEngineClass = Class.forName("org.watermedia.api.media.engines.SFXEngine");
            final Method legacyCreatePlayer = this.mrl.getClass().getMethod("createPlayer", gfxEngineClass, sfxEngineClass);
            final Object player = legacyCreatePlayer.invoke(this.mrl, this.createGfxEngine(), this.createSfxEngine());
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
            this.logWaterMediaDiagnostics("resume-requested");
        }

        if (this.videoPlayer != null && !this.isVideoReady() && this.status == Status.OPENING_GAME) {
            this.waitingTicks++;

            if (this.waitingTicks == 10 || this.waitingTicks == 20 || this.waitingTicks == 40 || this.waitingTicks == 80 || this.waitingTicks == 160) {
                this.kickWaterMediaDecodeThreadsIfNeeded("waiting-" + this.waitingTicks);
            }

            if (this.waitingTicks == 20 || this.waitingTicks == 60 || this.waitingTicks == 100 || this.waitingTicks == 200 || this.waitingTicks == 400) {
                WaterVision.LOGGER.warn("WaterVision waiting for first video frame [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
                this.logWaterMediaDiagnostics("waiting-" + this.waitingTicks);
            }
            if (this.waitingTicks >= 200 && !this.waitLogged) {
                this.waitLogged = true;
                WaterVision.LOGGER.error("WaterVision first frame still missing [{}]: status={}, texture={}, size={}x{}, uri={}", BUILD_TAG, this.videoPlayer.status(), this.videoPlayer.texture(), this.videoPlayer.width(), this.videoPlayer.height(), this.uri);
                this.logWaterMediaDiagnostics("first-frame-missing");
            }
        }

        switch (this.status) {
            case OPENING_GAME -> {
                if (this.gameBackground.isFadedIn() && this.isVideoReady()) {
                    this.status = Status.OPENING_VIDEO;
                    this.waitingTicks = 0;
                    WaterVision.LOGGER.info("WaterVision first frame ready [{}] for {}", BUILD_TAG, this.uri);
                    if (!this.firstFrameDiagnosticsLogged) {
                        this.firstFrameDiagnosticsLogged = true;
                        this.logWaterMediaDiagnostics("first-frame-ready");
                    }
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

    private void kickWaterMediaDecodeThreadsIfNeeded(final String stage) {
        if (this.videoPlayer == null) return;

        try {
            final Object player = this.videoPlayer;
            final boolean demuxAlive = this.isThreadAlive(this.getFieldValue(player, "demuxThread"));
            final boolean videoMissing = this.isPresent(this.getFieldValue(player, "videoCodecContext")) && !this.isThreadAlive(this.getFieldValue(player, "videoDecodeThread"));
            final boolean audioMissing = this.isPresent(this.getFieldValue(player, "audioCodecContext")) && !this.isThreadAlive(this.getFieldValue(player, "audioDecodeThread"));
            final int videoPackets = this.toInt(callNoArg(this.getFieldValue(player, "videoPacketQueue"), "count"));
            final int audioPackets = this.toInt(callNoArg(this.getFieldValue(player, "audioPacketQueue"), "count"));

            if (!demuxAlive || (!videoMissing && !audioMissing)) return;

            final Method ensureDecodeThreads = player.getClass().getDeclaredMethod("ensureDecodeThreads");
            ensureDecodeThreads.setAccessible(true);
            ensureDecodeThreads.invoke(player);

            WaterVision.LOGGER.warn("WaterVision WM_RECOVERY [{}] stage={} invoked ensureDecodeThreads: demuxAlive={}, videoMissing={}, audioMissing={}, videoPackets={}, audioPackets={}, uri={}",
                    BUILD_TAG, stage, demuxAlive, videoMissing, audioMissing, videoPackets, audioPackets, this.uri);
        } catch (final Throwable throwable) {
            WaterVision.LOGGER.warn("WaterVision WM_RECOVERY [{}] stage={} failed to invoke ensureDecodeThreads", BUILD_TAG, stage, throwable);
        }
    }

    private void logWaterMediaDiagnostics(final String stage) {
        if (this.videoPlayer == null) return;

        try {
            final Object player = this.videoPlayer;
            final Object gfx = this.getFieldValue(player, "gfx");
            final Object clock = this.getFieldValue(player, "clock");
            final Object demuxThread = this.getFieldValue(player, "demuxThread");
            final Object videoThread = this.getFieldValue(player, "videoDecodeThread");
            final Object audioThread = this.getFieldValue(player, "audioDecodeThread");

            WaterVision.LOGGER.warn("WaterVision WM_DIAG [{}] stage={} playerClass={} wmStatus={} texture={} size={}x{} time={} duration={} clock={} vIdx={} aIdx={} hwCtx={} fmtCtx={} vCodec={} aCodec={} rendered={} skipped={} uri={}",
                    BUILD_TAG,
                    stage,
                    player.getClass().getName(),
                    safeStatus(player),
                    safeTexture(player),
                    safeWidth(player),
                    safeHeight(player),
                    safeTime(player),
                    safeDuration(player),
                    clock,
                    this.getFieldValue(player, "videoStreamIndex"),
                    this.getFieldValue(player, "audioStreamIndex"),
                    this.exists(this.getFieldValue(player, "hwDeviceCtx")),
                    this.exists(this.getFieldValue(player, "formatContext")),
                    this.exists(this.getFieldValue(player, "videoCodecContext")),
                    this.exists(this.getFieldValue(player, "audioCodecContext")),
                    this.getFieldValue(player, "totalRenderedFrames"),
                    this.getFieldValue(player, "totalSkippedFrames"),
                    this.uri);

            WaterVision.LOGGER.warn("WaterVision WM_DIAG [{}] stage={} threads demux={} video={} audio={}",
                    BUILD_TAG,
                    stage,
                    this.threadSummary(demuxThread),
                    this.threadSummary(videoThread),
                    this.threadSummary(audioThread));

            WaterVision.LOGGER.warn("WaterVision WM_DIAG [{}] stage={} queues vPackets={} aPackets={} vFrames={} aFrames={}",
                    BUILD_TAG,
                    stage,
                    this.queueSummary(this.getFieldValue(player, "videoPacketQueue")),
                    this.queueSummary(this.getFieldValue(player, "audioPacketQueue")),
                    this.queueSummary(this.getFieldValue(player, "videoFrameQueue")),
                    this.queueSummary(this.getFieldValue(player, "audioFrameQueue")));

            WaterVision.LOGGER.warn("WaterVision WM_DIAG [{}] stage={} gfx={}", BUILD_TAG, stage, this.gfxSummary(gfx));
        } catch (final Throwable throwable) {
            WaterVision.LOGGER.warn("WaterVision WM_DIAG [{}] stage={} failed to inspect WaterMedia internals", BUILD_TAG, stage, throwable);
        }
    }

    private static Object safeStatus(final Object player) { return callNoArg(player, "status"); }
    private static Object safeTexture(final Object player) { return callNoArg(player, "texture"); }
    private static Object safeWidth(final Object player) { return callNoArg(player, "width"); }
    private static Object safeHeight(final Object player) { return callNoArg(player, "height"); }
    private static Object safeTime(final Object player) { return callNoArg(player, "time"); }
    private static Object safeDuration(final Object player) { return callNoArg(player, "duration"); }

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
                return "field-error:" + throwable.getClass().getSimpleName();
            }
        }
        return "missing:" + name;
    }

    private static Object callNoArg(final Object target, final String methodName) {
        if (target == null) return null;
        try {
            final Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (final Throwable throwable) {
            return "method-error:" + methodName + ":" + throwable.getClass().getSimpleName();
        }
    }

    private boolean isPresent(final Object value) {
        if (value == null) return false;
        if (value instanceof final String string && (string.startsWith("missing:") || string.startsWith("field-error:"))) return false;
        return true;
    }

    private String exists(final Object value) {
        if (value == null) return "false";
        if (value instanceof final String string && string.startsWith("missing:")) return "missing";
        return "true";
    }

    private boolean isThreadAlive(final Object value) {
        return value instanceof final Thread thread && thread.isAlive() && !thread.isInterrupted();
    }

    private int toInt(final Object value) {
        if (value instanceof final Number number) return number.intValue();
        return -1;
    }

    private String threadSummary(final Object value) {
        if (!(value instanceof final Thread thread)) return String.valueOf(value);
        return thread.getName() + "{alive=" + thread.isAlive() + ", state=" + thread.getState() + ", interrupted=" + thread.isInterrupted() + "}";
    }

    private String queueSummary(final Object queue) {
        if (queue == null) return "null";
        final String className = queue.getClass().getSimpleName();
        return className + "{" +
                "count=" + callNoArg(queue, "count") +
                ", bytes=" + callNoArg(queue, "byteSize") +
                ", remaining=" + callNoArg(queue, "remaining") +
                ", empty=" + callNoArg(queue, "isEmpty") +
                ", serial=" + callNoArg(queue, "serial") +
                ", aborted=" + this.getFieldValue(queue, "aborted") +
                ", finished=" + this.getFieldValue(queue, "finished") +
                '}';
    }

    private String gfxSummary(final Object gfx) {
        if (gfx == null) return "null";
        return gfx.getClass().getName() + "{" +
                "texture=" + callNoArg(gfx, "texture") +
                ", width=" + this.getFieldValue(gfx, "width") +
                ", height=" + this.getFieldValue(gfx, "height") +
                ", pixelFormat=" + this.getFieldValue(gfx, "pixelFormat") +
                ", managedTexture=" + this.getFieldValue(gfx, "managedTexture") +
                ", managedTextureW=" + this.getFieldValue(gfx, "managedTextureW") +
                ", managedTextureH=" + this.getFieldValue(gfx, "managedTextureH") +
                ", activeFrameTexture=" + this.getFieldValue(gfx, "activeFrameTexture") +
                ", pboInitialized=" + this.getFieldValue(gfx, "pboInitialized") +
                ", pboReady=" + this.getFieldValue(gfx, "pboReady") +
                ", firstFrame=" + this.getFieldValue(gfx, "firstFrame") +
                '}';
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
