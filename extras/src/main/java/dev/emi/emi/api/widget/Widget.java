package dev.emi.emi.api.widget;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;

/** 컴파일용 stub (EMI 실제 클래스를 쓰며 jar에 포함되지 않음). */
public abstract class Widget implements Renderable {
    public abstract Bounds getBounds();

    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return List.of();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}
