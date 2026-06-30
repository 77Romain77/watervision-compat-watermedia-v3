# WaterVision — latest WaterMedia V3 compatibility branch

WaterVision is a Minecraft mod used to play fullscreen cinematic videos in-game through commands.

This branch updates the cinematic screen for the latest WaterMedia V3 line, currently targeting **WaterMedia 3.0.0.21**, while keeping the server cutscene quality-of-life features: client-side volume control, cinematic tips, hold-to-skip, pause/resume, and seek controls.

This project is not affiliated with Mr.Puzzle or PuzzleVision.

## Branch status

This branch is intended for:

```text
Minecraft Forge 1.20.1
WaterMedia 3.0.0.21+
```

It was created separately from the older WaterMedia compatibility branches so the working WaterMedia `3.0.0.16` and `3.0.0.17` variants stay untouched.

Expected debug tag in logs:

```text
[cinematic-ui-watermedia-021-debug]
```

## What changed in this branch

### Latest WaterMedia API adaptation

WaterMedia changed a lot after `3.0.0.17`, especially around MRL state handling, player lifecycle, audio/video startup, native release safety, and no-frame decoder failures.

This branch adapts WaterVision to the newer API:

- Uses `MediaAPI.getMRL(URI)`.
- Uses `MRL.status()` instead of the old `ready()` / `hasError()` flow.
- Creates players through `MediaAPI.createPlayer(MRL, Supplier<GFXEngine>, Supplier<SFXEngine>)`.
- Handles `MRL.Status.FETCHING`, `LOADED`, `ERROR`, `BLOCKED`, `EXPIRED`, and `FORGOTTEN` properly.
- Stops cleanly on `ERROR` or `BLOCKED` instead of retrying broken URLs.
- Keeps one guarded retry only for the old edge case where a player reaches `ENDED`/`STOPPED` before rendering the first texture.
- Removes the private `ensureDecodeThreads()` recovery workaround used for WaterMedia `3.0.0.17` testing.
- Keeps a temporary `20 tick` delayed resume for safe startup while testing the latest WaterMedia builds.

In successful logs, you should see:

```text
WaterVision delayed resume armed [cinematic-ui-watermedia-021-debug]
WaterVision player resume requested [cinematic-ui-watermedia-021-debug] after 20 ticks
WaterVision first frame ready [cinematic-ui-watermedia-021-debug]
WaterVision first video texture rendered [cinematic-ui-watermedia-021-debug]
```

### Why the old recovery was removed

The previous `3.0.0.17` branch used reflection to call private WaterMedia internals such as `ensureDecodeThreads()`.

That was useful during debugging, but it could race against WaterMedia's native FFmpeg threads and cause random crashes. WaterMedia `3.0.0.18+` includes several pipeline, pause/resume, release, and no-frame failure fixes, so this branch avoids touching internal threads.

### WaterMedia dependency note

This branch uses:

```gradle
implementation fg.deobf("curse.maven:watermedia-869524:latest.release")
```

If CurseMaven cannot resolve `latest.release` in GitHub Actions, replace it with the exact CurseForge file id for WaterMedia `3.0.0.21`.

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
Tips :
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

## Loading animation

The loading animation is provided by the WaterMedia API and can be customized by placing a file at:

```text
<minecraft folder>/config/watermedia/assets/watervision/loading.gif
```

If this file is not found, WaterMedia will use its default loading animation, which can also be customized here:

```text
<minecraft folder>/config/watermedia/assets/loading.gif
```

Note: customizing the default WaterMedia loading animation affects all mods using the WaterMedia API.

## Debugging

This branch logs the active build tag when a cinematic starts:

```text
[cinematic-ui-watermedia-021-debug]
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
- `MRL failed before player creation`

If a video fails to start, first check whether the failure is now an `MRL.Status.ERROR`, `BLOCKED`, or a player `ERROR`. WaterVision should no longer repeatedly retry HTTP/player errors.

## License

This mod is licensed under the PolyForm Strict License 1.0.0.
