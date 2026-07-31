package me.srrapero720.watervision.compat.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import me.srrapero720.watervision.client.audio.CinematicAudioMute;

@ForgeVoicechatPlugin
public class WaterVisionVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "watervision";
    }

    @Override
    public void registerEvents(final EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onReceiveSound, 1000);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::onReceiveSound, 1000);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, this::onReceiveSound, 1000);
    }

    private void onReceiveSound(final ClientReceiveSoundEvent event) {
        if (CinematicAudioMute.isActive()) {
            event.setRawAudio(null);
        }
    }
}
