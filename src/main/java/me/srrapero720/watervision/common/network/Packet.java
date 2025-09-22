package me.srrapero720.watervision.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public abstract class Packet<T extends Packet<T>> {

    public void exec(NetworkEvent.Context context) {
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
    protected abstract void execClient(Player player);
    protected abstract void execServer(ServerPlayer player);

    public abstract void write(FriendlyByteBuf buf);
    public abstract void read(FriendlyByteBuf buf);
}
