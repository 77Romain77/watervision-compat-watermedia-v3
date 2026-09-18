# RELEASE 1.1.1
- Keep native WaterMedia streaming first after checking the local cache.
- Remove background cache downloads competing with streaming.
- Cancel cache requests and proxy transfers when closing or changing playback method.
- Bound cache HTTP requests, clean incomplete files, and report slow loading after 15 seconds.
- Preserve upstream range support headers and add local HTTP regression tests.

# RELEASE 0.1.0-alpha
- ✨ Renamed `/video ...` command to `/playvideo ...` and `/videoclient ...` command to `/playvideoclient ...` 
- ✨ Added /playoverlay and /stopoverlay commands to play videos in the overlay (no fullscreen).
  - Resolution its limited to the game's current aspect ratio
- ✨ Added /stopvideo to stop active video (including videos played by the client)
- ✨ Added support for commandblocks (only for `/playvideo`, `/stopvideo`, `/playoverlay`, `/stopoverlay`)
- 🛠️ Reduced minimal required forge/fabric/neoforge version 

# RELEASE 0.0.1-alpha
- ✨ Added /video and /videoclient commands to play videos from URLs.
- 🎨 Implemented customizable loading animation feature.
- 🛠️ Initial release of WaterVision mod.