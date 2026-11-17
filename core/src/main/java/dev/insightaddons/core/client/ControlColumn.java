package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 보관함/단말기 화면의 조작 위젯(3D 표시 비율, 창 투명도, 난장판 버튼, 난장판 속도/랜덤, HUD 숨김)을
 * 서로 겹치지 않게 세로 한 열로 모아 창 옆 빈 곳에 놓는다.
 * - Sophisticated 화면은 왼쪽에 업그레이드 탭이 붙으므로 오른쪽, 그 밖에는 왼쪽(JEI/EMI 목록은 보통 오른쪽)을 먼저 쓴다.
 * - EMI 위쪽 즐겨찾기 줄과 아래쪽 버튼 줄을 피하려고 위아래 여백을 둔다. 열이 화면에 안 들어가면 간격을 줄인다.
 * 클릭은 WidgetGuard가 JEI/EMI보다 먼저 받는다.
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ControlColumn {
    private static final int W = 108;
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 36;

    private ControlColumn() {}

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) {
            return;
        }
        boolean ae = AeTerminal.isTerminalScreen(s) && InsightConfig.AE2_PHYSICS.get();
        boolean insight = ScreenEvents.insightPresent();
        boolean glass = insight && s.getClass().getName().equals("com.mrsun.insightchests.client.gui.GlassChestScreen");
        boolean soph = insight && GlassSkin.isStorageScreenClass(s) && GlassSkin.placed();
        boolean chaos = ae || ChestChaos.eligible(s);
        if (!ae && !glass && !soph && !chaos) {
            return;
        }
        if (InsightConfig.GLASS_ALPHA.get() >= 0 && (glass || soph)) {
            InsightBridge.setUiOpacity(InsightConfig.GLASS_ALPHA.get() / 255.0);
        }
        List<AbstractWidget> rows = new ArrayList<>();
        int h = 14;
        if (ae) {
            rows.add(AeWindow.ratioSlider(0, 0, W, h));
            rows.add(new ConfigSlider(0, 0, W, h, 0, 255, InsightConfig.AE2_GLASS_ALPHA.get(),
                    v -> Component.translatable("insight_addons.window.alpha", Math.round(v * 100f / 255f)),
                    ConfigSlider.save(InsightConfig.AE2_GLASS_ALPHA)));
        } else if (glass || soph) {
            rows.add(new DropRatioSlider(0, 0, W, h));
            var save = ConfigSlider.save(InsightConfig.GLASS_ALPHA);
            rows.add(new ConfigSlider(0, 0, W, h, 0, 255, GlassSkin.effectiveAlpha(),
                    v -> Component.translatable("insight_addons.window.alpha", Math.round(v * 100f / 255f)),
                    v -> {
                        save.accept(v);
                        InsightBridge.setUiOpacity(v / 255.0);
                    }));
        }
        if (chaos) {
            Runnable go = ae ? AeScene::chaos : () -> ChestChaos.toggle(acs);
            rows.add(Button.builder(Component.translatable("insight_addons.chaos"), b -> go.run()).bounds(0, 0, W, 18).build());
            rows.add(new ConfigSlider(0, 0, W, h, 20, 300, InsightConfig.CHAOS_SPEED.get(),
                    v -> Component.translatable("insight_addons.chaos.speed", v), ConfigSlider.save(InsightConfig.CHAOS_SPEED)));
            rows.add(new ConfigSlider(0, 0, W, h, 0, 100, InsightConfig.CHAOS_RANDOM.get(),
                    v -> Component.translatable("insight_addons.chaos.random", v), ConfigSlider.save(InsightConfig.CHAOS_RANDOM)));
        }
        if (ae || glass || soph) {
            rows.add(HudToggle.button(0, 0, W));
        }
        if (rows.isEmpty()) {
            return;
        }
        // 열 높이 계산 (간격 2, 안 들어가면 0까지 줄임)
        int sum = 0;
        for (AbstractWidget r : rows) {
            sum += r.getHeight();
        }
        int avail = s.height - TOP_MARGIN - BOTTOM_MARGIN;
        int gap = Math.max(0, Math.min(2, (avail - sum) / Math.max(1, rows.size() - 1)));
        int total = sum + gap * (rows.size() - 1);

        // 이미 화면에 있는 다른 버튼(다른 모드 포함)을 장애물로 모은다 (다른 모드가 늦게 붙여도 닿도록 LOWEST로 실행)
        List<int[]> obstacles = new ArrayList<>();
        for (var l : e.getListenersList()) {
            if (l instanceof AbstractWidget w && w.visible && w.getWidth() > 0 && !rows.contains(w)) {
                obstacles.add(new int[]{w.getX(), w.getY(), w.getX() + w.getWidth(), w.getY() + w.getHeight()});
            }
        }
        int left = acs.getGuiLeft();
        int right = left + acs.getXSize();
        int tabs = soph ? 32 : 0;
        int leftX = left - 6 - W - tabs;
        int rightX = right + 6;
        boolean leftOk = leftX >= 2;
        boolean rightOk = rightX + W <= s.width - 2;
        boolean preferRight = soph || GlassSkin.isStorageScreenClass(s);
        List<Integer> xs = new ArrayList<>();
        if (preferRight) {
            if (rightOk) xs.add(rightX);
            if (leftOk) xs.add(leftX);
        } else {
            if (leftOk) xs.add(leftX);
            if (rightOk) xs.add(rightX);
        }
        xs.add(Math.max(2, s.width - W - 2));
        xs.add(2);
        int bestX = xs.get(0);
        int bestY = Math.max(TOP_MARGIN, Math.min(acs.getGuiTop(), s.height - BOTTOM_MARGIN - total));
        boolean found = false;
        outer:
        for (int cx : xs) {
            for (int cy = Math.max(TOP_MARGIN, acs.getGuiTop()); cy + total <= s.height - BOTTOM_MARGIN; cy += 4) {
                if (!hits(obstacles, cx, cy, W, total)) {
                    bestX = cx;
                    bestY = cy;
                    found = true;
                    break outer;
                }
            }
            for (int cy = Math.max(TOP_MARGIN, acs.getGuiTop()) - 4; cy >= TOP_MARGIN; cy -= 4) {
                if (cy + total <= s.height - BOTTOM_MARGIN && !hits(obstacles, cx, cy, W, total)) {
                    bestX = cx;
                    bestY = cy;
                    found = true;
                    break outer;
                }
            }
        }
        int x = bestX;
        int y = found ? bestY : Math.max(TOP_MARGIN, Math.min(acs.getGuiTop(), s.height - BOTTOM_MARGIN - total));
        for (AbstractWidget r : rows) {
            r.setX(x);
            r.setY(y);
            y += r.getHeight() + gap;
            e.addListener(WidgetGuard.mine(r));
        }
    }

    private static boolean hits(List<int[]> obs, int x, int y, int w, int h) {
        for (int[] o : obs) {
            if (x < o[2] + 2 && x + w > o[0] - 2 && y < o[3] + 2 && y + h > o[1] - 2) {
                return true;
            }
        }
        return false;
    }
}
