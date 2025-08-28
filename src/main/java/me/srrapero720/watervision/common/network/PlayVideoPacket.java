package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.net.URI;

import static me.srrapero720.watervision.WaterVision.ID;

public record PlayVideoPacket(String url, int volume, float speed, boolean stretch, float gameFadeDuration, float videoFadeDuration, boolean controls, boolean exit) implements Packet {
    static final PacketType<PlayVideoPacket> TYPE = PacketType.create(new ResourceLocation(ID, "play_video_packet"), PlayVideoPacket::decode);


    @Override
    @Environment(EnvType.CLIENT)
    public void execClient(final Player player) {
        WaterVisionClient.openScreen(URI.create(this.url), this.volume, this.speed, this.stretch, this.gameFadeDuration, this.videoFadeDuration, this.controls, this.exit);
    }

    @Override
    public void execServer(final ServerPlayer player) {
        throw new UnsupportedOperationException("Packet its S2C only");
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeInt(this.volume);
        buf.writeFloat(this.speed);
        buf.writeBoolean(this.stretch);
        buf.writeFloat(this.gameFadeDuration);
        buf.writeFloat(this.videoFadeDuration);
        buf.writeBoolean(this.controls);
        buf.writeBoolean(this.exit);
    }

    public static PlayVideoPacket decode(FriendlyByteBuf buf) {
        return new PlayVideoPacket(
                buf.readUtf(),
                buf.readInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }
}