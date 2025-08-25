package me.srrapero720.watervision;

import me.srrapero720.watervision.common.commands.VisionCommands;
import me.srrapero720.watervision.common.network.VisionNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class WaterVision implements ModInitializer {
    public static final String ID = "watervision";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final Marker IT = MarkerManager.getMarker("Main");
    public static final ResourceLocation LOADING_ANIM_TEXTURE = ResourceLocation.tryBuild(ID, "loading_animation");
    static int ticks = 0;

    @Override
    public void onInitialize() {
        VisionNetwork.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VisionCommands.register(dispatcher)
        );
    }

    public static int getTicks() {
        return ticks;
    }

    @Environment(EnvType.CLIENT)
    public static float deltaFrames() {
        return Minecraft.getInstance().isPaused() ? 1.0F : (Minecraft.getInstance().getFrameTimeNs() / 1_000_000_000.0F) * 20.0F;
    }
}
