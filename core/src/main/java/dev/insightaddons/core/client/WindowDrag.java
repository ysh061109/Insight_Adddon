package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 보관함 창 끌어서 옮기기. 창의 빈 곳(슬롯/버튼 없는 곳, 제목 줄 포함)을 끌면 화면 전체가 같이 움직입니다.
 * 화면을 직접 그리며(이동 후 좌표로) 마우스 입력도 이동한 만큼 되돌려서 화면에 전달하므로,
 * 슬롯, 업그레이드 탭, 슬라이더 등 어떤 화면에도 같은 방식으로 작동합니다.
 * 창 위치는 화면 종류별로 게임을 켜 둔 동안 기억합니다. 제목 줄을 더블클릭하면 원래 자리로 돌아옵니다.
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class WindowDrag {
    private static final int TITLE_H = 18;
    private static final Map<String, int[]> OFFSETS = new HashMap<>();
    private static boolean dragging;
    private static long lastTitleClick;

    private WindowDrag() {}

    private static boolean eligible(Screen s) {
        return s instanceof AbstractContainerScreen<?>
                && !(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen)
                && InsightConfig.WINDOW_DRAG.get();
    }

    private static int[] off(Screen s) {
        return OFFSETS.computeIfAbsent(s.getClass().getName(), k -> new int[2]);
    }

    private static void clamp(AbstractContainerScreen<?> s, int[] o) {
        int w = s.getXSize();
        int left = s.getGuiLeft();
        int top = s.getGuiTop();
        o[0] = Math.max(20 - w - left, Math.min(s.width - 20 - left, o[0]));
        o[1] = Math.max(-top, Math.min(s.height - TITLE_H - top, o[1]));
    }

    private static boolean inTitle(AbstractContainerScreen<?> s, int[] o, double mx, double my) {
        int x = s.getGuiLeft() + o[0];
        int y = s.getGuiTop() + o[1];
        return mx >= x && mx < x + s.getXSize() && my >= y && my < y + TITLE_H;
    }

    /** 창 안(제목 줄 포함)에서 슬롯, 버튼, 들고 있는 아이템이 없는 빈 곳인지 */
    private static boolean canStartDrag(AbstractContainerScreen<?> s, int[] o, double mx, double my, double ax, double ay) {
        int x = s.getGuiLeft() + o[0];
        int y = s.getGuiTop() + o[1];
        boolean inside = mx >= x && mx < x + s.getXSize() && my >= y && my < y + s.getYSize();
        if (!inside || !s.getMenu().getCarried().isEmpty() || s.getSlotUnderMouse() != null) {
            return false;
        }
        for (var child : s.children()) {
            if (child.isMouseOver(ax, ay)) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        if (o[0] == 0 && o[1] == 0) {
            return;
        }
        clamp((AbstractContainerScreen<?>) s, o);
        // 원래 그리기를 막고, 이동한 좌표계로 직접 그린다 (마우스 위치도 되돌려서 전달)
        e.setCanceled(true);
        var pose = e.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate(o[0], o[1], 0);
        s.renderWithTooltip(e.getGuiGraphics(), e.getMouseX() - o[0], e.getMouseY() - o[1], e.getPartialTick());
        pose.popPose();
    }

    @SubscribeEvent
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (e.getButton() == 0 && canStartDrag((AbstractContainerScreen<?>) s, o, e.getMouseX(), e.getMouseY(), ax, ay)) {
            // 슬롯이나 버튼이 없는 빈 곳을 눌렀을 때만 끌기를 시작한다
            long now = System.currentTimeMillis();
            if (now - lastTitleClick < 350) {
                o[0] = 0;
                o[1] = 0;
                dragging = false;
            } else {
                dragging = true;
            }
            lastTitleClick = now;
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            e.setCanceled(s.mouseClicked(ax, ay, e.getButton()) || true);
        }
    }

    @SubscribeEvent
    public static void onDrag(ScreenEvent.MouseDragged.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        if (dragging && e.getMouseButton() == 0) {
            o[0] += (int) Math.round(e.getDragX());
            o[1] += (int) Math.round(e.getDragY());
            clamp((AbstractContainerScreen<?>) s, o);
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            s.mouseDragged(e.getMouseX() - o[0], e.getMouseY() - o[1], e.getMouseButton(), e.getDragX(), e.getDragY());
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            dragging = false;
            return;
        }
        if (dragging && e.getButton() == 0) {
            dragging = false;
            e.setCanceled(true);
            return;
        }
        int[] o = off(s);
        if (o[0] != 0 || o[1] != 0) {
            s.mouseReleased(e.getMouseX() - o[0], e.getMouseY() - o[1], e.getButton());
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        if (o[0] != 0 || o[1] != 0) {
            s.mouseScrolled(e.getMouseX() - o[0], e.getMouseY() - o[1], e.getScrollDeltaX(), e.getScrollDeltaY());
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing e) {
        dragging = false;
    }
}
