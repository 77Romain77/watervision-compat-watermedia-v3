package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record StopVideoPacket() implements Packet {
    public static final CustomPacketPayload.Type<StopVideoPacket> PACKET_TYPE = new Type<>(ResourceLocation.tryBuild(WaterVision.ID, "stop_video_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StopVideoPacket> STREAM_CODEC = StreamCodec.ofMember(StopVideoPacket::write, StopVideoPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.closeScreen();
    }

    @Override
    public void execServer(ServerPlayer player) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    public static StopVideoPacket decode(FriendlyByteBuf buf) {
        return new StopVideoPacket();
    }
}
