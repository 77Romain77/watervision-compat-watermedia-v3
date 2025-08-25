package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record StopVideoPacket() implements Packet {
    public static final StreamCodec<RegistryFriendlyByteBuf, StopVideoPacket> STREAM_CODEC = StreamCodec.ofMember(StopVideoPacket::encode, StopVideoPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.closeScreen();
    }

    @Override
    public void execServer(ServerPlayer player) {

    }

    @Override
    public void encode(FriendlyByteBuf buf) {
    }

    public static StopVideoPacket decode(FriendlyByteBuf buf) {
        return new StopVideoPacket();
    }
}
