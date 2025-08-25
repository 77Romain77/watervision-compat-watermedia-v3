package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.client.screens.VisionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.net.URI;

public class PlayVideoPacket extends Packet<PlayVideoPacket> {
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

    @Override
    public void read(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.volume = buf.readInt();
        this.speed = buf.readFloat();
        this.stretch = buf.readBoolean();
        this.gameFadeDuration = buf.readFloat();
        this.videoFadeDuration = buf.readFloat();
        this.controls = buf.readBoolean();
        this.exit = buf.readBoolean();
    }
}
