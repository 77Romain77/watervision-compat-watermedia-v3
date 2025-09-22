package me.srrapero720.watervision.common.network;

import me.srrapero720.watervision.WaterVision;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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
        register(PlayVideoPacket.class, PlayVideoPacket::new);
        register(PlayVideoOverlayPacket.class, PlayVideoOverlayPacket::new);
    }

    private static <T extends Packet<T>> void register(Class<T> clazz, Supplier<T> factory) {
        INSTANCE.registerMessage(
                nextId++, clazz,
                // encode
                Packet::write,
                // decode
                buf -> {
                    final T msg = factory.get();
                    msg.read(buf);
                    return msg;
                },
                // handle
                (msg, ctx) -> msg.exec(ctx.get())
        );
    }

    public static <MSG> void sendTo(MSG msg, ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static <MSG> void sendToClient(MSG message, Level level, BlockPos pos) {
        sendToClient(message, level.getChunkAt(pos));
    }

    public static <MSG> void sendToClient(MSG msg, LevelChunk chunk) {
        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static <MSG> void sendToAllTracking(MSG msg, LivingEntity entityToTrack) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entityToTrack), msg);
    }

    public static <MSG> void sendToAll(MSG msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static <MSG> void sendToServer(MSG msg) {
        INSTANCE.sendToServer(msg);
    }
}
