package me.srrapero720.watervision.compat.watermedia;

import me.srrapero720.watervision.WaterVision;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.players.MediaPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Compatibility bridge for WaterMedia's session-wide FFmpeg hardware decoder setting.
 *
 * WaterMedia intentionally keeps its boot modules private, so this bridge uses a
 * narrow reflective lookup for the documented media.ffmpegHardwareAcceleration
 * setting. Hardware decoding remains enabled by default and is disabled only after
 * WaterVision detects a player that produced video metadata but no OpenGL texture.
 */
public final class WaterMediaDecoderCompat {

    private static final String HARDWARE_FLAG = "ffmpeghardwareacceleration";
    private static final int MAX_SCAN_DEPTH = 5;

    private static final String[] CONFIG_ROOTS = {
            "org.watermedia.WaterMediaConfig",
            "org.watermedia.WaterMedia",
            "org.watermedia.api.media.MediaAPI",
            "org.watermedia.api.media.MediaConfig",
            "org.watermedia.config.WaterMediaConfig",
            "org.watermedia.config.MediaConfig",
            "org.watermedia.api.config.WaterMediaConfig",
            "org.watermedia.api.configs.WaterMediaConfig"
    };

    private static volatile boolean softwareForcedForSession;
    private static volatile boolean hardwareSettingDisabled;
    private static volatile boolean lookupFailureLogged;

    private WaterMediaDecoderCompat() {
    }

    public static boolean softwareForcedForSession() {
        return softwareForcedForSession && hardwareSettingDisabled;
    }

    /**
     * Disables WaterMedia hardware decoding for players created after this call.
     * The choice is remembered only for the current game session.
     */
    public static synchronized boolean forceSoftwareForSession() {
        softwareForcedForSession = true;
        if (hardwareSettingDisabled) {
            return true;
        }

        final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean changed = scanClass(MediaAPI.class, visited, 0);

        for (final String className : CONFIG_ROOTS) {
            try {
                changed |= scanClass(Class.forName(className), visited, 0);
            } catch (final ClassNotFoundException ignored) {
                // WaterMedia moved configuration classes between 3.x builds.
            } catch (final Throwable throwable) {
                WaterVision.LOGGER.debug("WaterVision could not inspect WaterMedia config root {}", className, throwable);
            }
        }

        hardwareSettingDisabled = changed;
        if (changed) {
            WaterVision.LOGGER.warn("WaterVision disabled WaterMedia hardware decoding for the current session after a texture upload failure");
        } else if (!lookupFailureLogged) {
            lookupFailureLogged = true;
            WaterVision.LOGGER.error("WaterVision could not locate WaterMedia setting media.ffmpegHardwareAcceleration; software decoder fallback is unavailable");
        }
        return changed;
    }

