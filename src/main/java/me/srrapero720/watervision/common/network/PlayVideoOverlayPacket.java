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

import java.net.URI;

public record PlayVideoOverlayPacket(String url) implements Packet {
    public static final CustomPacketPayload.Type<PlayVideoOverlayPacket> PACKET_TYPE = new Type<>(ResourceLocation.tryBuild(WaterVision.ID, "play_video_overlay_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayVideoOverlayPacket> STREAM_CODEC = StreamCodec.ofMember(PlayVideoOverlayPacket::write, PlayVideoOverlayPacket::decode);

    @Override
    public void execClient(Player player) {
        WaterVisionClient.openOverlay(URI.create(this.url));
    }

    @Override
    public void execServer(ServerPlayer player) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public Type<PlayVideoOverlayPacket> type() {
        return PACKET_TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
    }

    public static PlayVideoOverlayPacket decode(FriendlyByteBuf buf) {
        return new PlayVideoOverlayPacket(buf.readUtf());
    }
}
