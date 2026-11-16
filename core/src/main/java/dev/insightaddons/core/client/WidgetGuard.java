package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * JEI/EMI 화면 요소가 위에 겹쳐 있어도 우리 슬라이더/버튼과 AE2 단말기의 스크롤바(창 오르내리기 바)가 눌리도록,
 * 그 자리를 누르면 JEI/EMI보다 먼저 화면에 직접 전달하고 이벤트를 가져옵니다. (창을 옮긴 만큼은 되돌려서 전달)
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class WidgetGuard {
    private static final Set<Object> OURS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean active;
    private static Field scrollField;
    private static Method boundsM;
    private static boolean scrollTried;

    private WidgetGuard() {}

    /** 우리 위젯으로 등록하고 그대로 돌려준다 */
    public static <T extends GuiEventListener> T mine(T w) {
        OURS.add(w);
        return w;
    }

    private static boolean scrollbarHit(Screen s, double ax, double ay) {
        if (!(s instanceof AbstractContainerScreen<?> acs) || !AeTerminal.isTerminalScreen(s)) {
            return false;
        }
        try {
            if (!scrollTried) {
                scrollTried = true;
                for (Class<?> c = s.getClass(); c != null && scrollField == null; c = c.getSuperclass()) {
                    try {
                        scrollField = c.getDeclaredField("scrollbar");
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (scrollField != null) {
                    scrollField.setAccessible(true);
                    boundsM = scrollField.getType().getMethod("getBounds");
                }
            }
            if (scrollField == null || boundsM == null) {
                return false;
            }
            Object sb = scrollField.get(s);
            if (sb == null) {
                return false;
            }
            var r = (net.minecraft.client.renderer.Rect2i) boundsM.invoke(sb);
            int x = acs.getGuiLeft() + r.getX() - 2;
            int y = acs.getGuiTop() + r.getY();
            int w = Math.max(r.getWidth(), 12) + 4;
            return ax >= x && ax < x + w && ay >= y && ay < y + r.getHeight();
        } catch (Throwable t) {
            scrollField = null;
            return false;
        }
    }

    /** 스크롤바 위인지 (창 끌기가 그 자리에서 시작되지 않도록 WindowDrag도 쓴다) */
    public static boolean overScrollbar(Screen s, double ax, double ay) {
        return scrollbarHit(s, ax, ay);
    }

    private static boolean overOurs(Screen s, double ax, double ay) {
        for (var c : s.children()) {
            if (OURS.contains(c) && c.isMouseOver(ax, ay)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hit(Screen s, double ax, double ay) {
        return overOurs(s, ax, ay) || scrollbarHit(s, ax, ay);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre e) {
        Screen s = e.getScreen();
        if (!(s instanceof AbstractContainerScreen<?>)) {
            return;
        }
        int[] o = WindowDrag.offsetOf(s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (!hit(s, ax, ay)) {
            return;
        }
        active = true;
        s.mouseClicked(ax, ay, e.getButton());
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrag(ScreenEvent.MouseDragged.Pre e) {
        if (!active) {
            return;
        }
        Screen s = e.getScreen();
        int[] o = WindowDrag.offsetOf(s);
        s.mouseDragged(e.getMouseX() - o[0], e.getMouseY() - o[1], e.getMouseButton(), e.getDragX(), e.getDragY());
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre e) {
        if (!active) {
            return;
        }
        active = false;
        Screen s = e.getScreen();
        int[] o = WindowDrag.offsetOf(s);
        s.mouseReleased(e.getMouseX() - o[0], e.getMouseY() - o[1], e.getButton());
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScroll(ScreenEvent.MouseScrolled.Pre e) {
        Screen s = e.getScreen();
        if (!(s instanceof AbstractContainerScreen<?>)) {
            return;
        }
        int[] o = WindowDrag.offsetOf(s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (scrollbarHit(s, ax, ay)) {
            s.mouseScrolled(ax, ay, e.getScrollDeltaX(), e.getScrollDeltaY());
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing e) {
        active = false;
    }
}
