package me.srrapero720.watervision.common.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class VisionNetwork {
    public static void init() {
        PayloadTypeRegistry.playS2C().register(PlayVideoPacket.PACKET_TYPE, PlayVideoPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayVideoOverlayPacket.PACKET_TYPE, PlayVideoOverlayPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StopVideoOverlayPacket.PACKET_TYPE, StopVideoOverlayPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StopVideoPacket.PACKET_TYPE, StopVideoPacket.STREAM_CODEC);
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayVideoPacket.PACKET_TYPE, VisionNetwork::handle);
        ClientPlayNetworking.registerGlobalReceiver(PlayVideoOverlayPacket.PACKET_TYPE, VisionNetwork::handle);
        ClientPlayNetworking.registerGlobalReceiver(StopVideoOverlayPacket.PACKET_TYPE, VisionNetwork::handle);
        ClientPlayNetworking.registerGlobalReceiver(StopVideoPacket.PACKET_TYPE, VisionNetwork::handle);
    }

    @Environment(EnvType.CLIENT)
    private static <T extends Packet> void handle(T packet, ClientPlayNetworking.Context context) {
        packet.exec(context.player(), context.responseSender());
    }

    public static void sendTo(final Packet packet, final ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }
}
