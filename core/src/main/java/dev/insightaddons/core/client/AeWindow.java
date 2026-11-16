package dev.insightaddons.core.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * AE2 단말기 창의 서버룸 연출 조작 위젯: 3D 아이템 표시 슬라이더(Plus의 보관함 슬라이더와 같은 3단계)와 난장판 버튼.
 * 일반 위젯으로 넣으므로 창을 끌어 옮기면(WindowDrag) 같이 따라갑니다.
 */
@EventBusSubscriber(modid = dev.insightaddons.core.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class AeWindow {
    private static final int[] RATIOS = {100, 50, 20};
    private static final String[] KEYS = {
            "insight_addons.ratio.full", "insight_addons.ratio.half", "insight_addons.ratio.original"
    };

    private AeWindow() {}

    private static int stepOf(int ratio) {
        int best = 0;
        for (int i = 1; i < RATIOS.length; i++) {
            if (Math.abs(RATIOS[i] - ratio) < Math.abs(RATIOS[best] - ratio)) {
                best = i;
            }
        }
        return best;
    }

    private static final class Slider extends AbstractSliderButton {
        Slider(int x, int y, int w) {
            super(x, y, w, 12, Component.empty(), stepOf(InsightConfig.AE2_PHYSICS_RATIO.get()) / 2.0);
            updateMessage();
        }

        private int step() {
            return Math.max(0, Math.min(2, (int) Math.round(value * 2)));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("insight_addons.ratio", Component.translatable(KEYS[step()])));
        }

        @Override
        protected void applyValue() {
            int st = step();
            value = st / 2.0;
            if (InsightConfig.AE2_PHYSICS_RATIO.get() != RATIOS[st]) {
                InsightConfig.AE2_PHYSICS_RATIO.set(RATIOS[st]);
                InsightConfig.AE2_PHYSICS_RATIO.save();
                AeScene.resync();
            }
        }
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs) || !AeTerminal.isTerminalScreen(s)
                || !InsightConfig.AE2_PHYSICS.get()) {
            return;
        }
        int w = 126;
        int h = 12;
        // 창 위쪽(EMI 검색창은 아래 가운데). 자리가 없으면 아래, 그것도 없으면 오른쪽 옆
        int x = acs.getGuiLeft();
        int y = acs.getGuiTop() - h - 3;
        if (y < 2) {
            y = acs.getGuiTop() + acs.getYSize() + 3;
            if (y + h > s.height - 2) {
                x = Math.min(s.width - w - 2, acs.getGuiLeft() + acs.getXSize() + 4);
                y = Math.max(2, acs.getGuiTop());
            }
        }
        e.addListener(WidgetGuard.mine(new Slider(x, y, w)));
        e.addListener(WidgetGuard.mine(Button.builder(Component.translatable("insight_addons.chaos"), b -> AeScene.chaos())
                .bounds(x + w + 4, y - 3, 54, 18).build()));
        int nx = x + w + 4 + 54 + 4;
        e.addListener(WidgetGuard.mine(new ConfigSlider(nx, y, 90, h, 20, 300, InsightConfig.CHAOS_SPEED.get(),
                v -> Component.translatable("insight_addons.chaos.speed", v), ConfigSlider.save(InsightConfig.CHAOS_SPEED))));
        e.addListener(WidgetGuard.mine(new ConfigSlider(nx + 94 + 104, y, 90, h, 0, 100, InsightConfig.CHAOS_RANDOM.get(),
                v -> Component.translatable("insight_addons.chaos.random", v), ConfigSlider.save(InsightConfig.CHAOS_RANDOM))));
        HudToggle.addButton(e, nx + 94 + 104 + 94, y - 3, 60);
        e.addListener(WidgetGuard.mine(new ConfigSlider(nx + 94, y, 100, h, 0, 255, InsightConfig.AE2_GLASS_ALPHA.get(),
                v -> Component.translatable("insight_addons.window.alpha", Math.round(v * 100f / 255f)), ConfigSlider.save(InsightConfig.AE2_GLASS_ALPHA))));
    }
}
