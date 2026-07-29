# WaterVision — WaterMedia 3.0.0.23 compatibility branch

WaterVision is a Minecraft Forge 1.20.1 mod used to play fullscreen cinematic videos and video overlays in-game through commands.

This branch targets **WaterMedia 3.0.0.23** and keeps the TFOT cinematic improvements developed on the 3.0.0.21 branch.

## Target environment

```text
Minecraft Forge 1.20.1
WaterMedia 3.0.0.23
A WaterMedia Binaries build compatible with WaterMedia 3.0.0.23
Optional: Simple Voice Chat 1.20.1-2.6.16 / voicechat_api 2.6.13+
Optional: RegionMusic Client with RegionMusicClientApi
```

The WaterMedia dependency is deliberately restricted to:

```text
[3.0.0.23,3.0.0.24)
```

WaterMedia 3.0.0.22 and 3.0.0.23 introduced binary-breaking API changes. Pinning the range prevents Forge from loading this build with a future incompatible version without recompiling WaterVision.

## Branch status

- Forge 1.20.1 compilation: passing.
- WaterMedia 3.0.0.23 API migration: complete.
- Runtime validation in the TFOT modpack: required before production deployment.
- Older WaterMedia compatibility branches remain untouched.

Expected fullscreen cinematic build tag:

```text
[cinematic-ui-watermedia-023-streaming-proxy-safe]
```

Expected overlay build tag:

```text
[overlay-watermedia-023-debug]
```

Expected proxy build tag:

```text
[streaming-proxy-003]
```

## WaterMedia 3.0.0.23 migration

The fullscreen player and overlay now use the current API:

- `MediaAPI.mrl(URI)` instead of the removed `MediaAPI.getMRL(URI)`.
- `MediaAPI.createPlayer(...)` for player creation.
- `MediaAPI.glEngine(Thread, Executor)` instead of `GLEngine.Builder`.
- `MediaAPI.alEngine()` instead of `ALEngine.buildDefault()`.
- The boolean results returned by `start()` and `startPaused()` are checked.
- A refused fullscreen startup enters the existing proxy/cache fallback chain.
- A refused overlay startup closes the overlay cleanly and reports the failure.

WaterMedia now owns the OpenGL state handling used by its video engine. WaterVision no longer provides manual `GlStateManager` callbacks to `GLEngine`.

## Fullscreen cinematic features

### HTTP proxy and persistent cache

WaterVision keeps the existing fallback chain for direct remote videos:

```text
1. Try the original remote URL.
2. If playback fails before the first frame, inspect the local cache.
3. Play a valid cached file immediately when available.
4. Otherwise start the local 127.0.0.1 streaming proxy.
5. Download the full video into the persistent cache in the background.
6. If proxy playback also fails, wait for the complete local file and retry it.
```

The cache is stored in:

```text
<minecraft folder>/watervision-cache/
```

Cached files are validated with the available remote metadata:

- `ETag`
- `Last-Modified`
- `Content-Length`

Incomplete downloads are rejected.

### Playback controls

When `allow_controls = true`:

- `Space`: pause or resume.
- `Arrow Right`: seek forward by 5 seconds.
- `Arrow Left`: seek backward by 5 seconds.

A short cooldown prevents excessive seek requests.

### Cinematic volume

- `Arrow Up`: increase the client cinematic volume.
- `Arrow Down`: decrease the client cinematic volume.
- The client preference is stored and reused for later cinematics.
- The command volume remains the server-provided base volume.

Effective volume:

```text
command_volume * client_cinematic_volume / 100
```

### Safe skip

When `allow_exit = true`, the player must hold `Escape` for about two seconds. A progress indicator is displayed while holding the key.

### Dynamic tips

The tips panel adapts to the enabled controls and can be toggled with `K`.

### Exclusive cinematic audio

While a fullscreen cinematic is active:

- existing Minecraft sounds are paused;
- new Minecraft sounds are muted at the sound-engine level;
- received Simple Voice Chat audio is cancelled when the optional API is present;
- RegionMusic is paused through `RegionMusicClientApi.setPaused("watervision", true)` when a compatible RegionMusic build is installed.

At the end of the cinematic, RegionMusic receives:

```java
RegionMusicClientApi.setPaused("watervision", false);
```

The integration is reflection-based, so RegionMusic remains optional. No Minecraft volume value is written to `options.txt`.

## Commands

### `/video`

```text
/video <"url"> <targets> [volume] [speed] [stretch_video] [game_fade_duration] [video_fade_duration] [allow_controls] [allow_exit]
```

Example:

```text
/video "https://example.com/video.mp4" @a 100 1.0 false 20.0 20.0 true true
```

### `/videoclient`

```text
/videoclient <"url">
```

Plays a fullscreen video only on the local client.

## Runtime validation checklist

Before deploying this branch, test:

1. Client startup with WaterMedia 3.0.0.23 and its compatible binaries.
2. A direct remote MP4.
3. A cached MP4.
4. Proxy fallback and complete-download fallback.
5. Pause, resume, seek, volume and hold-to-skip.
6. Natural video completion and forced closure.
7. Several consecutive cinematics to verify resource release.
8. The video overlay.
9. Minecraft audio restoration.
10. Simple Voice Chat mute and restoration.
11. RegionMusic pause and exact resume position.
12. Oculus/Embeddium rendering with the TFOT client pack.

Useful successful log entries include:

```text
WaterVision player created [cinematic-ui-watermedia-023-streaming-proxy-safe]
WaterVision player resume requested [cinematic-ui-watermedia-023-streaming-proxy-safe]
WaterVision first frame ready [cinematic-ui-watermedia-023-streaming-proxy-safe]
WaterVision first video texture rendered [cinematic-ui-watermedia-023-streaming-proxy-safe]
WaterVision RegionMusic compatibility enabled
WaterVision RegionMusic pause: active=true
WaterVision RegionMusic pause: active=false
```

## License

This mod is licensed under the PolyForm Strict License 1.0.0.
