# WaterVision — WaterMedia V3 compatibility branch

WaterVision is a Minecraft mod used to play fullscreen cinematic videos in-game through commands.

This branch updates the mod to work with **WaterMedia V3** and adds several cinematic quality-of-life features for players, especially for servers that use videos as cutscenes.

This project is not affiliated with Mr.Puzzle or PuzzleVision.

## What changed in this branch

### WaterMedia V3 compatibility

This branch ports the video screen to the WaterMedia V3 API:

- Uses the WaterMedia V3 player creation flow.
- Creates compatible video and audio engines for fullscreen playback.
- Keeps support for Forge 1.20.1.
- Tested with WaterMedia `3.0.0.16` on Forge `1.20.1`.

### More reliable video startup

Some WaterMedia V3 players could start, read the video, but never render the first frame. In logs, this usually appeared as a player ending with `texture=0` before any frame was rendered.

This branch adds recovery logic to make cinematic startup more reliable:

- Detects when the first video texture is missing.
- Triggers a guarded WaterMedia decode-thread recovery after 10 ticks when needed.
- Retries player creation only if the player reaches a terminal state before rendering the first frame.
- Adds cache-busting retry URLs for recreated players.
- Adds detailed debug logs to make video startup issues easier to diagnose.

### Cinematic volume control

Players can now adjust the video volume during a cinematic:

- `Arrow Up`: increase cinematic volume.
- `Arrow Down`: decrease cinematic volume.
- The volume overlay appears briefly when the value changes.
- The player volume is saved client-side and reused for later cinematics.
- The command volume still acts as the base/max volume chosen by the server.

Effective volume is calculated like this:

```text
command_volume * client_cinematic_volume / 100
```

Example: if the command sends volume `80` and the player volume is `50%`, the effective video volume is `40`.

### Optional playback controls

The `allow_controls` argument is now used.

When `allow_controls = true`, players can control the cinematic:

- `Space`: pause/resume the video.
- `Arrow Right`: seek forward by 5 seconds.
- `Arrow Left`: seek backward by 5 seconds.

Visual overlays were added:

- `Pause` is shown near the bottom center while the video is paused.
- `+5s` appears on the right side when seeking forward.
- `-5s` appears on the left side when seeking backward.

A 5-tick seek cooldown is applied to avoid spamming WaterMedia with too many seek requests too quickly.

When `allow_controls = false`, pause and seek inputs are ignored and the related tips are hidden.

### Safer skip behavior

The `allow_exit` argument controls whether the player can skip the video.

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
| `Arrow Right` | Seek forward 5 seconds | Only if `allow_controls = true` |
| `Arrow Left` | Seek backward 5 seconds | Only if `allow_controls = true` |
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

This branch logs the active build tag when a cinematic starts. For the current control/cooldown implementation, the expected tag is:

```text
[cinematic-ui-controls-cooldown-debug]
```

Useful WaterVision log entries include:

- `screen opened`
- `player created`
- `WM_RECOVERY`
- `first frame ready`
- `first video texture rendered`
- `seek control`
- `playback control`
- `skip hold completed`

If a video fails to start, check whether the player reaches a terminal state before the first texture is rendered.

## License

This mod is licensed under the PolyForm Strict License 1.0.0.