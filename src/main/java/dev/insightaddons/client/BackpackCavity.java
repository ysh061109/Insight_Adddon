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
            // 예전 기본값(갈색 테라코타)이 설정 파일에 남아 있으면 InSight Chests 기본 질감(참나무 판자)으로 바꿔 쓴다
            if ("minecraft:textures/block/brown_terracotta.png".equals(key)) {
                key = "minecraft:textures/block/oak_planks.png";
            }
            ResourceLocation parsed = ResourceLocation.tryParse(key);
            cached = parsed != null ? parsed : ResourceLocation.withDefaultNamespace("textures/block/oak_planks.png");
        }
        return cached;
    }
}
