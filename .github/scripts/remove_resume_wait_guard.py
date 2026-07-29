from pathlib import Path

path = Path('src/main/java/me/srrapero720/watervision/client/screens/VisionScreen.java')
text = path.read_text(encoding='utf-8')
old = '''        if (this.videoPlayer.loading() || this.videoPlayer.buffering() || this.videoPlayer.waiting()) {
            this.resumeRetryDelayTicks = 5;
            return true;
        }

'''
count = text.count(old)
if count != 1:
    raise SystemExit(f'Expected one resume wait guard, found {count}')
path.write_text(text.replace(old, '', 1), encoding='utf-8')
