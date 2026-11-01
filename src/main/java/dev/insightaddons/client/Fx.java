package dev.insightaddons.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** 효과음/파티클 공통 설정(effects 섹션)을 적용하는 도우미 */
public final class Fx {
    private Fx() {}

    public static void sound(SoundEvent event, float pitch, float volume) {
        float v = volume * InsightConfig.SOUND_VOLUME.get().floatValue();
        if (v <= 0.001f) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, Math.min(v, 2.0f)));
    }

    public static boolean particles() {
        return InsightConfig.PARTICLES.get();
    }
}
