package me.srrapero720.watervision.compat.regionmusic;

import me.srrapero720.watervision.WaterVision;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional client-side bridge to RegionMusicClient.
 *
 * <p>The integration is reflection-based so RegionMusic remains fully optional
 * and WaterVision still loads with older RegionMusic builds that do not expose
 * the client API.</p>
 */
public final class RegionMusicCompat {
    private static final String MOD_ID = "regionmusicclient";
    private static final String API_CLASS = "fr.watherfoxx.regionmusic.client.RegionMusicClientApi";
    private static final String PAUSE_SOURCE = "watervision";

    private static volatile boolean resolved;
    private static volatile Method setPausedMethod;

    private RegionMusicCompat() {}

    public static void setCinematicActive(final boolean active) {
        final Method method = resolveSetPausedMethod();
        if (method == null) return;

        try {
            method.invoke(null, PAUSE_SOURCE, active);
            WaterVision.LOGGER.info("WaterVision RegionMusic pause: active={}", active);
        } catch (final IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            WaterVision.LOGGER.warn("WaterVision could not update RegionMusic pause state: active={}", active, exception);
        } catch (final LinkageError error) {
            WaterVision.LOGGER.warn("WaterVision RegionMusic compatibility failed because of a linkage error", error);
        }
    }

    private static Method resolveSetPausedMethod() {
        if (resolved) return setPausedMethod;

        synchronized (RegionMusicCompat.class) {
            if (resolved) return setPausedMethod;
            resolved = true;

            if (!ModList.get().isLoaded(MOD_ID)) return null;

            try {
                final Class<?> apiClass = Class.forName(API_CLASS, false, RegionMusicCompat.class.getClassLoader());
                setPausedMethod = apiClass.getMethod("setPaused", String.class, boolean.class);
                WaterVision.LOGGER.info("WaterVision RegionMusic compatibility enabled");
            } catch (final ClassNotFoundException | NoSuchMethodException exception) {
                WaterVision.LOGGER.warn("RegionMusicClient is installed, but its pause API is unavailable; install a compatible RegionMusicClient build", exception);
            } catch (final LinkageError error) {
                WaterVision.LOGGER.warn("RegionMusicClient is installed, but its pause API could not be linked", error);
            }

            return setPausedMethod;
        }
    }
}
