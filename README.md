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
[cinematic-ui-watermedia-023-sequential-loading]
```

Expected overlay build tag:

```text
[overlay-watermedia-023-debug]
```

Expected proxy build tag:

```text
[streaming-proxy-004]
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
1. Check for a valid local cache before opening any remote player (validation capped at 5 seconds).
2. If no usable cache exists, try native WaterMedia streaming from the original URL.
3. If native playback fails before the first frame, use the local 127.0.0.1 streaming proxy.
4. Only if proxy playback fails, stop its transfers and download one complete local file.
5. Play the complete cached file. An unreadable initial cache falls back to native streaming.
```

The cache is stored in:

```text
<minecraft folder>/watervision-cache/
```

Cached files are validated with the available remote metadata:

- `ETag`
- `Last-Modified`
- `Content-Length`

Incomplete downloads are rejected and temporary files are cleaned up on failure/cancellation.
There is no background full download during native or proxy streaming. Consequently,
successful streaming does not populate WaterVision's persistent cache; the complete-download
fallback does. Skipping/closing a cinematic interrupts cache requests and closes proxy transfers.
The proxy forwards the upstream `Accept-Ranges` header instead of claiming range support.

After 15 seconds without a first image, a wrapped French loading message explains that the
connection or video host may be slow. The timer spans retries and does not change their thresholds.
Cache HTTP connections are bounded to 5 seconds and complete downloads to 10 minutes.

Run `bash scripts/test-network.sh` with Java 17 for the local HTTP regression tests.
Full rendering and native WaterMedia behavior still require in-game testing.

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
WaterVision player created [cinematic-ui-watermedia-023-sequential-loading]
WaterVision player resume requested [cinematic-ui-watermedia-023-sequential-loading]
WaterVision first frame ready [cinematic-ui-watermedia-023-sequential-loading]
WaterVision first video texture rendered [cinematic-ui-watermedia-023-sequential-loading]
WaterVision RegionMusic compatibility enabled
WaterVision RegionMusic pause: active=true
WaterVision RegionMusic pause: active=false
```

## License

This mod is licensed under the PolyForm Strict License 1.0.0.
