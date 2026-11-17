package dev.insightaddons.core.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * AE2 단말기 창의 서버룸 연출 조작 위젯: 3D 아이템 표시 슬라이더(Plus의 보관함 슬라이더와 같은 3단계)와 난장판 버튼.
 * 일반 위젯으로 넣으므로 창을 끌어 옮기면(WindowDrag) 같이 따라갑니다.
 */
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
        Slider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), stepOf(InsightConfig.AE2_PHYSICS_RATIO.get()) / 2.0);
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

    /** 3D 아이템 표시 슬라이더 (ControlColumn이 배치한다) */
    static AbstractSliderButton ratioSlider(int x, int y, int w, int h) {
        return new Slider(x, y, w, h);
    }
}
