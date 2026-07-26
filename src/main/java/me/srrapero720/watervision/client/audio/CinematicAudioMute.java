package me.srrapero720.watervision.client.audio;

import me.srrapero720.watervision.WaterVision;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary in-memory audio mute state used while a fullscreen cinematic is active.
 * Nothing is written to Minecraft options, so a crash cannot leave the player muted
 * on the next game launch.
 */
public final class CinematicAudioMute {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private CinematicAudioMute() {}

    public static boolean isActive() {
        return ACTIVE.get();
    }

    /**
     * @return true when the state actually changed
     */
    public static boolean setActive(final boolean active) {
        final boolean previous = ACTIVE.getAndSet(active);
        if (previous != active) {
            WaterVision.LOGGER.info("WaterVision cinematic audio mute: active={}", active);
            return true;
        }
        return false;
    }
}
