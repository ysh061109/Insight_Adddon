package dev.insightaddons.core.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

/**
 * 다른 모드와의 호환 (전부 리플렉션이라 해당 모드가 없어도 안전):
 * - Mouse Tweaks: 창을 옮긴 상태에서는 우리가 마우스 이벤트를 먼저 가로채므로, 옮긴 만큼 되돌린 좌표로 Mouse Tweaks를 직접 호출해 준다.
 * - Quark: 정렬 단축키를 마우스 가운데 버튼에 지정했다면 창 위치 초기화(가운데 클릭)보다 Quark를 우선한다.
 */
public final class ModCompat {
    private static boolean mtTried;
    private static Method mtClick;
    private static Method mtRelease;
    private static Method mtDrag;
    private static Method mtScroll;
    private static Method mtButton;

    private ModCompat() {}

    private static void initMt() {
        if (mtTried) {
            return;
        }
        mtTried = true;
        try {
            Class<?> main = Class.forName("yalter.mousetweaks.Main");
            Class<?> btn = Class.forName("yalter.mousetweaks.MouseButton");
            mtButton = btn.getMethod("fromEventButton", int.class);
            for (Method m : main.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                switch (m.getName()) {
                    case "onMouseClicked" -> mtClick = m;
                    case "onMouseReleased" -> mtRelease = m;
                    case "onMouseDrag" -> mtDrag = m;
                    case "onMouseScrolled" -> mtScroll = m;
                    default -> {
                    }
                }
            }
        } catch (Throwable t) {
            mtClick = mtRelease = mtDrag = mtScroll = null;
        }
    }

    private static boolean call(Method m, Screen s, double x, double y, int button) {
        initMt();
        if (m == null || mtButton == null) {
            return false;
        }
        try {
            Object b = mtButton.invoke(null, button);
            if (b == null) {
                return false;
            }
            return Boolean.TRUE.equals(m.invoke(null, s, x, y, b));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 이동한 창 기준 좌표(x, y)로 Mouse Tweaks에 클릭을 알린다. 처리했으면 true. */
    public static boolean mtClicked(Screen s, double x, double y, int button) {
        initMt();
        return call(mtClick, s, x, y, button);
    }

    public static boolean mtReleased(Screen s, double x, double y, int button) {
        initMt();
        return call(mtRelease, s, x, y, button);
    }

    public static boolean mtDragged(Screen s, double x, double y, int button) {
        initMt();
        return call(mtDrag, s, x, y, button);
    }

    public static void mtScrolled(Screen s, double x, double y, double deltaY) {
        initMt();
        if (mtScroll == null) {
            return;
        }
        try {
            mtScroll.invoke(null, s, x, y, deltaY);
        } catch (Throwable ignored) {
        }
    }

    /** Quark의 정렬 등 단축키가 마우스 가운데 버튼에 지정돼 있는가 */
    public static boolean quarkUsesMiddle() {
        try {
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
                if (k.getName().startsWith("quark.keybind") && !k.isUnbound() && k.matchesMouse(2)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
