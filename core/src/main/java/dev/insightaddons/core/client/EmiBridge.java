package dev.insightaddons.core.client;

import java.lang.reflect.Field;

/**
 * 설치된 배낭/보관함 화면이 열려 있는 동안 EMI 화면 요소(검색창 등)를 잠깐 끕니다.
 * EMI에 컴파일 의존성 없이 리플렉션으로 EmiConfig.enabled만 바꾸며, 화면을 닫으면 원래 값으로 되돌립니다.
 * (설정 파일에는 저장하지 않습니다. 필드를 못 찾으면 아무 일도 하지 않습니다.)
 */
public final class EmiBridge {
    private static Field field;
    private static Boolean saved;

    private EmiBridge() {}

    public static void hide() {
        if (saved != null) {
            return;
        }
        try {
            field = Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled");
            saved = field.getBoolean(null);
            field.setBoolean(null, false);
        } catch (Throwable t) {
            saved = null;
        }
    }

    public static void restore() {
        if (saved == null || field == null) {
            return;
        }
        try {
            field.setBoolean(null, saved);
        } catch (Throwable ignored) {
            // 무시
        }
        saved = null;
    }

    public static boolean hidden() {
        return saved != null;
    }
}
