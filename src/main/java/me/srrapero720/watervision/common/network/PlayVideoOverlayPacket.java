package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.net.URI;

public record PlayVideoOverlayPacket(String url) implements Packet {

    @Override
    public void execClient(Player player) {
        WaterVisionClient.openOverlay(URI.create(this.url));
    }

    @Override
    public void execServer(ServerPlayer player) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
    }

    public static PlayVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new PlayVideoOverlayPacket(buf.readUtf());
    }
}
