package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVisionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class StopVideoPacket extends Packet<StopVideoPacket> {
    @Override
    protected void execClient(Player player) {
        WaterVisionClient.closeScreen();
    }

    @Override
    protected void execServer(ServerPlayer player) {

    }

    @Override
    public void write(FriendlyByteBuf buf) {

    }

    @Override
    public void read(FriendlyByteBuf buf) {

    }
}
