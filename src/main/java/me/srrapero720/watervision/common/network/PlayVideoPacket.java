package me.srrapero720.watervision.common.network;

import com.mojang.serialization.Codec;
import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.net.URI;

public class PlayVideoPacket extends Packet<PlayVideoPacket> {
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayVideoPacket> STREAM_CODEC = StreamCodec.ofMember(PlayVideoPacket::encode, PlayVideoPacket::decode);

    public String url;
    public int volume;
    public float speed;
    public boolean stretch;
    public float gameFadeDuration;
    public float videoFadeDuration;
    public boolean controls;
    public boolean exit;

    public PlayVideoPacket() {}

    public PlayVideoPacket(String url, int volume, float speed, boolean stretch, float gameFadeDuration, float videoFadeDuration, boolean controls, boolean exit) {
        this.url = url;
        this.volume = volume;
        this.speed = speed;
        this.stretch = stretch;
        this.gameFadeDuration = gameFadeDuration;
        this.videoFadeDuration = videoFadeDuration;
        this.controls = controls;
        this.exit = exit;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void execClient(final Player player) {
        Minecraft.getInstance().setScreen(new VisionScreen(URI.create(this.url), this.volume, this.speed, this.stretch, this.gameFadeDuration, this.videoFadeDuration, this.controls, this.exit));
    }

    @Override
    protected void execServer(final ServerPlayer player) {
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
