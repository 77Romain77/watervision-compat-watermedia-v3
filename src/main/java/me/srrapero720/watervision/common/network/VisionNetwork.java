package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Function;
import java.util.function.Supplier;

public class VisionNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel INSTANCE;

    private static int nextId = 0;

    public static void init() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(WaterVision.ID, "network"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();

        // Register Packets
        register(PlayVideoPacket.class, PlayVideoPacket::decode);
        register(PlayVideoOverlayPacket.class, PlayVideoOverlayPacket::decode);
        register(StopVideoPacket.class, StopVideoPacket::decode);
        register(StopVideoOverlayPacket.class, StopVideoOverlayPacket::decode);
    }

    private static <T extends Packet> void register(final Class<T> type, final Function<FriendlyByteBuf, T> factory) {
        INSTANCE.registerMessage(nextId++, type, Packet::encode, factory, Packet::exec);
    }

    public static <MSG> void sendTo(final MSG msg, final ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static <MSG> void sendToClient(final MSG message, final Level level, final BlockPos pos) {
        sendToClient(message, level.getChunkAt(pos));
    }

    public static <MSG> void sendToClient(final MSG msg, final LevelChunk chunk) {
        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static <MSG> void sendToAllTracking(final MSG msg, final LivingEntity entityToTrack) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entityToTrack), msg);
    }

    public static <MSG> void sendToAll(final MSG msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static <MSG> void sendToServer(final MSG msg) {
        INSTANCE.sendToServer(msg);
    }
}
