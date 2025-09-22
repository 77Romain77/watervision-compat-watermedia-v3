package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record StopVideoOverlayPacket() implements Packet {
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

    public static StopVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new StopVideoOverlayPacket();
    }
}
