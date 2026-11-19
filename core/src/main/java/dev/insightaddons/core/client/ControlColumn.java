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
        // 화면이 다시 초기화되며 이 이벤트가 또 올 때, 앞서 붙인 우리 버튼이 겹쳐 남지 않게 먼저 치운다
        for (var l : new ArrayList<>(e.getListenersList())) {
            if (WidgetGuard.isMine(l)) {
                e.removeListener(l);
            }
        }
        List<AbstractWidget> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int h = 14;
        if (ae) {
            ids.add("ratio");
            rows.add(AeWindow.ratioSlider(0, 0, W, h));
            ids.add("alpha");
            rows.add(new ConfigSlider(0, 0, W, h, 0, 255, InsightConfig.AE2_GLASS_ALPHA.get(),
                    v -> Component.translatable("insight_addons.window.alpha", Math.round(v * 100f / 255f)),
                    ConfigSlider.save(InsightConfig.AE2_GLASS_ALPHA)));
        } else if (glass || soph) {
            ids.add("ratio");
            rows.add(new DropRatioSlider(0, 0, W, h));
            ids.add("alpha");
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
            ids.add("chaos");
            rows.add(Button.builder(Component.translatable("insight_addons.chaos"), b -> go.run()).bounds(0, 0, W, 18).build());
            ids.add("speed");
            rows.add(new ConfigSlider(0, 0, W, h, 20, 300, InsightConfig.CHAOS_SPEED.get(),
                    v -> Component.translatable("insight_addons.chaos.speed", v), ConfigSlider.save(InsightConfig.CHAOS_SPEED)));
            ids.add("random");
            rows.add(new ConfigSlider(0, 0, W, h, 0, 100, InsightConfig.CHAOS_RANDOM.get(),
                    v -> Component.translatable("insight_addons.chaos.random", v), ConfigSlider.save(InsightConfig.CHAOS_RANDOM)));
        }
        if (ae || glass || soph) {
            ids.add("hud");
            rows.add(HudToggle.button(0, 0, W));
        }
        // 버튼 배치 설정에서 숨긴 버튼은 빼고, 나머지는 위치를 옮긴 만큼 이동시킨다
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (!InsightConfig.layoutShow(ids.get(i))) {
                rows.remove(i);
                ids.remove(i);
            }
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
        // 보관함 창 자체도 장애물(버튼이 창 위에 얹히지 않도록)
        obstacles.add(new int[]{left, acs.getGuiTop(), right, acs.getGuiTop() + acs.getYSize()});
        int tabs = soph ? 32 : 0;
        int leftX = left - 6 - W - tabs;
        int rightX = right + 6;
        boolean leftOk = leftX >= 2;
        boolean rightOk = rightX + W <= s.width - 2;
        // AE2는 화면 왼쪽 아래 구석, 그 밖의 모든 보관함은 오른쪽 아래 구석(인벤토리 아래쪽)에 붙인다. 자리가 겹치면 위로 비킨다
        List<Integer> xs = new ArrayList<>();
        int cornerLeft = 2;
        int cornerRight = Math.max(2, s.width - W - 2);
        // 손에 든/멘 배낭과 플레이어 인벤토리만 왼쪽 아래. Mekanism 개인 상자·통 등은 일반 보관함처럼 인벤토리 아래 격자
        boolean worn = ChestChaos.iconMode(s) && !s.getClass().getName().startsWith("mekanism.");
        if (ae || worn) {
            xs.add(cornerLeft);
            xs.add(cornerRight);
        } else {
            xs.add(cornerRight);
            xs.add(cornerLeft);
        }
        // 그 밖의 Plus 보관함(InSight 유리 상자, 설치한 배낭, 개인 저장소 등): 창 오른쪽 아래, 플레이어 인벤토리 바로 아래에 2열 격자로 붙인다.
        // 창이 화면 밖으로 잘려 있거나 아래에 자리가 없으면 창을 화면 안으로(필요하면 위로) 옮겨서 항상 이 자리를 만든다.
        if (!ae && !worn && rows.size() >= 2) {
            int n = rows.size();
            int extraL = soph ? 32 : 0;
            int extraR = 28; // 오른쪽에 붙는 기능 버튼(배낭 설정, 보안 버튼 등) 자리
            int gapY = 6;
            int vgap = 4;
            // 1) 창 + 아래 버튼 격자를 한 덩어리로 보고 화면 오른쪽 가운데에 둔다. GUI 크기가 커서 안 들어가면 열을 늘리고 버튼을 낮춘다
            for (int cellH : new int[]{14, 12}) {
                for (int cols = 2; cols <= 4; cols++) {
                    int colW = cols == 2 ? 84 : (cols == 3 ? 76 : 62);
                    int rowsN = (n + cols - 1) / cols;
                    int gridW = colW * cols + 2 * (cols - 1);
                    int gridH = rowsN * cellH + (rowsN - 1) * vgap;
                    int blockH = acs.getYSize() + gapY + gridH;
                    if (blockH > s.height - 4 || gridW > s.width - 4) {
                        continue;
                    }
                    WindowDrag.place(acs, blockH, extraL, extraR);
                    int[] wo = WindowDrag.offsetOf(s);
                    int gx = acs.getGuiLeft() + acs.getXSize() - gridW;
                    if (gx + wo[0] < 2) {
                        gx = 2 - wo[0];
                    }
                    int gy = acs.getGuiTop() + acs.getYSize() + gapY;
                    // 창을 실제로 옮기지 못했거나 계산이 어긋나 격자가 화면 밖으로 나가면, 이 배치는 버리고 다음 배치를 시도한다
                    if (gy + wo[1] + gridH > s.height - 2 || gx + wo[0] < 0 || gx + wo[0] + gridW > s.width) {
                        continue;
                    }
                    for (int i = 0; i < n; i++) {
                        AbstractWidget r = rows.get(i);
                        r.setWidth(colW);
                        r.setHeight(cellH);
                        r.setX(gx + (i % cols) * (colW + 2));
                        r.setY(gy + (i / cols) * (cellH + vgap));
                        shift(r, ids.get(i), s, wo);
                        e.addListener(WidgetGuard.mine(r));
                    }
                    return;
                }
            }
            // 2) 창이 너무 커서 아래에 자리가 없으면, 창 왼쪽 옆에 버튼 한 줄(세로)로 두고 창은 오른쪽 가운데에
            int leftBlock = extraL + W + 6;
            if (acs.getXSize() + leftBlock + extraR + 4 <= s.width) {
                int sideH = 0;
                for (AbstractWidget r : rows) {
                    sideH += r.getHeight();
                }
                sideH += 2 * (rows.size() - 1);
                WindowDrag.place(acs, acs.getYSize(), leftBlock, extraR);
                int[] wo = WindowDrag.offsetOf(s);
                int x = acs.getGuiLeft() - leftBlock;
                int y = acs.getGuiTop() + Math.max(0, (acs.getYSize() - sideH) / 2);
                boolean sideOk = x + wo[0] >= 2 && y + wo[1] >= 0 && y + wo[1] + sideH <= s.height;
                for (int i = 0; sideOk && i < rows.size(); i++) {
                    AbstractWidget r = rows.get(i);
                    r.setWidth(W);
                    r.setX(x);
                    r.setY(y);
                    y += r.getHeight() + 2;
                    shift(r, ids.get(i), s, wo);
                    e.addListener(WidgetGuard.mine(r));
                }
                if (sideOk) {
                    return;
                }
            }
        }
        // 배낭(B키)·AE2 단말기 창도 양옆 탭/툴바까지 포함해 화면 밖으로 잘려 있으면 안으로 옮긴다
        if (ae) {
            WindowDrag.fitInside(acs, 0, 24);
        } else if (worn) {
            WindowDrag.fitInside(acs, 32, 28);
        }
        // 인벤토리/멘 배낭: 창 왼쪽 옆에 바짝 붙이고, 아이템이 가리지 않도록 창 아래쪽 높이에 맞춘다(자리가 없으면 오른쪽 옆)
        if (worn) {
            int wx = acs.getGuiLeft() - 4 - W;
            if (wx < 2) {
                wx = acs.getGuiLeft() + acs.getXSize() + 4;
            }
            int wy = Math.min(s.height - BOTTOM_MARGIN / 2 - total, acs.getGuiTop() + acs.getYSize() - total);
            wy = Math.max(TOP_MARGIN, wy);
            if (wx + W <= s.width - 2 && !hits(obstacles, wx, wy, W, total)) {
                int yy = wy;
                for (int i = 0; i < rows.size(); i++) {
                    AbstractWidget r = rows.get(i);
                    r.setX(wx);
                    r.setY(yy);
                    yy += r.getHeight() + gap;
                    shift(r, ids.get(i), s);
                    e.addListener(WidgetGuard.mine(r));
                }
                return;
            }
        }
        int bottomY = s.height - BOTTOM_MARGIN - total;
        int bestX = xs.get(0);
        int bestY = Math.max(TOP_MARGIN, bottomY);
        boolean found = false;
        outer:
        for (int cx : xs) {
            for (int cy = bottomY; cy >= TOP_MARGIN; cy -= 4) {
                if (!hits(obstacles, cx, cy, W, total)) {
                    bestX = cx;
                    bestY = cy;
                    found = true;
                    break outer;
                }
            }
        }
        int x = bestX;
        int y = bestY;
        int idx = 0;
        for (AbstractWidget r : rows) {
            r.setX(x);
            r.setY(y);
            y += r.getHeight() + gap;
            shift(r, ids.get(idx++), s);
            e.addListener(WidgetGuard.mine(r));
        }
    }

    /** 버튼 배치 설정의 이동량을 적용하고 화면 밖으로 나가지 않게 한다 */
    private static void shift(AbstractWidget r, String id, Screen s) {
        shift(r, id, s, new int[2]);
    }

    /** 창을 옮긴 만큼(wo) 위젯도 같이 옮겨져 그려지므로, 화면 안에 들어오는지는 그 이동량을 더해서 따진다(안 그러면 아래 줄이 위로 눌려 겹친다) */
    private static void shift(AbstractWidget r, String id, Screen s, int[] wo) {
        r.setX(Math.max(-wo[0], Math.min(s.width - r.getWidth() - wo[0], r.getX() + InsightConfig.layoutDx(id))));
        r.setY(Math.max(-wo[1], Math.min(s.height - r.getHeight() - wo[1], r.getY() + InsightConfig.layoutDy(id))));
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
