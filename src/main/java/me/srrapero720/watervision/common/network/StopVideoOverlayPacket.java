package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static me.srrapero720.watervision.WaterVision.ID;

public record StopVideoOverlayPacket() implements Packet {
    static final PacketType<StopVideoOverlayPacket> TYPE = PacketType.create(new ResourceLocation(ID, "stop_video_overlay_packet"), StopVideoOverlayPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.closeOverlay();
    }

    @Override
    public void execServer(ServerPlayer player) {

    }

    @Override
    public void write(FriendlyByteBuf buf) {

    }
    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static StopVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new StopVideoOverlayPacket();
    }
}
