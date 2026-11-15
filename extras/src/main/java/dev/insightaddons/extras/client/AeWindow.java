package dev.insightaddons.extras.client;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * AE2 단말기 창 꾸미기: 창 끌어서 옮기기 + 창 아래의 "아이템 비율" 슬라이더.
 * 화면을 직접 그리며(이동한 좌표로) 마우스 입력도 이동한 만큼 되돌려 전달하므로 슬롯과 버튼이 그대로 작동합니다.
 * 슬라이더는 창과 함께 움직입니다. 비율은 서버룸에 쌓이는 아이템 조각 수에 곱해집니다.
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class AeWindow {
    private static final Map<String, int[]> OFFSETS = new HashMap<>();
    private static long lastPressMs;
    private static double lastPressX;
    private static double lastPressY;
    private static boolean dragging;
    private static long lastClick;
    private static Slider slider;
    private static Screen sliderScreen;
    private static int sliderX;
    private static int sliderY;

    private static int scissorDx;
    private static int scissorDy;
    private static Button chaos;

    private AeWindow() {}

    public static int scissorDx() {
        return scissorDx;
    }

    public static int scissorDy() {
        return scissorDy;
    }

    private static final class Slider extends AbstractSliderButton {
        Slider(int x, int y, int w) {
            super(x, y, w, 18, Component.empty(), (InsightConfig.AE2_PHYSICS_RATIO.get() - 10) / 290.0);
            updateMessage();
        }

        private int current() {
            return 10 + (int) Math.round(value * 290);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("insight_addons.ae.ratio", current()));
        }

        @Override
        protected void applyValue() {
            InsightConfig.AE2_PHYSICS_RATIO.set(current());
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            InsightConfig.AE2_PHYSICS_RATIO.save();
            AeScene.resync();
        }
    }

    private static boolean eligible(Screen s) {
        return s instanceof AbstractContainerScreen<?> && AeTerminal.isTerminalScreen(s) && InsightConfig.AE2_PHYSICS.get();
    }

    private static int[] off(Screen s) {
        return OFFSETS.computeIfAbsent(s.getClass().getName(), k -> new int[2]);
    }

    private static void clamp(AbstractContainerScreen<?> s, int[] o) {
        if (!InsightConfig.AE2_WINDOW_DRAG.get()) {
            o[0] = 0;
            o[1] = 0;
            return;
        }
        o[0] = Math.max(20 - s.getXSize() - s.getGuiLeft(), Math.min(s.width - 20 - s.getGuiLeft(), o[0]));
        o[1] = Math.max(-s.getGuiTop(), Math.min(s.height - 18 - s.getGuiTop(), o[1]));
    }

    /** 슬라이더를 창 아래(자리가 없으면 창 위)에 놓는다. 창이 화면을 꽉 채우면 창 안쪽 아래 */
    private static Slider sliderFor(AbstractContainerScreen<?> s) {
        int w = 90;
        // 창 왼쪽 바깥(EMI 검색창이 있는 아래쪽을 피함). 자리가 없으면 창 위, 그것도 없으면 창 안쪽 위
        int x = s.getGuiLeft() - w - 4;
        int y = s.getGuiTop() + 4;
        if (x < 2) {
            x = s.getGuiLeft();
            y = s.getGuiTop() - 21;
            if (y < 2) {
                x = s.getGuiLeft() + 4;
                y = s.getGuiTop() + 2;
            }
        }
        if (slider == null || sliderScreen != s || sliderX != x || sliderY != y) {
            slider = new Slider(x, y, w);
            chaos = Button.builder(Component.translatable("insight_addons.ae.chaos"), b -> AeScene.chaos())
                    .bounds(x, y + 22, w, 18).build();
            sliderScreen = s;
            sliderX = x;
            sliderY = y;
        }
        return slider;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) s;
        int[] o = off(s);
        clamp(acs, o);
        Slider sl = sliderFor(acs);
        e.setCanceled(true);
        var g = e.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(o[0], o[1], 0);
        int mx = e.getMouseX() - o[0];
        int my = e.getMouseY() - o[1];
        scissorDx = o[0];
        scissorDy = o[1];
        try {
            s.renderWithTooltip(g, mx, my, e.getPartialTick());
        } finally {
            scissorDx = 0;
            scissorDy = 0;
        }
        sl.render(g, mx, my, e.getPartialTick());
        chaos.render(g, mx, my, e.getPartialTick());
        g.pose().popPose();
    }

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
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) s;
        int[] o = off(s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        Slider sl = sliderFor(acs);
        if (e.getButton() == 2) {
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
                o[0] = 0;
                o[1] = 0;
                dragging = false;
                lastPressMs = 0;
                e.setCanceled(true);
                return;
            }
        }
        if (e.getButton() == 0 && chaos.isMouseOver(ax, ay)) {
            chaos.mouseClicked(ax, ay, 0);
            e.setCanceled(true);
            return;
        }
        if (e.getButton() == 0 && sl.isMouseOver(ax, ay)) {
            sl.mouseClicked(ax, ay, 0);
            e.setCanceled(true);
            return;
        }
        if (InsightConfig.AE2_WINDOW_DRAG.get() && e.getButton() == 0 && canStartDrag(acs, o, e.getMouseX(), e.getMouseY(), ax, ay)) {
            long now = System.currentTimeMillis();
            if (now - lastClick < 350) {
                o[0] = 0;
                o[1] = 0;
                dragging = false;
            } else {
                dragging = true;
            }
            lastClick = now;
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            s.mouseClicked(ax, ay, e.getButton());
            e.setCanceled(true);
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
        Slider sl = sliderFor((AbstractContainerScreen<?>) s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (e.getMouseButton() == 0 && sl.isFocused()) {
            sl.mouseDragged(ax, ay, 0, e.getDragX(), e.getDragY());
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            s.mouseDragged(ax, ay, e.getMouseButton(), e.getDragX(), e.getDragY());
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
        int[] o = off(s);
        if (dragging && e.getButton() == 0) {
            dragging = false;
            e.setCanceled(true);
            return;
        }
        Slider sl = sliderFor((AbstractContainerScreen<?>) s);
        double ax = e.getMouseX() - o[0];
        double ay = e.getMouseY() - o[1];
        if (e.getButton() == 0 && sl.isFocused()) {
            sl.mouseReleased(ax, ay, 0);
            sl.setFocused(false);
            e.setCanceled(true);
            return;
        }
        if (o[0] != 0 || o[1] != 0) {
            s.mouseReleased(ax, ay, e.getButton());
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
        slider = null;
        chaos = null;
        sliderScreen = null;
    }
}