    /**
     * Returns WaterMedia's current decoder mode when the implementation exposes
     * FFMediaPlayer#isHwAccel(). Null means the implementation did not expose it.
     */
    public static Boolean hardwareAccelerationState(final MediaPlayer player) {
        if (player == null) {
            return null;
        }
        Class<?> type = player.getClass();
        while (type != null) {
            try {
                final Method method = type.getDeclaredMethod("isHwAccel");
                method.setAccessible(true);
                final Object result = method.invoke(player);
                return result instanceof Boolean value ? value : null;
            } catch (final NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (final Throwable throwable) {
                WaterVision.LOGGER.debug("WaterVision could not read WaterMedia hardware decoder state", throwable);
                return null;
            }
        }
        return null;
    }

    private static boolean scanClass(final Class<?> type, final Set<Object> visited, final int depth) {
        if (type == null || depth > MAX_SCAN_DEPTH) {
            return false;
        }
        return scanObject(null, type, true, visited, depth);
    }

    private static boolean scanObject(final Object owner,
                                      final Class<?> type,
                                      final boolean staticOnly,
                                      final Set<Object> visited,
                                      final int depth) {
        if (type == null || depth > MAX_SCAN_DEPTH) {
            return false;
        }
        if (owner != null && !visited.add(owner)) {
            return false;
        }

        boolean changed = invokeBooleanSetters(owner, type, staticOnly);

        for (final Field field : type.getDeclaredFields()) {
            final boolean isStatic = Modifier.isStatic(field.getModifiers());
            if (staticOnly != isStatic) {
                continue;
            }
            try {
                field.setAccessible(true);
                final String fieldName = normalize(field.getName());
                if (HARDWARE_FLAG.equals(fieldName)) {
                    changed |= disableField(owner, field);
                    continue;
                }

                if (depth >= MAX_SCAN_DEPTH || !isConfigPath(fieldName, field.getType())) {
                    continue;
                }
                final Object value = field.get(owner);
                if (value != null) {
                    changed |= scanObject(value, value.getClass(), false, visited, depth + 1);
                }
            } catch (final Throwable ignored) {
                // Keep probing other known roots/paths.
            }
        }

        if (depth < MAX_SCAN_DEPTH) {
            changed |= scanGetterPaths(owner, type, staticOnly, visited, depth);
        }
        return changed;
    }

    private static boolean invokeBooleanSetters(final Object owner, final Class<?> type, final boolean staticOnly) {
        boolean changed = false;
        for (final Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != staticOnly || method.getParameterCount() != 1) {
                continue;
            }
            final String name = normalize(method.getName());
            if (!name.equals(HARDWARE_FLAG) && !name.equals("set" + HARDWARE_FLAG)) {
                continue;
            }
            final Class<?> parameter = method.getParameterTypes()[0];
            if (parameter != boolean.class && parameter != Boolean.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(owner, false);
                changed = true;
            } catch (final Throwable ignored) {
                // Try fields and wrapper values next.
            }
        }
        return changed;
    }

    private static boolean disableField(final Object owner, final Field field) {
        try {
            final Class<?> type = field.getType();
            if (type == boolean.class || type == Boolean.class) {
                field.set(owner, false);
                return true;
            }
            final Object holder = field.get(owner);
            return disableBooleanHolder(holder);
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean disableBooleanHolder(final Object holder) {
        if (holder == null) {
            return false;
        }
        for (final Method method : holder.getClass().getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            final String name = normalize(method.getName());
            if (!name.equals("set") && !name.equals("setvalue") && !name.equals("value") && !name.equals("update") && !name.equals("accept")) {
                continue;
            }
            final Class<?> parameter = method.getParameterTypes()[0];
            if (parameter != boolean.class && parameter != Boolean.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(holder, false);
                return true;
            } catch (final Throwable ignored) {
                // Try the next conventional setter.
            }
        }
        return false;
    }

    private static boolean scanGetterPaths(final Object owner,
                                           final Class<?> type,
                                           final boolean staticOnly,
                                           final Set<Object> visited,
                                           final int depth) {
        boolean changed = false;
        for (final Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != staticOnly || method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            final String name = normalize(method.getName());
            if (HARDWARE_FLAG.equals(name)) {
                try {
                    method.setAccessible(true);
                    changed |= disableBooleanHolder(method.invoke(owner));
                } catch (final Throwable ignored) {
                    // Continue with other access paths.
                }
                continue;
            }
            if (!isConfigPath(name, method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                final Object value = method.invoke(owner);
                if (value != null) {
                    changed |= scanObject(value, value.getClass(), false, visited, depth + 1);
                }
            } catch (final Throwable ignored) {
                // Getter may require boot state that is not available; ignore it.
            }
        }
        return changed;
    }

    private static boolean isConfigPath(final String memberName, final Class<?> memberType) {
        if (memberType.isPrimitive() || memberType.isEnum() || memberType == String.class || memberType == Class.class) {
            return false;
        }
        final Package memberPackage = memberType.getPackage();
        final String packageName = memberPackage == null ? "" : memberPackage.getName();
        final String typeName = normalize(memberType.getSimpleName());
        return packageName.startsWith("org.watermedia")
                || memberName.contains("config")
                || memberName.contains("setting")
                || memberName.equals("media")
                || typeName.contains("config")
                || typeName.contains("setting");
    }

    private static String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
