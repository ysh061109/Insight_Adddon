package dev.insightaddons.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

final class AeFx {
    private AeFx() {}

    static void sound(SoundEvent event, float pitch, float volume) {
        if (volume <= 0.001f) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, Math.min(volume, 2.0f)));
    }
}
