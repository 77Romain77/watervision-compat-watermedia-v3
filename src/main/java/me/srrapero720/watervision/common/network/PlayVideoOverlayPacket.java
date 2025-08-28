package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.util.function.Function;

import static me.srrapero720.watervision.WaterVision.ID;

public record PlayVideoOverlayPacket(String url) implements Packet {
    static final PacketType<PlayVideoOverlayPacket> TYPE = PacketType.create(new ResourceLocation(ID, "play_video_overlay_packet"), PlayVideoOverlayPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.openOverlay(URI.create(this.url));
    }

    @Override
    public void execServer(ServerPlayer player) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static PlayVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new PlayVideoOverlayPacket(buf.readUtf());
    }
}
