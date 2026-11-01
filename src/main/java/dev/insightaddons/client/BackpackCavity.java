package dev.insightaddons.client;

import net.minecraft.resources.ResourceLocation;

/** 배낭용 내부 질감을 한 인스턴스로 캐시해서, 렌더 단계에서 "배낭 상자인지"를 객체 동일성으로 구분합니다. */
public final class BackpackCavity {
    private static String lastKey;
    private static ResourceLocation cached;

    private BackpackCavity() {}

    public static synchronized ResourceLocation get() {
        String key = InsightConfig.BACKPACK_CAVITY_TEXTURE.get();
        if (cached == null || !key.equals(lastKey)) {
            lastKey = key;
            ResourceLocation parsed = ResourceLocation.tryParse(key);
            cached = parsed != null ? parsed : ResourceLocation.withDefaultNamespace("textures/block/brown_terracotta.png");
        }
        return cached;
    }
}
