# WaterVision
WaterVision, a cool mod that ```[REDACTED]```

Hoever, the mod is still in its early stages of development, until the version 3 of
watermedia got released.

At this moment, it only implements side-features such as a Video Player command, as a 
temporal replacement of the mod VideoPlayer

# USAGE
This mod adds 2 commands
- ```/video <"url"> <targets> [volume] [speed] [stretch_video] [game_fade_duration] [video_fade_duration] [allow_controls] [allow_exit]```
  - Plays a video from the given URL at the specified properties, by default, volume 100, speed 1.0, stretch_video false, game_fade_duration 20.0, video_fade_duration 20.0, allow_controls true, allow_exit true
  - Example: `/video https://streamable.com/jzqiek @a 100 1.0 false 20.0 20.0 true true
  - Requires OP permission level 4 (default)
  - Arguments:
    - url: The URL of the video to play. Must be a direct link to a video file (e.g., .mp4, .webm). MUST be quoted
    - targets: The player(s) to play the video for
    - volume: The volume of the video (0-120)
    - speed: The speed of the video (0.1-2.0)
    - stretch_video: Whether to stretch the video to fit the screen (true/false)
    - game_fade_duration: The duration of the fade-in/out effect for the game to the video screen (in ticks)
    - video_fade_duration: The duration of the fade-in/out effect for the darkness to the video (in ticks)
    - allow_controls: Whether to allow players to control the video playback (true/false) **[UNUSED]**
    - allow_exit: Whether to allow players to exit the video playback using **ESC** key (true/false)
- ```/videoclient <"url">```
  - Plays a video from the given URL on the client side only, without any server interaction
  - Example: `/videoclient https://streamable.com/jzqiek`
  - Requires no permission level and only had effects on your client
  - Arguments:
    - url: The URL of the video to play. Must be a direct link to a video file (e.g., .mp4, .webm). MUST be quoted
    - volume: The volume of the video (0-120)
    - speed: The speed of the video (0.1-2.0)
    - stretch_video: Whether to stretch the video to fit the screen (true/false)

# LOADING ANIMATION
The loading animation its provided by the WATERMeDIA API, and can be customized to place your own file by 
adding it in the following path:
`<minecraft folder>/mods/watermedia/assets/watervision/loading.gif`

If the file is not found, it will use the default one, which can be also customized to place your own file by
adding it in the following path:
`<minecraft folder>/mods/watermedia/assets/loading.gif`
NOTE: Customizing the default one will affect all the mods that use the WATERMeDIA API


# LICENSE
This mod is licensed under the PolyForm Strict License 1.0.0.