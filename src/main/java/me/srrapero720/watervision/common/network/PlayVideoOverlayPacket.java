package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.net.URI;

public class PlayVideoOverlayPacket extends Packet<PlayVideoOverlayPacket> {
    public String url;

    public PlayVideoOverlayPacket() {}

    public PlayVideoOverlayPacket(String url) {
        this.url = url;
    }

    @Override
    protected void execClient(Player player) {
        WaterVisionClient.openOverlay(URI.create(this.url));
    }

    @Override
    protected void execServer(ServerPlayer player) {
        throw new UnsupportedOperationException("Packet its S2C only");
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
    }
}
