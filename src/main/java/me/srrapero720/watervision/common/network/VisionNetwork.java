package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.swing.plaf.PanelUI;
import java.util.function.Function;
import java.util.Objects;

public class VisionNetwork {
    public static void init() {

    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayVideoPacket.TYPE, Packet::exec);
        ClientPlayNetworking.registerGlobalReceiver(PlayVideoOverlayPacket.TYPE, Packet::exec);
        ClientPlayNetworking.registerGlobalReceiver(StopVideoOverlayPacket.TYPE, Packet::exec);
        ClientPlayNetworking.registerGlobalReceiver(StopVideoPacket.TYPE, Packet::exec);
    }

    public static void sendTo(Packet packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }
}
