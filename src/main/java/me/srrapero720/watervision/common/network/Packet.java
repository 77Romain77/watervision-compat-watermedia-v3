package me.srrapero720.watervision.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public interface Packet extends CustomPacketPayload {

    default void exec(IPayloadContext context) {
        context.enqueueWork(() -> this.exectute(context.connection().getDirection().isClientbound(), context.player()));
    }

    private void exectute(boolean client, Player player) {
        if (client) {
            this.execClient(player);
        } else {
            this.execServer((ServerPlayer) player);
        }
    }

    void execClient(Player player);
    void execServer(ServerPlayer player);

    void encode(FriendlyByteBuf buf);
}
