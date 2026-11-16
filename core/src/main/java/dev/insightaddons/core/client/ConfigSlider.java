package dev.insightaddons.core.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** 설정값(정수) 하나를 바로 바꾸는 범용 슬라이더. 값은 min~max 사이이고, 글씨는 label(값)으로 만든다. */
public final class ConfigSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final IntFunction<Component> label;
    private final IntConsumer apply;

    public ConfigSlider(int x, int y, int w, int h, int min, int max, int current, IntFunction<Component> label, IntConsumer apply) {
        super(x, y, w, h, Component.empty(), (Math.max(min, Math.min(max, current)) - min) / (double) (max - min));
        this.min = min;
        this.max = max;
        this.label = label;
        this.apply = apply;
        updateMessage();
    }

    private int val() {
        return min + (int) Math.round(value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(val()));
    }

    @Override
    protected void applyValue() {
        apply.accept(val());
    }

    public static IntConsumer save(ModConfigSpec.IntValue cfg) {
        return v -> {
            if (cfg.get() != v) {
                cfg.set(v);
                cfg.save();
            }
        };
    }
}
