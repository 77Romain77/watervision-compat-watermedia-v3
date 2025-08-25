package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public class VisionNetwork {
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(WaterVision.ID, "network");

    private static SimpleChannel INSTANCE;

    public static void init() {
        INSTANCE = ChannelBuilder.named(NAME)
                .simpleChannel()
                    .play()
                        .clientbound()
                        .add(PlayVideoPacket.class, PlayVideoPacket.STREAM_CODEC, Packet::exec)
                        .add(PlayVideoOverlayPacket.class, PlayVideoOverlayPacket.STREAM_CODEC, Packet::exec)
                        .add(StopVideoPacket.class, StopVideoPacket.STREAM_CODEC, Packet::exec)
                        .add(StopVideoOverlayPacket.class, StopVideoOverlayPacket.STREAM_CODEC, Packet::exec)
                .build();

    }

    public static <MSG> void sendTo(final MSG msg, final ServerPlayer player) {
        INSTANCE.send(msg, player.connection.getConnection());
    }

    public static <MSG> void sendToClient(final MSG message, final Level level, final BlockPos pos) {
        sendToClient(message, level.getChunkAt(pos));
    }

    public static <MSG> void sendToClient(final MSG msg, final LevelChunk chunk) {
        INSTANCE.send(msg, PacketDistributor.TRACKING_CHUNK.with(chunk));
    }

    public static <MSG> void sendToAllTracking(final MSG msg, final LivingEntity entityToTrack) {
        INSTANCE.send(msg, PacketDistributor.TRACKING_ENTITY.with(entityToTrack));
    }

    public static <MSG> void sendToAll(MSG msg) {
        INSTANCE.send(msg, PacketDistributor.ALL.noArg());
    }
}
