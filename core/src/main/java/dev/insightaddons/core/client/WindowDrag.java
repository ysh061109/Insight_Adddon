package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
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
    private static long lastPressMs;
    private static double lastPressX;
    private static double lastPressY;
    private static boolean dragging;
    private static long lastTitleClick;

    private static int scissorDx;
    private static int scissorDy;

    private WindowDrag() {}

    public static int scissorDx() {
        return scissorDx;
    }

    public static int scissorDy() {
        return scissorDy;
    }

    private static boolean eligible(Screen s) {
        // Insight Plus가 적용되는 화면(InSight 유리 상자, Sophisticated/Mekanism 개인 저장소, AE2 단말기)에서만 창을 끌 수 있다.
        // 일반 기계(Mekanism 기계, AE 기계, 화로 등)는 건드리지 않는다.
        return s instanceof AbstractContainerScreen<?>
                && !(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen)
                && InsightConfig.WINDOW_DRAG.get()
                && (ScreenEvents.isGlassChest(s) || GlassSkin.isStorageScreenClass(s) || AeTerminal.isTerminalScreen(s));
    }

    private static int[] off(Screen s) {
        return OFFSETS.computeIfAbsent(s.getClass().getName(), k -> new int[2]);
    }

    /** 화면의 창 이동량(없으면 0,0). 다른 클래스가 마우스 좌표를 되돌릴 때 쓴다. */
    public static int[] offsetOf(Screen s) {
        if (!eligible(s)) {
            return new int[2];
        }
        return off(s);
    }

    /**
     * 창(+양옆 탭, 아래 버튼 격자)이 화면 밖으로 나가 있으면 화면 안으로 옮긴다. 처음 열릴 때의 기본 위치가 잘려 있거나,
     * 버튼 둘 자리가 아래에 없을 때 쓴다. 사용자가 끌어 둔 위치는 필요한 만큼만 보정한다.
     */
    private static final java.util.Set<String> USER = new java.util.HashSet<>();

    /** 위·오른쪽 끝으로 붙이고(직접 옮기지 않았다면) 화면 안에 들어오게 보정 */
    public static void fit(AbstractContainerScreen<?> s, int extraLeft, int extraRight, int below) {
        fit(s, extraLeft, extraRight, below, true);
    }

    /**
     * 창 + 아래 버튼(합쳐서 blockH 높이)을 화면 오른쪽 가운데에 둔다(직접 옮기지 않았다면). 오른쪽 기능 버튼(extraRight)과
     * 왼쪽 탭/버튼 열(extraLeft)이 잘리지 않는 선까지 붙이고, 그래도 넘치면 화면 안으로 보정한다.
     */
    public static void place(AbstractContainerScreen<?> s, int blockH, int extraLeft, int extraRight) {
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        if (!USER.contains(s.getClass().getName())) {
            int top = Math.max(2, (s.height - blockH) / 2);
            o[1] = top - s.getGuiTop();
            o[0] = s.width - 2 - (s.getGuiLeft() + s.getXSize() + extraRight);
        }
        fit(s, extraLeft, extraRight, blockH - s.getYSize(), false);
    }

    /** 위치는 그대로 두고, 창(+양옆 탭)이 화면 밖으로 잘려 있을 때만 안으로 옮긴다 (배낭·AE2 단말기용) */
    public static void fitInside(AbstractContainerScreen<?> s, int extraLeft, int extraRight) {
        fit(s, extraLeft, extraRight, 0, false);
    }

    private static void fit(AbstractContainerScreen<?> s, int extraLeft, int extraRight, int below, boolean auto) {
        if (!eligible(s)) {
            return;
        }
        int[] o = off(s);
        // 사용자가 직접 옮기지 않았다면 열 때마다 창을 화면 위 끝·오른쪽 끝(오른쪽 탭 같은 기능 버튼이 잘리지 않는 선까지)으로 붙여서 아래에 버튼 자리를 넉넉히 남긴다
        if (auto && !USER.contains(s.getClass().getName())) {
            o[1] = 2 - s.getGuiTop();
            o[0] = Math.max(0, s.width - 2 - (s.getGuiLeft() + s.getXSize() + extraRight));
        }
        int left = s.getGuiLeft() + o[0] - extraLeft;
        int right = s.getGuiLeft() + o[0] + s.getXSize() + extraRight;
        int top = s.getGuiTop() + o[1];
        int bottom = top + s.getYSize() + below;
        int dx = 0;
        int dy = 0;
        if (right > s.width - 2) {
            dx = s.width - 2 - right;
        }
        if (left + dx < 2) {
            dx = 2 - left;
        }
        if (bottom > s.height - 2) {
            dy = s.height - 2 - bottom;
        }
        if (top + dy < 2) {
            dy = 2 - top;
        }
        o[0] += dx;
        o[1] += dy;
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
        if (WidgetGuard.overScrollbar(s, ax, ay)) {
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
        scissorDx = o[0];
        scissorDy = o[1];
        try {
            s.renderWithTooltip(e.getGuiGraphics(), e.getMouseX() - o[0], e.getMouseY() - o[1], e.getPartialTick());
        } finally {
            scissorDx = 0;
            scissorDy = 0;
        }
        pose.popPose();
    }

    // 다른 모드(Sophisticated 등)가 먼저 가운데 버튼을 가로채도 리셋이 되도록 가장 먼저, 취소된 이벤트도 받는다
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        if (e.isCanceled() && e.getButton() != 2) {
            return;
        }
        int[] o = off(s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (e.getButton() == 2 && ModCompat.quarkUsesMiddle()) {
            // Quark 정렬 단축키가 가운데 버튼이면 Quark에게 양보 (창 초기화는 더블클릭으로)
            return;
        }
        if (e.getButton() == 2) {
            // 마우스 가운데 버튼: 어디서든 창을 원래 자리로
            USER.remove(s.getClass().getName());
            o[0] = 0;
            o[1] = 0;
            dragging = false;
            e.setCanceled(true);
            return;
        }
        // 더블클릭: 슬롯이 아닌 곳을 연달아 두 번 누르면(그 아래에 보이지 않는 위젯이 있어도) 창을 원위치로
        {
            long nowMs = System.currentTimeMillis();
            AbstractContainerScreen<?> w = (AbstractContainerScreen<?>) s;
            int wx = w.getGuiLeft() + o[0];
            int wy = w.getGuiTop() + o[1];
            boolean in = e.getMouseX() >= wx && e.getMouseX() < wx + w.getXSize() && e.getMouseY() >= wy && e.getMouseY() < wy + w.getYSize();
            boolean dbl = e.getButton() == 0 && in && nowMs - lastPressMs < 400
                    && Math.abs(e.getMouseX() - lastPressX) < 8 && Math.abs(e.getMouseY() - lastPressY) < 8
                    && w.getSlotUnderMouse() == null && (o[0] != 0 || o[1] != 0);
            if (e.getButton() == 0) {
                lastPressMs = nowMs;
                lastPressX = e.getMouseX();
                lastPressY = e.getMouseY();
            }
            if (dbl) {
                USER.remove(s.getClass().getName());
                o[0] = 0;
                o[1] = 0;
                dragging = false;
                lastPressMs = 0;
                e.setCanceled(true);
                return;
            }
        }
        if (e.getButton() == 0 && canStartDrag((AbstractContainerScreen<?>) s, o, e.getMouseX(), e.getMouseY(), ax, ay)) {
            // 슬롯이나 버튼이 없는 빈 곳을 눌렀을 때만 끌기를 시작한다
            long now = System.currentTimeMillis();
            if (now - lastTitleClick < 350) {
                USER.remove(s.getClass().getName());
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
            // Mouse Tweaks는 창이 옮겨진 걸 모르므로 되돌린 좌표로 직접 알려 준다
            if (ModCompat.mtClicked(s, ax, ay, e.getButton())) {
                e.setCanceled(true);
                return;
            }
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
            USER.add(s.getClass().getName());
            o[0] += (int) Math.round(e.getDragX());
            o[1] += (int) Math.round(e.getDragY());
            clamp((AbstractContainerScreen<?>) s, o);
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            if (ModCompat.mtDragged(s, e.getMouseX() - o[0], e.getMouseY() - o[1], e.getMouseButton())) {
                e.setCanceled(true);
                return;
            }
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
            if (ModCompat.mtReleased(s, e.getMouseX() - o[0], e.getMouseY() - o[1], e.getButton())) {
                e.setCanceled(true);
                return;
            }
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
            double ax = e.getMouseX() - o[0];
            double ay = e.getMouseY() - o[1];
            if (!s.mouseScrolled(ax, ay, e.getScrollDeltaX(), e.getScrollDeltaY())) {
                ModCompat.mtScrolled(s, ax, ay, e.getScrollDeltaY());
            }
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing e) {
        dragging = false;
    }
}
