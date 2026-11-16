package dev.insightaddons.core.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 모든 보관함 화면의 "난장판" 버튼: InSight가 쌓은 3D 물리 아이템이 있으면 그것들이 상자 안을 날아다니다 그 자리에서 떨어지고, 없으면 아이콘이 화면에서 튕기다 떨어집니다.
 * 화면 위에 그리는 연출일 뿐, 실제 아이템은 건드리지 않습니다. 다시 누르면 바로 멈춥니다.
 */
@EventBusSubscriber(modid = dev.insightaddons.core.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ChestChaos {
    private static final class P {
        ItemStack stack;
        float x, y, hx, hy, vx, vy, rot, spin, size;
    }

    private static final List<P> PARTS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final int MAX = 240;
    private static final long FLY_MS = 6000;
    /** 날아다닌 뒤 떨어져서 바닥에 놓여 있는 시간 */
    private static final long FALL_MS = 3500;
    private static long start;
    private static long lastMs;
    private static Screen owner;

    private ChestChaos() {}

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

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!eligible(s)) {
            return;
        }
        AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) s;
        int w = 48;
        int h = 18;
        // 왼쪽은 배낭 업그레이드 탭 자리이고 아래 왼쪽은 3D 표시 슬라이더 자리라서, 오른쪽 끝에 맞춰 위 -> 아래 -> 오른쪽 옆 순서로 둔다
        int right = acs.getGuiLeft() + acs.getXSize();
        int x = right - w;
        int y = acs.getGuiTop() - h - 3;
        if (y < 2) {
            y = acs.getGuiTop() + acs.getYSize() + 3;
            if (y + h > s.height - 2) {
                x = Math.min(s.width - w - 2, right + 4);
                y = Math.max(2, acs.getGuiTop());
                if (x < right) {
                    x = right - w - 4;
                    y = Math.max(2, s.height - h - 2);
                }
            }
        }
        e.addListener(WidgetGuard.mine(Button.builder(Component.translatable("insight_addons.chaos"), b -> toggle(acs))
                .bounds(x, y, w, h).build()));
        // 난장판 속도 슬라이더: 버튼 왼쪽, 자리가 없으면 버튼 아래
        int sw = 90;
        int sx = x - sw - 4;
        int sy = y + 3;
        if (sx < 2) {
            sx = x - (sw - w);
            sy = y + h + 2;
            if (sx < 2) {
                sx = 2;
            }
        }
        e.addListener(WidgetGuard.mine(new ConfigSlider(sx, sy, sw, 12, 20, 300, InsightConfig.CHAOS_SPEED.get(),
                v -> Component.translatable("insight_addons.chaos.speed", v), ConfigSlider.save(InsightConfig.CHAOS_SPEED))));
    }

    private static void toggle(AbstractContainerScreen<?> acs) {
        if (InsightChaos.running()) {
            InsightChaos.stop();
            return;
        }
        if (!PARTS.isEmpty() && owner == acs) {
            PARTS.clear();
            return;
        }
        // InSight가 쌓아 둔 실제 3D 물리 아이템이 있으면 그걸 날린다 (AE 서버룸과 같은 연출)
        if (InsightChaos.start()) {
            return;
        }
        PARTS.clear();
        owner = acs;
        var inv = Minecraft.getInstance().player.getInventory();
        // AE 단말기처럼: 아이템 개수에 비례해서 여러 조각으로 흩어져 날뛴다 (스택당 최대 8조각, 전체 MAX)
        for (Slot slot : acs.getMenu().slots) {
            if (slot.container == inv || !slot.hasItem()) {
                continue;
            }
            int pieces = Math.max(1, Math.min(8, (slot.getItem().getCount() + 7) / 8));
            for (int k = 0; k < pieces && PARTS.size() < MAX; k++) {
                P p = new P();
                p.stack = slot.getItem().copyWithCount(1);
                p.hx = acs.getGuiLeft() + slot.x;
                p.hy = acs.getGuiTop() + slot.y;
                p.x = p.hx;
                p.y = p.hy;
                double ang = RNG.nextDouble() * Math.PI * 2;
                float sp = (200 + RNG.nextFloat() * 650) * (InsightConfig.CHAOS_SPEED.get() / 100f) * (0.35f + RNG.nextFloat() * 1.3f); // 픽셀/초
                p.vx = (float) (Math.cos(ang) * sp);
                p.vy = (float) (Math.sin(ang) * sp);
                p.spin = (RNG.nextFloat() - 0.5f) * 1100f;
                p.size = 0.8f + RNG.nextFloat() * 1.1f;
                PARTS.add(p);
            }
        }
        start = System.currentTimeMillis();
        lastMs = start;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (PARTS.isEmpty()) {
            return;
        }
        Screen s = e.getScreen();
        if (s != owner) {
            PARTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        long el = now - start;
        if (el > FLY_MS + FALL_MS) {
            PARTS.clear();
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        boolean fall = el > FLY_MS;
        for (P p : PARTS) {
            if (!fall) {
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.spin * dt;
                if (p.x < 0 || p.x > s.width - 16 * p.size) {
                    p.vx = -p.vx;
                    p.x = Math.max(0, Math.min(s.width - 16 * p.size, p.x));
                }
                if (p.y < 0 || p.y > s.height - 16 * p.size) {
                    p.vy = -p.vy;
                    p.y = Math.max(0, Math.min(s.height - 16 * p.size, p.y));
                }
            } else {
                // 제자리로 돌아가지 않고, 있던 자리에서 중력으로 떨어져 바닥에 쌓인다
                p.vy += 1800f * dt;
                p.vx *= 1f - 1.5f * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.spin * dt;
                p.spin *= 1f - 2f * dt;
                float floor = s.height - 16 * p.size;
                if (p.y > floor) {
                    p.y = floor;
                    p.vy = -p.vy * 0.35f;
                    p.vx *= 0.6f;
                    if (Math.abs(p.vy) < 40f) {
                        p.vy = 0f;
                    }
                }
                if (p.x < 0 || p.x > s.width - 16 * p.size) {
                    p.vx = -p.vx * 0.5f;
                    p.x = Math.max(0, Math.min(s.width - 16 * p.size, p.x));
                }
            }
            float dx = p.x;
            float dy = p.y;
            float rot = p.rot;
            g.pose().pushPose();
            float sz = p.size;
            g.pose().translate(dx + 8, dy + 8, 400);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            g.pose().scale(sz, sz, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(p.stack, 0, 0);
            g.pose().popPose();
        }
    }
}
