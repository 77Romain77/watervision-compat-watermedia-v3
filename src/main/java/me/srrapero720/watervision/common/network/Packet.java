package me.srrapero720.watervision.common.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public interface Packet extends CustomPacketPayload {
    default void exec(Player player, PacketSender sender) {
        this.exectute(!(player instanceof ServerPlayer), player);
    }

    private void exectute(boolean client, Player player) {
        if (client) {
            this.executeClient();
        } else {
            final ServerPlayer sender = (ServerPlayer) player;
            this.execServer(sender);
        }
    }

    @Environment(EnvType.CLIENT)
    private void executeClient() {
        this.execClient(Minecraft.getInstance().player);
    }

    @Environment(EnvType.CLIENT)
    void execClient(Player player);
    void execServer(ServerPlayer player);

    void write(FriendlyByteBuf buf);
}
