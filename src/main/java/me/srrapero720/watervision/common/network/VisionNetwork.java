package me.srrapero720.watervision.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber
public class VisionNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.commonToClient(PlayVideoPacket.PACKET_TYPE, PlayVideoPacket.STREAM_CODEC, Packet::exec);
        registrar.commonToClient(PlayVideoOverlayPacket.PACKET_TYPE, PlayVideoOverlayPacket.STREAM_CODEC, Packet::exec);
        registrar.commonToClient(StopVideoPacket.PACKET_TYPE, StopVideoPacket.STREAM_CODEC, Packet::exec);
        registrar.commonToClient(StopVideoOverlayPacket.PACKET_TYPE, StopVideoOverlayPacket.STREAM_CODEC, Packet::exec);
    }

    public static void sendTo(Packet playVideoOverlayPacket, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, playVideoOverlayPacket);
    }
}
