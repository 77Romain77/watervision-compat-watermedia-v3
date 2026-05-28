package me.srrapero720.watervision.client;

import me.srrapero720.watervision.WaterVision;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VisionClientSettings {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("watervision-client.properties");
    private static final String CINEMATIC_VOLUME_KEY = "cinematicVolume";
    private static final int DEFAULT_CINEMATIC_VOLUME = 100;
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;

    private static boolean loaded;
    private static int cinematicVolume = DEFAULT_CINEMATIC_VOLUME;

    private VisionClientSettings() {}

    public static int cinematicVolume() {
        loadIfNeeded();
        return cinematicVolume;
    }

    public static void setCinematicVolume(final int volume) {
        loadIfNeeded();
        final int clamped = clamp(volume, MIN_VOLUME, MAX_VOLUME);
        if (cinematicVolume == clamped) return;
        cinematicVolume = clamped;
        save();
    }

    public static int clampVolume(final int volume) {
        return clamp(volume, MIN_VOLUME, MAX_VOLUME);
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;

        final Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (final InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
            } catch (final IOException exception) {
                WaterVision.LOGGER.warn("Failed to load WaterVision client settings, using defaults", exception);
            }
        }

        cinematicVolume = parseVolume(properties.getProperty(CINEMATIC_VOLUME_KEY), DEFAULT_CINEMATIC_VOLUME);
        save();
    }

    private static int parseVolume(final String value, final int fallback) {
        if (value == null) return fallback;
        try {
            return clamp(Integer.parseInt(value.trim()), MIN_VOLUME, MAX_VOLUME);
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void save() {
        final Properties properties = new Properties();
        properties.setProperty(CINEMATIC_VOLUME_KEY, String.valueOf(cinematicVolume));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (final OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "WaterVision client settings");
            }
        } catch (final IOException exception) {
            WaterVision.LOGGER.warn("Failed to save WaterVision client settings", exception);
        }
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
