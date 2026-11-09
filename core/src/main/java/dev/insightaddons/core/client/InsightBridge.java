package dev.insightaddons.core.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Field;

/**
 * InSight Chests의 클라이언트 설정(투명도, 화면 위치)을 리플렉션으로 읽습니다.
 * 컴파일 시점에 InSight Chests 클래스를 참조하지 않기 위해서이며, 실패하면 기본값을 돌려줍니다.
 */
public final class InsightBridge {
    private static boolean tried;
    private static Object client;
    private static Field fOpacity;
    private static Field fPosition;

    private InsightBridge() {}

    private static void init() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class<?> cfg = Class.forName("com.mrsun.insightchests.config.InsightConfig");
            client = cfg.getField("CLIENT").get(null);
            fOpacity = client.getClass().getField("uiBackgroundOpacity");
            fPosition = client.getClass().getField("guiPosition");
        } catch (Throwable t) {
            client = null;
        }
    }

    /** InSight Chests의 uiBackgroundOpacity (0~1), 기본 0.25 */
    public static double uiOpacity() {
        init();
        try {
            if (client != null) {
                Object v = ((ModConfigSpec.ConfigValue<?>) fOpacity.get(client)).get();
                if (v instanceof Number n) {
                    return n.doubleValue();
                }
            }
        } catch (Throwable ignored) {
            // 설정이 아직 로드되지 않은 경우 등
        }
        return 0.25;
    }

    /** "MIDDLE", "LEFT", "RIGHT" 중 하나. 기본 RIGHT */
    public static String guiPosition() {
        init();
        try {
            if (client != null) {
                Object v = ((ModConfigSpec.ConfigValue<?>) fPosition.get(client)).get();
                if (v instanceof Enum<?> e) {
                    return e.name();
                }
            }
        } catch (Throwable ignored) {
            // 기본값 사용
        }
        return "RIGHT";
    }
}
