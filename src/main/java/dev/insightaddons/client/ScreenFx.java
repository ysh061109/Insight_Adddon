package dev.insightaddons.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 화면 안 제작 연출: 멘 배낭의 제작/화로 업그레이드처럼 바라보는 작업대가 없는 경우,
 * 3D 연출은 GUI 뒤에 가려지므로 재료 아이콘이 칸에서 결과(또는 화면 가운데)로 날아가는 모습을 화면 위에 그린다.
 */
@EventBusSubscriber(modid = dev.insightaddons.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ScreenFx {
    private record Fly(ItemStack stack, float fx, float fy, float tx, float ty, long startMs, int delayMs) {}

    private static final List<Fly> FLIES = new ArrayList<>();
    private static final int DURATION_MS = 550;

    private ScreenFx() {}

    /** 이 화면에서 화면 안 연출을 쓸지 (배낭 같은 휴대용 메뉴) */
    public static boolean wanted(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?> acs
                && acs.getMenu().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("sophisticated");
    }

    public static void spawn(Minecraft mc, List<Item> items) {
        if (!InsightConfig.CRAFT_FX.get() || !(mc.screen instanceof AbstractContainerScreen<?> acs)) {
            return;
        }
        AbstractContainerMenu menu = acs.getMenu();
        int gl = acs.getGuiLeft();
        int gt = acs.getGuiTop();
        float tx = acs.width / 2f;
        float ty = acs.height / 2f;
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot) {
                tx = gl + slot.x + 8;
                ty = gt + slot.y + 8;
                break;
            }
        }
        long now = System.currentTimeMillis();
        int i = 0;
        for (Item item : items) {
            Slot from = null;
            for (Slot slot : menu.slots) {
                if (!(slot instanceof ResultSlot) && slot.hasItem() && slot.getItem().is(item)) {
                    from = slot;
                    break;
                }
            }
            float fx = from != null ? gl + from.x + 8 : gl + 10;
            float fy = from != null ? gt + from.y + 8 : gt + acs.height / 3f;
            FLIES.add(new Fly(new ItemStack(item), fx, fy, tx, ty, now, i * 60));
            i++;
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (FLIES.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        FLIES.removeIf(f -> now - f.startMs() > f.delayMs() + DURATION_MS + 50);
        GuiGraphics g = event.getGuiGraphics();
        for (Fly f : FLIES) {
            float t = (now - f.startMs() - f.delayMs()) / (float) DURATION_MS;
            if (t < 0f || t > 1f) {
                continue;
            }
            float e = t * t * (3f - 2f * t);
            float x = f.fx() + (f.tx() - f.fx()) * e;
            float y = f.fy() + (f.ty() - f.fy()) * e - (float) Math.sin(Math.PI * e) * 18f;
            float scale = 1.3f * (1f - 0.5f * e);
            g.pose().pushPose();
            g.pose().translate(x, y, 450);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(f.stack(), -8, -8);
            g.pose().popPose();
        }
    }

    public static void clear() {
        FLIES.clear();
    }
}
