package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism 개인 상자/개인 통 화면의 슬롯 칸(GuiSlot)을 InSight 유리 칸으로 그립니다.
 * 슬롯 위의 아이템, 경고, 보안 같은 기능은 그대로 두고 칸 그림만 바꿉니다.
 */
@Mixin(targets = "mekanism.client.gui.element.slot.GuiSlot", remap = false)
public abstract class MekanismSlotSkinMixin {

    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$glassCell(GuiGraphics g, CallbackInfo ci) {
        if (!GlassSkin.enabled()) {
            return;
        }
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                || !screen.getClass().getName().startsWith("mekanism.client.gui.GuiPersonalStorage")) {
            return;
        }
        AbstractWidget widget = (AbstractWidget) (Object) this;
        // 이 시점의 좌표계는 화면 왼쪽 위(guiLeft, guiTop)가 원점이다
        GlassSkin.cell(g, widget.getX() - screen.getGuiLeft(), widget.getY() - screen.getGuiTop());
        ci.cancel();
    }
}
