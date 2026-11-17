package dev.insightaddons.core.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** 보관함 안 3D 아이템 표시 비율을 3단계(실제 개수 1:1 / 절반 / 원래 방식)로 고르는 슬라이더. */
public final class DropRatioSlider extends AbstractSliderButton implements Wheel {
    private static final String[] KEYS = {
            "insight_addons.ratio.full", "insight_addons.ratio.half", "insight_addons.ratio.original"
    };

    public DropRatioSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), InsightConfig.DROP_RATIO.get() / 2.0);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int idx = (int) Math.round(value * 2);
        setMessage(Component.translatable("insight_addons.ratio", Component.translatable(KEYS[Math.max(0, Math.min(2, idx))])));
    }

    @Override
    protected void applyValue() {
        int idx = (int) Math.round(value * 2);
        value = idx / 2.0;
        if (InsightConfig.DROP_RATIO.get() != idx) {
            InsightConfig.DROP_RATIO.set(idx);
            InsightConfig.DROP_RATIO.save();
        }
    }

    @Override
    public boolean wheel(double dir, boolean shift) {
        int idx = Math.max(0, Math.min(2, (int) Math.round(value * 2) + (dir > 0 ? 1 : -1)));
        value = idx / 2.0;
        updateMessage();
        applyValue();
        return true;
    }
}
