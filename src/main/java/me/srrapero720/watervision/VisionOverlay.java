package me.srrapero720.watervision;

import com.mojang.blaze3d.opengl.GlStateManager;
import me.srrapero720.watervision.client.render.TextureWrapper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;

public class VisionOverlay implements LayeredDraw.Layer {
    private static final int PADDING = 8;
    private static VideoPlayer player;
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "overlay_texture");
    static URI uri;
    static URI activeUri;

    public static void onClientPause(final boolean pause) {
        if (player != null && player.isPaused() != pause) {
            player.setPauseMode(pause);
        }
    }

    public static void onClientDisconnect() {
        uri = null;
        activeUri = null;
        player.stop();
    }

    public static void onClientTick() {
        if (player == null) {
            player = new VideoPlayer(runable -> Minecraft.getInstance().execute(() -> {
                runable.run();
                GlStateManager._bindTexture(0);
            }));
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new TextureWrapper(player.texture(), player.width(), player.height()));
        }

        if (player == null) {
            player = new VideoPlayer(runable -> Minecraft.getInstance().execute(() -> {
                runable.run();
                GlStateManager._bindTexture(0);
            }));
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new TextureWrapper(player.texture(), player.width(), player.height()));
        }

        if (uri != activeUri) {
            if (uri == null) {
                player.stop();
                activeUri = null;
                return;
            }

            player.startPaused(uri);
            activeUri = uri;
        }

        if (player.isBroken()) {
            Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal("Failed to open a video overlay"), true);
            activeUri = null;
            uri = null;
        }

        if (player.isSafeUse() && player.isReady() && player.isPaused() && !Minecraft.getInstance().isPaused()) {
            player.play();
        }
    }


    @Override
    public void render(GuiGraphics graphics, DeltaTracker p_344084_) {
        if (player != null && player.isSafeUse() && (player.isPlaying() || player.isBuffering()) && !player.isPaused()) {
            player.preRender();
            GlStateManager._bindTexture(0);
            final int screenWidth = graphics.guiWidth();
            final int screenHeight = graphics.guiHeight();

            final int x = (int) (screenWidth / 1.6f) - PADDING;
            final int y = (int) (screenHeight / 1.6f) - PADDING;
            int width = screenWidth - x;
            int height = screenHeight - y;
            width -= PADDING;
            height -= PADDING;

            WaterVisionClient.internal$blit(graphics, TEXTURE, 1.0f, x, y, 0, 0, width, height);
        } else if (player.isSafeUse() && player.isEnded()) {
            uri = null;
            activeUri = null;
            player.stop();
        }
    }
}
