package me.srrapero720.watervision;

import me.srrapero720.watervision.client.render.TextureWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.watermedia.api.player.PlayerAPI;
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
    public static void onClientPause(ClientPauseChangeEvent.Post e) {
        if (player != null && player.isPaused() != e.isPaused()) {
            player.setPauseMode(e.isPaused());
        }
    }

    public static void onClientDisconnect() {
        if (player != null) {
            player.release();
        }
        player = null;
        uri = null;
    }

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post e) {
        if (uri != null && player == null) {
            player = new VideoPlayer(PlayerAPI.getFactory(), Minecraft.getInstance());
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new TextureWrapper(player.texture(), player.width(), player.height()));
            player.start(uri);
            activeUri = uri;
        }

        if (player == null) {
            return;
        }

        if (player.isBroken()) {
            Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal("Failed to open a video overlay"), true);
            player.release();
            player = null;
            return;
        }

        if (uri == null && activeUri != null) {
            player.release();
            player = null;
            activeUri = null;
            return;
        }

        if (activeUri != uri) {
            player.start(uri);
            activeUri = uri;
        }

        if (player.isSafeUse() && player.isPlaying()) {
            player.preRender();
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
        } else if (player.isSafeUse() && player.isEnded()) {
            uri = null;
            activeUri = null;
            player.release();
            player = null;
        }
    }
}
