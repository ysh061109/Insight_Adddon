package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 보관함 화면을 열었을 때 체력, 갑옷, 배고픔, 경험치, 핫바를 한 번에 숨기고 보이게 하는 버튼.
 * 숨김 상태는 설정(hudHidden)에 저장되어, 다음에 보관함을 열어도 유지됩니다. 보관함 화면이 열려 있는 동안만 숨깁니다.
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class HudToggle {
    private HudToggle() {}

    static Button button(int x, int y, int w) {
        Button[] self = new Button[1];
        self[0] = Button.builder(label(), b -> {
            InsightConfig.HUD_HIDDEN.set(!InsightConfig.HUD_HIDDEN.get());
            InsightConfig.HUD_HIDDEN.save();
            b.setMessage(label());
        }).bounds(x, y, w, 18).build();
        return self[0];
    }

    private static Component label() {
        return Component.translatable(InsightConfig.HUD_HIDDEN.get() ? "insight_addons.hud.show" : "insight_addons.hud.hide");
    }

    public static void addButton(ScreenEvent.Init.Post e, int x, int y, int w) {
        e.addListener(WidgetGuard.mine(button(x, y, w)));
    }

    private static boolean plusScreen(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        String n = s.getClass().getName();
        return n.equals("com.mrsun.insightchests.client.gui.GlassChestScreen")
                || GlassSkin.isStorageScreenClass(s) || AeTerminal.isTerminalScreen(s);
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (!InsightConfig.HUD_HIDDEN.get() || !plusScreen(Minecraft.getInstance().screen)) {
            return;
        }
        String path = e.getName().getPath().toLowerCase();
        if (path.contains("health") || path.contains("armor") || path.contains("food") || path.contains("hunger")
                || path.contains("experience") || path.contains("xp") || path.contains("hotbar")
                || path.contains("air_level") || path.contains("jump") || path.contains("vehicle")
                || path.contains("selected_item") || path.contains("stamina") || path.contains("saturation")) {
            e.setCanceled(true);
        }
    }
}
