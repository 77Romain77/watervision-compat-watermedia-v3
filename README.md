# WaterVision — WaterMedia 3.0.0.21 compatibility branch

WaterVision is a Minecraft Forge 1.20.1 mod used to play fullscreen cinematic videos in-game through commands.

This branch targets **WaterMedia 3.0.0.21** and adds a safer cinematic player layer for server cutscenes:

- WaterMedia 3.0.0.21 API compatibility.
- Client-side cinematic volume control.
- Pause/resume and seek controls.
- Hold-to-skip behavior.
- Dynamic tips overlay.
- HTTP fallback through a local streaming proxy.
- Persistent local video cache with remote update validation.

This project is not affiliated with Mr.Puzzle or PuzzleVision.

## Branch status

This branch is intended for:

```text
Minecraft Forge 1.20.1
WaterMedia 3.0.0.21
watermedia_binaries 3.0.0-rc.4
```

It was created separately from the older WaterMedia compatibility branches so the working WaterMedia `3.0.0.16` and `3.0.0.17` variants stay untouched.

Expected current cinematic build tag in logs:

```text
[cinematic-ui-watermedia-021-streaming-proxy-safe]
```

Expected current proxy build tag in logs:

```text
[streaming-proxy-003]
```

## What changed in this branch

### WaterMedia 3.0.0.21 API adaptation

WaterMedia changed a lot after `3.0.0.17`, especially around MRL state handling, player lifecycle, audio/video startup, native release safety, and no-frame decoder failures.

This branch adapts WaterVision to the newer API:

- Uses `MediaAPI.getMRL(URI)`.
- Uses `MRL.status()` instead of the old `ready()` / `hasError()` flow.
- Creates players through `MediaAPI.createPlayer(MRL, Supplier<GFXEngine>, Supplier<SFXEngine>)`.
- Handles `MRL.Status.FETCHING`, `LOADED`, `ERROR`, `BLOCKED`, `EXPIRED`, and `FORGOTTEN` properly.
- Keeps one guarded retry only for the old edge case where a player reaches `ENDED`/`STOPPED` before rendering the first texture.
- Removes the private `ensureDecodeThreads()` recovery workaround used for WaterMedia `3.0.0.17` testing.
- Keeps a `20 tick` delayed resume for safer startup.

In successful logs, you should see:

```text
WaterVision delayed resume armed [cinematic-ui-watermedia-021-streaming-proxy-safe]
WaterVision player resume requested [cinematic-ui-watermedia-021-streaming-proxy-safe] after 20 ticks
WaterVision first frame ready [cinematic-ui-watermedia-021-streaming-proxy-safe]
WaterVision first video texture rendered [cinematic-ui-watermedia-021-streaming-proxy-safe]
```

### HTTP fallback, local proxy, and persistent cache

Some direct `https://...mp4` URLs can fail in WaterMedia 3.0.0.21 / FFmpeg with `HTTP 400 Bad Request`, even when the same file exists and normal browser/curl requests work.

WaterVision now uses a safer fallback chain:

```text
1. Try the original remote URL normally.
2. If WaterMedia fails before the first texture, validate the local cache.
3. If the cached file is valid, play the local file directly.
4. If the cache is missing or stale, start a local streaming proxy on 127.0.0.1.
5. Play the video through the local proxy immediately.
6. Download the full file into cache in the background.
7. If the proxy fails before playback starts, keep the screen open and wait for the full cache download.
8. When the cache download is complete, play the local file.
```

The local cache is stored in:

```text
<minecraft folder>/watervision-cache/
```

Cached videos are persistent. They are not deleted when Minecraft closes.

To detect updated videos without changing the URL, WaterVision stores and validates metadata:

- `ETag`
- `Last-Modified`
- `Content-Length`

If the server file changes but the URL stays the same, WaterVision detects the stale cache, deletes it, and loads the newer version.

Downloads are also checked against `Content-Length` when available. Incomplete downloads are rejected and are not promoted to valid cache files.

Useful fallback log entries include:

```text
WaterVision remote playback failed before first texture
WaterVision cache hit validated
WaterVision cache stale
WaterVision streaming proxy started
WaterVision streaming proxy route created
WaterVision streaming proxy fallback ready
WaterVision downloading video to cache
WaterVision cache download completed
WaterVision background cache download completed
WaterVision streaming proxy failed, waiting for full cache download fallback
```

The proxy can log `Connection reset by peer` or `Une connexion établie a été abandonnée...` when WaterMedia closes a range request early, seeks, skips, or stops playback. This is usually harmless as long as the video continues playing and `first frame ready` appears.

### Cinematic volume control

Players can adjust the video volume during a cinematic:

