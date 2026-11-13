package dev.insightaddons.extras;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.concurrent.atomic.AtomicBoolean;

/** 월드에 처음 들어갈 때 믹신 적용 요약을 한 번 더 남깁니다. 클라이언트 전용. */
final class DiagHooks {
    private static final AtomicBoolean DONE = new AtomicBoolean();

    private DiagHooks() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            if (DONE.compareAndSet(false, true)) {
                MixinDiag.summary("월드 입장");
            }
        });
    }
}
