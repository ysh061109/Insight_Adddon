package dev.insightaddons.extras.client;

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
 * 모든 보관함 화면의 "난장판" 버튼: 누르면 보관함 안 아이템 아이콘이 화면 곳곳을 튕겨 다니다가 제자리로 돌아옵니다.
 * 화면 위에 그리는 연출일 뿐, 실제 아이템은 건드리지 않습니다. 다시 누르면 바로 멈춥니다.
 */
@EventBusSubscriber(modid = dev.insightaddons.extras.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ChestChaos {
    private static final class P {
        ItemStack stack;
        float x, y, hx, hy, vx, vy, rot, spin;
    }

    private static final List<P> PARTS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final int MAX = 140;
    private static final long FLY_MS = 6000;
    private static final long BACK_MS = 800;
    private static long start;
    private static long lastMs;
    private static Screen owner;

    private ChestChaos() {}

    private static boolean eligible(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?> acs) || AeTerminal.isTerminalScreen(s)) {
            return false;
        }
        if (s.getClass().getName().contains("Creative")) {
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
        int w = 54;
        int x = acs.getGuiLeft() - w - 4;
        int y = acs.getGuiTop() + 4;
        if (x < 2) {
            x = acs.getGuiLeft() + acs.getXSize() - w;
            y = acs.getGuiTop() - 21;
            if (y < 2) {
                x = acs.getGuiLeft() + 4;
                y = 2;
            }
        }
        e.addListener(Button.builder(Component.translatable("insight_addons.ae.chaos"), b -> toggle(acs))
                .bounds(x, y, w, 18).build());
    }

    private static void toggle(AbstractContainerScreen<?> acs) {
        if (!PARTS.isEmpty() && owner == acs) {
            PARTS.clear();
            return;
        }
        PARTS.clear();
        owner = acs;
        var inv = Minecraft.getInstance().player.getInventory();
        for (Slot slot : acs.getMenu().slots) {
            if (slot.container == inv || !slot.hasItem() || PARTS.size() >= MAX) {
                continue;
            }
            P p = new P();
            p.stack = slot.getItem().copyWithCount(1);
            p.hx = acs.getGuiLeft() + slot.x;
            p.hy = acs.getGuiTop() + slot.y;
            p.x = p.hx;
            p.y = p.hy;
            double ang = RNG.nextDouble() * Math.PI * 2;
            float sp = 180 + RNG.nextFloat() * 520; // 픽셀/초
            p.vx = (float) (Math.cos(ang) * sp);
            p.vy = (float) (Math.sin(ang) * sp);
            p.spin = (RNG.nextFloat() - 0.5f) * 900f;
            PARTS.add(p);
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
        if (el > FLY_MS + BACK_MS) {
            PARTS.clear();
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        boolean back = el > FLY_MS;
        float bt = back ? (el - FLY_MS) / (float) BACK_MS : 0f;
        float be = bt * bt * (3 - 2 * bt);
        for (P p : PARTS) {
            if (!back) {
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.spin * dt;
                if (p.x < 0 || p.x > s.width - 16) {
                    p.vx = -p.vx;
                    p.x = Math.max(0, Math.min(s.width - 16, p.x));
                }
                if (p.y < 0 || p.y > s.height - 16) {
                    p.vy = -p.vy;
                    p.y = Math.max(0, Math.min(s.height - 16, p.y));
                }
            }
            float dx = back ? p.x + (p.hx - p.x) * be : p.x;
            float dy = back ? p.y + (p.hy - p.y) * be : p.y;
            float rot = back ? p.rot * (1 - be) : p.rot;
            g.pose().pushPose();
            g.pose().translate(dx + 8, dy + 8, 400);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            g.pose().translate(-8, -8, 0);
            g.renderItem(p.stack, 0, 0);
            g.pose().popPose();
        }
    }
}
