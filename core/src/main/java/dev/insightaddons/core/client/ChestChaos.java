package dev.insightaddons.core.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 모든 보관함 화면의 "난장판" 버튼.
 * - InSight가 쌓아 둔 3D 물리 아이템이 있으면 그것들이 상자 안을 날아다니다 그 자리에서 떨어집니다.
 * - 동시에 화면의 슬롯 아이템(보관함 + 내 인벤토리)도 아이콘이 되어 화면을 튕겨 다니다가, 끝나면 물리적으로 날아와 원래 슬롯에 안착합니다.
 * - 날아다니는 동안 아이콘을 클릭하면 그 슬롯의 아이템을 집어 듭니다(들고 옮기고 놓기 가능).
 * 화면 위 연출이며 실제 아이템은 건드리지 않습니다(집어 드는 것만 진짜 클릭). 다시 누르면 바로 돌아가기 시작합니다.
 */
@EventBusSubscriber(modid = dev.insightaddons.core.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ChestChaos {
    private static final class P {
        Slot slot;
        ItemStack stack;
        float x, y, vx, vy, rot, spin, size, baseSize;
    }

    private static final List<P> PARTS = new ArrayList<>();
    private static final Set<Slot> HIDDEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Random RNG = new Random();
    private static final int MAX = 240;
    private static final long FLY_MS = 6000;
    private static final long RETURN_MAX_MS = 4000;
    private static long start;
    private static long lastMs;
    private static boolean returning;
    private static long returnStart;
    private static Screen owner;

    private ChestChaos() {}

    /** 슬롯의 진짜 아이콘을 숨겨야 하는가 (날아다니는 중이거나 돌아오는 중) */
    public static boolean hides(Slot slot) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(slot);
    }

    private static boolean eligible(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?> acs) || s.getClass().getName().startsWith("appeng.")) {
            return false;
        }
        if (s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            return false;
        }
        var inv = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        for (Slot slot : acs.getMenu().slots) {
            if (slot.container != inv) {
                return true;
            }
        }
        return false;
    }

    /** 오른쪽 바깥에 세로로 쌓는 조작 위젯 자리 (업그레이드 탭은 왼쪽이라 겹치지 않는다) */
    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) s;
        int w = 100;
        int right = acs.getGuiLeft() + acs.getXSize();
        int x = right + 6;
        int y = Math.max(2, acs.getGuiTop());
        boolean side = x + w <= s.width - 2;
        if (!side) {
            // 오른쪽에 자리가 없으면 창 위쪽 줄(오른쪽 끝 기준, 왼쪽으로)로 둔다
            x = Math.max(2, right - w);
            y = acs.getGuiTop() - 20 >= 2 ? acs.getGuiTop() - 20 : Math.min(s.height - 20, acs.getGuiTop() + acs.getYSize() + 3);
        }
        int step = 14;
        int cy = y;
        if (side) {
            e.addListener(WidgetGuard.mine(Button.builder(Component.translatable("insight_addons.chaos"), b -> toggle(acs))
                    .bounds(x, cy, w, 18).build()));
            cy += 20;
            e.addListener(WidgetGuard.mine(new ConfigSlider(x, cy, w, 12, 20, 300, InsightConfig.CHAOS_SPEED.get(),
                    v -> Component.translatable("insight_addons.chaos.speed", v), ConfigSlider.save(InsightConfig.CHAOS_SPEED))));
            cy += step;
            e.addListener(WidgetGuard.mine(new ConfigSlider(x, cy, w, 12, 0, 100, InsightConfig.CHAOS_RANDOM.get(),
                    v -> Component.translatable("insight_addons.chaos.random", v), ConfigSlider.save(InsightConfig.CHAOS_RANDOM))));
            cy += step + 4;
            HudToggle.addButton(e, x, cy, w);
        } else {
            e.addListener(WidgetGuard.mine(Button.builder(Component.translatable("insight_addons.chaos"), b -> toggle(acs))
                    .bounds(x, y, 48, 18).build()));
            HudToggle.addButton(e, x - 54, y, 50);
        }
    }

    private static void toggle(AbstractContainerScreen<?> acs) {
        if (InsightChaos.running()) {
            InsightChaos.stop();
        }
        if (!PARTS.isEmpty() && owner == acs) {
            // 진행 중이면 곧바로 돌아가기 시작
            if (!returning) {
                beginReturn();
            }
            return;
        }
        // InSight가 쌓아 둔 실제 3D 물리 아이템이 있으면 그것들도 날린다
        InsightChaos.start();
        clearAll();
        owner = acs;
        float f = InsightConfig.CHAOS_SPEED.get() / 100f;
        float r = InsightConfig.CHAOS_RANDOM.get() / 100f;
        int ox = WindowDrag.offsetOf(acs)[0];
        int oy = WindowDrag.offsetOf(acs)[1];
        for (Slot slot : acs.getMenu().slots) {
            if (!slot.hasItem()) {
                continue;
            }
            int pieces = Math.max(1, Math.min(8, (slot.getItem().getCount() + 7) / 8));
            boolean any = false;
            for (int k = 0; k < pieces && PARTS.size() < MAX; k++) {
                P p = new P();
                p.slot = slot;
                p.stack = slot.getItem().copyWithCount(1);
                p.x = acs.getGuiLeft() + slot.x + ox;
                p.y = acs.getGuiTop() + slot.y + oy;
                double ang = RNG.nextDouble() * Math.PI * 2;
                // 기본: 설정한 속도로 모든 아이템이 같은 속도. 랜덤 슬라이더만큼 아이템마다 속도가 달라진다
                float m = 1f + (RNG.nextFloat() * 2f - 1f) * r * 0.9f;
                float sp = 480f * f * m;
                p.vx = (float) (Math.cos(ang) * sp);
                p.vy = (float) (Math.sin(ang) * sp);
                p.spin = (RNG.nextFloat() - 0.5f) * 900f * f;
                p.baseSize = 0.8f + RNG.nextFloat() * 1.1f;
                p.size = 1f;
                PARTS.add(p);
                any = true;
            }
            if (any) {
                HIDDEN.add(slot);
            }
        }
        start = System.currentTimeMillis();
        lastMs = start;
        returning = false;
    }

    private static void clearAll() {
        PARTS.clear();
        HIDDEN.clear();
        returning = false;
    }

    private static void beginReturn() {
        returning = true;
        returnStart = System.currentTimeMillis();
    }

    /** 날아다니는 아이콘을 클릭하면 그 슬롯의 아이템을 집는다 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPress(ScreenEvent.MouseButtonPressed.Pre e) {
        if (PARTS.isEmpty() || e.getScreen() != owner || !(owner instanceof AbstractContainerScreen<?> acs)) {
            return;
        }
        if (e.getButton() != 0 && e.getButton() != 1) {
            return;
        }
        double mx = e.getMouseX();
        double my = e.getMouseY();
        for (int i = PARTS.size() - 1; i >= 0; i--) {
            P p = PARTS.get(i);
            float sz = 16 * p.size;
            if (mx >= p.x && mx < p.x + sz && my >= p.y && my < p.y + sz) {
                Slot slot = p.slot;
                var mc = Minecraft.getInstance();
                if (mc.gameMode != null && mc.player != null && slot.hasItem()) {
                    boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    mc.gameMode.handleInventoryMouseClick(acs.getMenu().containerId, slot.index, e.getButton(),
                            shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, mc.player);
                }
                final Slot taken = slot;
                PARTS.removeIf(q -> q.slot == taken);
                HIDDEN.remove(taken);
                e.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing e) {
        if (e.getScreen() == owner) {
            clearAll();
            InsightChaos.stop();
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (PARTS.isEmpty()) {
            HIDDEN.clear();
            return;
        }
        Screen s = e.getScreen();
        if (s != owner || !(s instanceof AbstractContainerScreen<?> acs)) {
            if (s != owner) {
                clearAll();
            }
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        if (!returning && now - start > FLY_MS) {
            beginReturn();
        }
        int ox = WindowDrag.offsetOf(acs)[0];
        int oy = WindowDrag.offsetOf(acs)[1];
        boolean timeout = returning && now - returnStart > RETURN_MAX_MS;
        for (int i = PARTS.size() - 1; i >= 0; i--) {
            P p = PARTS.get(i);
            float sz16 = 16 * p.size;
            if (!returning) {
                p.size = p.baseSize;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.spin * dt;
                if (p.x < 0 || p.x > s.width - sz16) {
                    p.vx = -p.vx;
                    p.x = Math.max(0, Math.min(s.width - sz16, p.x));
                }
                if (p.y < 0 || p.y > s.height - sz16) {
                    p.vy = -p.vy;
                    p.y = Math.max(0, Math.min(s.height - sz16, p.y));
                }
            } else {
                // 원래 슬롯으로 날아가 안착: 스프링으로 끌어당기고 크기/회전도 원래대로 풀어 준다
                float hx = acs.getGuiLeft() + p.slot.x + ox;
                float hy = acs.getGuiTop() + p.slot.y + oy;
                float ax = (hx - p.x) * 90f - p.vx * 12f;
                float ay = (hy - p.y) * 90f - p.vy * 12f;
                p.vx += ax * dt;
                p.vy += ay * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.size += (1f - p.size) * Math.min(1f, 8f * dt);
                float nearest = Math.round(p.rot / 360f) * 360f;
                p.rot += (nearest - p.rot) * Math.min(1f, 8f * dt);
                p.spin *= 0.8f;
                float d2 = (hx - p.x) * (hx - p.x) + (hy - p.y) * (hy - p.y);
                float v2 = p.vx * p.vx + p.vy * p.vy;
                if (timeout || (d2 < 1.0f && v2 < 400f)) {
                    Slot done = p.slot;
                    PARTS.remove(i);
                    boolean more = false;
                    for (P q : PARTS) {
                        if (q.slot == done) {
                            more = true;
                            break;
                        }
                    }
                    if (!more) {
                        HIDDEN.remove(done);
                    }
                    continue;
                }
            }
            GuiGraphics g = e.getGuiGraphics();
            g.pose().pushPose();
            g.pose().translate(p.x + 8 * p.size, p.y + 8 * p.size, 400);
            g.pose().mulPose(Axis.ZP.rotationDegrees(p.rot));
            g.pose().scale(p.size, p.size, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(p.stack, 0, 0);
            g.pose().popPose();
        }
        if (PARTS.isEmpty()) {
            HIDDEN.clear();
            returning = false;
        }
    }
}
