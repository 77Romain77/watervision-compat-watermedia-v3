package me.srrapero720.watervision;

import com.mojang.blaze3d.platform.GlStateManager;
import me.srrapero720.watervision.client.render.TextureWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.GLEngine;
import org.watermedia.api.media.players.MediaPlayer;

import java.net.URI;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = WaterVision.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class VisionOverlay {
    private static final int PADDING = 8;
    private static MediaPlayer player;
    private static MRL mrl;
    private static TextureWrapper textureWrapper;
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "overlay_texture");
    static URI uri;
    static URI activeUri;
    private static boolean failureReported;

    public static void onClientPause(boolean pause) {
        if (player != null) {
            player.pause(pause);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(final RenderGuiOverlayEvent.Pre e) {
        if (uri == null) {
            releaseOverlay();
            return;
        }

        if (!Objects.equals(activeUri, uri)) {
            releaseOverlay();
            activeUri = uri;
            mrl = MediaAPI.getMRL(uri.toString());
            failureReported = false;
        }

        if (player == null) {
            tryCreatePlayer();
        }

        if (player == null) {
            return;
        }

        if (player.error()) {
            reportFailure("Failed to open a video overlay");
            releaseOverlay();
            uri = null;
            return;
        }

        if (player.ended()) {
            releaseOverlay();
            uri = null;
            return;
        }

        if (player.texture() != 0 && player.width() > 0 && player.height() > 0 && (player.playing() || player.buffering() || player.paused())) {
            final GuiGraphics graphics = e.getGuiGraphics();

            final int screenWidth = graphics.guiWidth();
            final int screenHeight = graphics.guiHeight();

            final int x = (int) (screenWidth / 1.6f) - PADDING;
            final int y = (int) (screenHeight / 1.6f) - PADDING;
            int width = screenWidth - x;
            int height = screenHeight - y;
            width -= PADDING;
            height -= PADDING;

            WaterVisionClient.internal$blit(graphics, TEXTURE, 1.0f, x, y, 0, 0, width, height);
        }
    }

    private static void tryCreatePlayer() {
        if (mrl == null || !mrl.ready()) {
            return;
        }

        if (mrl.error()) {
            reportFailure("Failed to load a video overlay");
            WaterVision.LOGGER.error("Failed to load overlay media resource: {}", activeUri);
            releaseOverlay();
            uri = null;
            return;
        }

        player = mrl.createPlayer(createGfxEngine(), createSfxEngine());
        if (player == null) {
            reportFailure("Failed to create a video overlay player");
            WaterVision.LOGGER.error("WaterMedia v3 failed to create an overlay player for: {}", activeUri);
            releaseOverlay();
            uri = null;
            return;
        }

        textureWrapper = new TextureWrapper(() -> (int) player.texture());
        Minecraft.getInstance().getTextureManager().register(TEXTURE, textureWrapper);
        player.start();
    }

    private static GLEngine createGfxEngine() {
        return new GLEngine.Builder(Thread.currentThread(), Minecraft.getInstance())
                .setGenTexture(GlStateManager::_genTexture)
                .setBindTexture((target, texture) -> GlStateManager._bindTexture(texture))
                .setTexParameter(GlStateManager::_texParameter)
                .setPixelStore(GlStateManager::_pixelStore)
                .setDelTexture(GlStateManager::_deleteTexture)
                .build();
    }

    private static ALEngine createSfxEngine() {
        return ALEngine.buildDefault();
    }

    private static void reportFailure(final String message) {
        if (!failureReported) {
            Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(message), true);
            failureReported = true;
        }
    }

    private static void releaseOverlay() {
        if (player != null) {
            player.release();
        }
        player = null;
        mrl = null;
        textureWrapper = null;
        activeUri = null;
    }

    public static void onClientDisconnect() {
        releaseOverlay();
        uri = null;
    }
}
