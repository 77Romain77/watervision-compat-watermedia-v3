package me.srrapero720.watervision;

import me.srrapero720.watervision.client.render.RendererWrapper;
import me.srrapero720.watervision.common.commands.VisionCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.watermedia.api.image.ImageAPI;

@Mod(WaterVision.ID)
public class WaterVision {
    public static final String ID = "watervision";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final Marker IT = MarkerManager.getMarker("Main");
    public static final ResourceLocation LOADING_ANIM_TEXTURE = ResourceLocation.tryBuild(ID, "loading_animation");
    private static int ticks = 0;

    public WaterVision() {

    }

    @EventBusSubscriber
    public static class CommonEvents {
        @SubscribeEvent
        public static void onCommandsRegister(final RegisterCommandsEvent event) {
            VisionCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onClientCommandsRegister(final RegisterClientCommandsEvent event) {
            VisionCommands.registerClient(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onLevelTick(final ClientTickEvent.Pre event) {
            if (ticks == Integer.MAX_VALUE) ticks = 0;
            ticks++;
        }
    }
    @EventBusSubscriber(value = Dist.CLIENT)
    public static class ClientRegistryEvents {
        @SubscribeEvent
        public static void clientSetup(final FMLClientSetupEvent event) {
            LOGGER.debug("Client setup...");
            event.enqueueWork(() -> {
                Minecraft.getInstance().getTextureManager().register(LOADING_ANIM_TEXTURE, new RendererWrapper(ImageAPI.loadingGif("watervision")));
            });
        }
    }

    public static int getTicks() {
        return ticks;
    }

    @OnlyIn(Dist.CLIENT)
    public static float deltaFrames() {
        return Minecraft.getInstance().isPaused() ? 1.0F : (Minecraft.getInstance().getFrameTimeNs() / 1_000_000_000.0F) * 20.0F;
    }
}
