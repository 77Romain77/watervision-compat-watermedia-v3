package me.srrapero720.watervision;

import me.srrapero720.watervision.common.commands.VisionCommands;
import me.srrapero720.watervision.common.network.VisionNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

@Mod(WaterVision.ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaterVision {
    public static final String ID = "watervision";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final Marker IT = MarkerManager.getMarker("Main");
    public static final ResourceLocation LOADING_ANIM_TEXTURE = ResourceLocation.tryBuild(ID, "loading_animation");
    private static int ticks = 0;

    public WaterVision() {}

    @SubscribeEvent
    public static void onCommandsRegister(final RegisterCommandsEvent event) {
        VisionCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onClientCommandsRegister(final RegisterClientCommandsEvent event) {
        VisionCommands.registerClient(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLevelTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (ticks == Integer.MAX_VALUE) ticks = 0;
            ticks++;
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {

        @SubscribeEvent
        public static void setup(final FMLCommonSetupEvent event) {
            LOGGER.debug("Registering Network...");
            event.enqueueWork(VisionNetwork::init);
        }

        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void clientSetup(final FMLClientSetupEvent event) {
            LOGGER.debug("Client setup...");
        }
    }

    public static int getTicks() {
        return ticks;
    }

    @OnlyIn(Dist.CLIENT)
    public static float deltaFrames() {
        return Minecraft.getInstance().isPaused() ? 1.0F : Minecraft.getInstance().getFrameTime();
    }
}
