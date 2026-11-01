package dev.insightaddons.client;

/** 지금 InSight 연출이 열려 있는 보관함의 종류(배낭/셜커/통/기타). 3D 아이템 크기 배율을 고르는 데 쓴다. */
public final class ContainerKind {
    private static volatile String current = "other";

    private ContainerKind() {}

    public static void set(String kind) {
        current = kind;
    }

    /** 전체 배율 x 종류별 배율 */
    public static float itemScale() {
        double scale = InsightConfig.ITEM_SCALE.get();
        switch (current) {
            case "backpack" -> scale *= InsightConfig.ITEM_SCALE_BACKPACK.get();
            case "shulker" -> scale *= InsightConfig.ITEM_SCALE_SHULKER.get();
            case "barrel" -> scale *= InsightConfig.ITEM_SCALE_BARREL.get();
            default -> {
            }
        }
        return (float) scale;
    }
}
