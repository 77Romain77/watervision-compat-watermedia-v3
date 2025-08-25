package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.net.URI;

public record PlayVideoPacket(String url, int volume, float speed, boolean stretch, float gameFadeDuration, float videoFadeDuration, boolean controls, boolean exit) implements Packet {
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayVideoPacket> STREAM_CODEC = StreamCodec.ofMember(PlayVideoPacket::encode, PlayVideoPacket::decode);

    @Override
    @OnlyIn(Dist.CLIENT)
    public void execClient(final Player player) {
        WaterVisionClient.openScreen(URI.create(this.url), this.volume, this.speed, this.stretch, this.gameFadeDuration, this.videoFadeDuration, this.controls, this.exit);
    }

    @Override
    public void execServer(final ServerPlayer player) {
        throw new UnsupportedOperationException("Packet its S2C only");
    }

    public void encode(FriendlyByteBuf buf) {
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