- `Arrow Up`: increase cinematic volume.
- `Arrow Down`: decrease cinematic volume.
- A small volume overlay appears briefly when the value changes.
- The player volume is saved client-side and reused for later cinematics.
- The command volume remains the server-side base volume.

Effective volume is calculated like this:

```text
command_volume * client_cinematic_volume / 100
```

Example: if the command sends volume `80` and the player volume is `50%`, the effective video volume is `40`.

### Optional playback controls

The `allow_controls` command argument is used.

When `allow_controls = true`, players can control the cinematic:

- `Space`: pause/resume the video.
- `Arrow Right`: seek forward by 5 seconds.
- `Arrow Left`: seek backward by 5 seconds.

This branch uses the current WaterMedia control methods directly:

- `paused()`
- `pause()`
- `resume()`
- `togglePlay()` fallback
- `canSeek()`
- `forward()`
- `rewind()`
- `skipTime(long)` fallback

Visual overlays:

- `Pause` is shown near the bottom center while the video is paused.
- `+5s` appears on the right side when seeking forward.
- `-5s` appears on the left side when seeking backward.

A **5-tick seek cooldown** is applied to avoid sending too many seek requests to WaterMedia too quickly.

When `allow_controls = false`, pause and seek inputs are ignored and the related tips are hidden.

### Safer skip behavior

The `allow_exit` command argument controls whether the player can skip the video.

When `allow_exit = true`:

- The player must hold `Escape` to skip.
- A skip progress overlay appears in the bottom-right corner.
- The skip triggers after about 2 seconds.

When `allow_exit = false`:

- `Escape` does not skip the cinematic.
- The skip tip is not shown.

This avoids accidental skips from a single `Escape` press.

### Cinematic tips overlay

A small tips panel is displayed when the cinematic starts:

```text
Masquer : touche K
Régler le volume : ↑ / ↓
Pause : Espace
Avancer / reculer : ← / →
Passer : maintenir Échap
```

The panel is dynamic:

- It auto-hides after 10 seconds.
- `K` toggles the panel on/off.
- Pause/seek tips are shown only when `allow_controls = true`.
- Skip tips are shown only when `allow_exit = true`.

## Commands

### `/video`

```text
/video <"url"> <targets> [volume] [speed] [stretch_video] [game_fade_duration] [video_fade_duration] [allow_controls] [allow_exit]
```

Plays a fullscreen video for the selected target players.

Default values:

```text
volume: 100
speed: 1.0
stretch_video: false
game_fade_duration: 20.0
video_fade_duration: 20.0
allow_controls: true
allow_exit: true
```

Example:

```text
/video "https://example.com/video.mp4" @a 100 1.0 false 20.0 20.0 true true
```

Arguments:

- `url`: direct URL of the video to play. It should be quoted.
- `targets`: player(s) who should receive the video.
- `volume`: base server-side volume.
- `speed`: playback speed.
- `stretch_video`: stretches the video to the full screen if `true`; keeps aspect ratio if `false`.
- `game_fade_duration`: fade duration from game to video screen, in ticks.
- `video_fade_duration`: fade duration from black screen to video, in ticks.
- `allow_controls`: enables/disables pause and seek controls.
- `allow_exit`: enables/disables skip by holding `Escape`.

Requires OP permission level 4 by default.

### `/videoclient`

```text
/videoclient <"url">
```

Plays a video only on the local client, without server interaction.

Example:

```text
/videoclient "https://example.com/video.mp4"
```

## Player controls summary

| Key | Behavior | Condition |
| --- | --- | --- |
| `K` | Show/hide tips | Always available during the video |
| `Arrow Up` | Increase cinematic volume | Always available during the video |
| `Arrow Down` | Decrease cinematic volume | Always available during the video |
| `Space` | Pause/resume | Only if `allow_controls = true` |
| `Arrow Right` | Seek forward 5 seconds | Only if `allow_controls = true` and WaterMedia reports `canSeek()` |
| `Arrow Left` | Seek backward 5 seconds | Only if `allow_controls = true` and WaterMedia reports `canSeek()` |
| Hold `Escape` | Skip video | Only if `allow_exit = true` |

## Debugging

This branch logs the active build tag when a cinematic starts:

```text
[cinematic-ui-watermedia-021-streaming-proxy-safe]
```

Useful WaterVision log entries include:

- `screen opened`
- `player created`
- `delayed resume armed`
- `player resume requested`
- `first frame ready`
- `first video texture rendered`
- `seek control`
- `playback control`
- `skip hold completed`
- `player entered ERROR before first texture`
- `remote playback failed before first texture`
- `cache hit validated`
- `cache stale`
- `streaming proxy started`
- `streaming proxy fallback ready`
- `background cache download completed`

If a video fails to start, check whether the failure happens on the original remote URL, the proxy URL, or the cached local file.

## License

This mod is licensed under the PolyForm Strict License 1.0.0.
