package me.srrapero720.watervision;

import com.mojang.blaze3d.opengl.GlStateManager;
import me.srrapero720.watervision.client.render.TextureWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;

@EventBusSubscriber(modid = WaterVision.ID, value = Dist.CLIENT)
public class VisionOverlay {
    private static final int PADDING = 8;
    private static VideoPlayer player;
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "overlay_texture");
    static URI uri;
    static URI activeUri;

    @SubscribeEvent
    public static void onClientPause(final ClientPauseChangeEvent.Post e) {
        if (player != null && player.isPaused() != e.isPaused()) {
            player.setPauseMode(e.isPaused());
        }
    }

    public static void onClientDisconnect() {
        uri = null;
        activeUri = null;
        player.stop();
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Pre e) {
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


    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post e) {
        if (player != null && player.isSafeUse() && (player.isPlaying() || player.isBuffering()) && !player.isPaused()) {
            player.preRender();
            GuiGraphics graphics = e.getGuiGraphics();
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
