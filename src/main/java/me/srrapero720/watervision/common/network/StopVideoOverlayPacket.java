package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record StopVideoOverlayPacket() implements Packet {
    public static final CustomPacketPayload.Type<StopVideoOverlayPacket> PACKET_TYPE = new Type<>(ResourceLocation.tryBuild(WaterVision.ID, "stop_video_overlay_packet"));
    public static final StreamCodec<FriendlyByteBuf, StopVideoOverlayPacket> STREAM_CODEC = StreamCodec.ofMember(StopVideoOverlayPacket::encode, StopVideoOverlayPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.closeOverlay();
    }

    @Override
    public void execServer(ServerPlayer player) {

    }

    @Override
    public void encode(FriendlyByteBuf buf) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_TYPE;
    }

    public static StopVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new StopVideoOverlayPacket();
    }
}
