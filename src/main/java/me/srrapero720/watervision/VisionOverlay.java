package me.srrapero720.watervision;

import me.srrapero720.watervision.client.render.TextureWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;

@Mod.EventBusSubscriber(modid = WaterVision.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class VisionOverlay {
    private static final int PADDING = 8;
    private static VideoPlayer player;
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild("watervision", "overlay_texture");
    static URI uri;
    static URI activeUri;

    public static void onClientPause(boolean pause) {
        if (player != null && player.isPaused() != pause) {
            player.setPauseMode(pause);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(final RenderGuiOverlayEvent.Pre e) {
        if (uri != null && player == null) {
            player = new VideoPlayer(PlayerAPI.getFactory(), Minecraft.getInstance());
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new TextureWrapper(player.texture()));
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

    public static void onClientDisconnect() {
        player.release();
        player = null;
        uri = null;
    }
}
