package me.srrapero720.watervision.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.srrapero720.watervision.WaterVision;
import me.srrapero720.watervision.WaterVisionClient;
import me.srrapero720.watervision.common.network.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.net.URI;

public class VisionCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playvideo")
                .requires(source -> !source.isPlayer() || source.hasPermission(4))
                .then(Commands.argument("url", StringArgumentType.string())
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(VisionCommands::openVideoScreen)
                                .then(Commands.argument("volume", IntegerArgumentType.integer(0, 100))
                                        .executes(VisionCommands::openVideoScreen)
                                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.25f, 2.0f))
                                                .executes(VisionCommands::openVideoScreen)
                                                .then(Commands.argument("stretch_video", BoolArgumentType.bool())
                                                        .executes(VisionCommands::openVideoScreen)
                                                        .then(Commands.argument("game_fade_duration", FloatArgumentType.floatArg(0.0f, 100.0f))
                                                                .executes(VisionCommands::openVideoScreen)
                                                                .then(Commands.argument("video_fade_duration", FloatArgumentType.floatArg(0.0f, 100.0f))
                                                                        .executes(VisionCommands::openVideoScreen)
                                                                        .then(Commands.argument("allow_controls", BoolArgumentType.bool())
                                                                                .executes(VisionCommands::openVideoScreen)
                                                                                .then(Commands.argument("allow_exit", BoolArgumentType.bool())
                                                                                        .executes(VisionCommands::openVideoScreen)
                                                                                )
                                                                        )
                                                                )

                                                        )

                                                )
                                        )
                                )
                        )
                )
        );


        dispatcher.register(Commands.literal("stopvideo")
                .requires(source -> !source.isPlayer() || source.hasPermission(4))
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(VisionCommands::stopVideo)
                )
        );
        dispatcher.register(Commands.literal("playoverlay")
                .requires(source -> !source.isPlayer() || source.hasPermission(4))
                .then(Commands.argument("url", StringArgumentType.string())
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(VisionCommands::openVideoOverlay)
                        )
                )
        );

        dispatcher.register(Commands.literal("stopoverlay")
                .requires(source -> !source.isPlayer() || source.hasPermission(4))
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(VisionCommands::closeVideoOverlay)
                )
        );
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playvideoclient")
                .requires(source -> source.getEntity() != null)
                .then(Commands.argument("url", StringArgumentType.string())
                        .executes(VisionCommands::openVideoScreenClient)
                        .then(Commands.argument("volume", IntegerArgumentType.integer(0, 100))
                                .executes(VisionCommands::openVideoScreenClient)
                                .then(Commands.argument("speed", FloatArgumentType.floatArg(0.25f, 2.0f))
                                        .executes(VisionCommands::openVideoScreenClient)
                                        .then(Commands.argument("stretch_video", BoolArgumentType.bool())
                                                .executes(VisionCommands::openVideoScreenClient)
                                        )
                                )
                        )
                )
        );

        dispatcher.register(Commands.literal("playoverlayclient")
                .requires(source -> source.getEntity() != null)
                .then(Commands.argument("url", StringArgumentType.string())
                        .executes(VisionCommands::openVideoOverlayClient)
                )
        );

        dispatcher.register(Commands.literal("stopoverlayclient")
                .requires(source -> source.getEntity() != null)
                .executes(VisionCommands::closeVideoOverlayClient)
        );
    }

    @OnlyIn(Dist.CLIENT)
    private static int openVideoScreenClient(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var url = StringArgumentType.getString(context, "url");
            final var volume = getIntOrDefault(context, "volume", 100);
            final var speed = getFloatOrDefault(context, "speed", 1.0f);
            final var stretchVideo = getBoolOrDefault(context, "stretch_video", false);

            WaterVisionClient.openScreen(URI.create(url), volume, speed, stretchVideo, WaterVisionClient.DEF_GAME_FADE_DURATION, WaterVisionClient.DEF_VIDEO_FADE_DURATION, true, true);
            return 0;
        } catch (final Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see log for more details"));
            WaterVision.LOGGER.error("Failed to execute /playvideoclient command", e);
        }

        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    private static int openVideoOverlayClient(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var url = StringArgumentType.getString(context, "url");

            WaterVisionClient.openOverlay(URI.create(url));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playoverlay command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }
        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    private static int closeVideoOverlayClient(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {

            WaterVisionClient.closeOverlay();
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playoverlay command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }
        return 1;
    }

    private static int openVideoScreen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var url = StringArgumentType.getString(context, "url");
            final var players = EntityArgument.getPlayers(context, "target");
            final var volume = getIntOrDefault(context, "volume", 100);
            final var speed = getFloatOrDefault(context, "speed", 1.0f);
            final var stretchVideo = getBoolOrDefault(context, "stretch_video", false);
            final var gameFadeDuration = getFloatOrDefault(context, "game_fade_duration", 20.0f);
            final var videoFadeDuration = getFloatOrDefault(context, "video_fade_duration", 20.0f);
            final var allowControls = getBoolOrDefault(context, "allow_controls", true);
            final var exit = getBoolOrDefault(context, "allow_exit", true);

            for (final var player: players) {
                PacketDistributor.sendToPlayer(player, new PlayVideoPacket(url, volume, speed, stretchVideo, gameFadeDuration, videoFadeDuration, allowControls, exit));
            }

            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playvideo command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }

        return 1;
    }

    private static int openVideoOverlay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var url = StringArgumentType.getString(context, "url");
            final var players = EntityArgument.getPlayers(context, "target");

            for (final var player: players) {
                VisionNetwork.sendTo(new PlayVideoOverlayPacket(url), player);
            }

            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playoverlay command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }
        return 1;
    }

    private static int closeVideoOverlay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var players = EntityArgument.getPlayers(context, "target");

            for (final var player: players) {
                VisionNetwork.sendTo(new StopVideoOverlayPacket(), player);
            }

            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playoverlay command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }
        return 1;
    }

    private static int stopVideo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            final var players = EntityArgument.getPlayers(context, "target");

            for (final var player: players) {
                VisionNetwork.sendTo(new StopVideoPacket(), player);
            }

            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open video screen, see console for more details"));
            WaterVision.LOGGER.error("Failed to execute /playoverlay command", e);

            if (e instanceof CommandSyntaxException) {
                throw e;
            }
        }
        return 1;
    }

    private static String getStringOrDefault(CommandContext<CommandSourceStack> context, String name, String def) {
        try {
            return StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static int getIntOrDefault(CommandContext<CommandSourceStack> context, String name, int def) {
        try {
            return IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static float getFloatOrDefault(CommandContext<CommandSourceStack> context, String name, float def) {
        try {
            return FloatArgumentType.getFloat(context, name);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static boolean getBoolOrDefault(CommandContext<CommandSourceStack> context, String name, boolean def) {
        try {
            return BoolArgumentType.getBool(context, name);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
