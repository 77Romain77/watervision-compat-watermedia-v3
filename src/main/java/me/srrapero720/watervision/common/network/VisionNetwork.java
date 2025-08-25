package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.*;

public class VisionNetwork {
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(WaterVision.ID, "network");

    private static SimpleChannel INSTANCE;

    private static int nextId = 0;

    public static void init() {
        INSTANCE = ChannelBuilder.named(NAME)
                .simpleChannel()
                    .play()
                        .clientbound()
                        .add(PlayVideoPacket.class, PlayVideoPacket.STREAM_CODEC, Packet::exec)
                .build();

    }

    public static <MSG> void sendTo(MSG msg, ServerPlayer player) {
        INSTANCE.send(msg, player.connection.getConnection());
    }

    public static <MSG> void sendToClient(MSG message, Level level, BlockPos pos) {
        sendToClient(message, level.getChunkAt(pos));
    }

    public static <MSG> void sendToClient(MSG msg, LevelChunk chunk) {
        INSTANCE.send(msg, PacketDistributor.TRACKING_CHUNK.with(chunk));
    }

    public static <MSG> void sendToAllTracking(MSG msg, LivingEntity entityToTrack) {
        INSTANCE.send(msg, PacketDistributor.TRACKING_ENTITY.with(entityToTrack));
    }

    public static <MSG> void sendToAll(MSG msg) {
        INSTANCE.send(msg, PacketDistributor.ALL.noArg());
    }
}
