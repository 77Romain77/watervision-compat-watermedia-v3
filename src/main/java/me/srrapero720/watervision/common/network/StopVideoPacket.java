package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static me.srrapero720.watervision.WaterVision.ID;

public record StopVideoPacket() implements Packet {
    static final PacketType<StopVideoPacket> TYPE = PacketType.create(new ResourceLocation(ID, "stop_video_packet"), StopVideoPacket::decode);


    @Override
    public void execClient(Player player) {
        WaterVisionClient.closeScreen();
    }

    @Override
    public void execServer(ServerPlayer player) {

    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {

    }

    public static StopVideoPacket decode(FriendlyByteBuf buf) {
        return new StopVideoPacket();
    }
}
