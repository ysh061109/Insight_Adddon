package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.GlassSkin;
import dev.insightaddons.core.client.InsightBridge;
import dev.insightaddons.core.client.InsightConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism 개인 상자/개인 통 화면에 InSight Chests 스타일의 유리 배경을 입히고, 화면을 InSight 위치로 옮깁니다.
 * 다른 Mekanism 기계 화면은 건드리지 않습니다. 보안 탭 같은 Mekanism 기능은 그대로입니다.
 * 주입 지점을 못 찾으면 require=0 때문에 조용히 건너뛰고 원래 화면이 그대로 나옵니다.
 */
@Mixin(targets = "mekanism.client.gui.GuiMekanism", remap = false)
public abstract class MekanismScreenSkinMixin {

    private boolean insightaddons$personal() {
        return GlassSkin.enabled() && this.getClass().getName().startsWith("mekanism.client.gui.GuiPersonalStorage");
    }

    /** 요소(슬롯, 탭)를 만들기 전에 화면 위치를 옮긴다. 요소 좌표는 이때의 화면 위치를 기준으로 정해진다. */
    @Inject(method = "init",
            at = @At(value = "INVOKE", target = "Lmekanism/client/gui/GuiMekanism;addGuiElements()V"),
            remap = false, require = 0)
    private void insightaddons$reposition(CallbackInfo ci) {
        if (!insightaddons$personal() || !InsightConfig.FOLLOW_INSIGHT_POSITION.get()) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) (Object) this;
        int w = container.getXSize();
        int h = container.getYSize();
        // 오른쪽에는 보안 탭 등이 붙으므로 여유를 둔다
        switch (InsightBridge.guiPosition()) {
            case "LEFT" -> access.insightaddons$setLeftPos(6);
            case "RIGHT" -> access.insightaddons$setLeftPos(Math.max(6, screen.width - w - 36));
            default -> {
                return;
            }
        }
        access.insightaddons$setTopPos(Math.max(4, (screen.height - h) / 2));
    }

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$glassPanel(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!insightaddons$personal()) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        GlassSkin.panel(g, screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize());
        ci.cancel();
    }
}
