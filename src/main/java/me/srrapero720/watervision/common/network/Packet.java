package me.srrapero720.watervision.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public interface Packet {

    default void exec(final Supplier<NetworkEvent.Context> supplier) {
        final var context = supplier.get();
        context.enqueueWork(() -> this.exectute(context.getDirection().getReceptionSide().isClient(), context.getSender()));
        context.setPacketHandled(true);
    }

    private void exectute(boolean client, Player player) {
        if (client) {
            this.executeClient();
        } else {
            final ServerPlayer sender = (ServerPlayer) player;
            this.execServer(sender);
            if (sender != null) {
                VisionNetwork.sendToClient(this, sender.level(), sender.blockPosition());
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void executeClient() {
        this.execClient(Minecraft.getInstance().player);
    }

    @OnlyIn(Dist.CLIENT)
    void execClient(Player player);
    void execServer(ServerPlayer player);

    void encode(FriendlyByteBuf buf);
}
