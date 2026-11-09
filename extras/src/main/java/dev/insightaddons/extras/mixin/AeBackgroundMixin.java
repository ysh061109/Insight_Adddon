package dev.insightaddons.extras.mixin;

import dev.insightaddons.extras.client.AeTerminal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 단말기를 열었을 때 3D 장면이 어둡게 가려지지 않도록 기본 배경(어두운 막/흐림)을 그리지 않습니다.
 */
@Mixin(Screen.class)
public abstract class AeBackgroundMixin {

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noDim(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (AeTerminal.isTerminalScreen(this)) {
            ci.cancel();
        }
    }

    // 1.21.1에서 월드를 어둡게/흐리게 하는 경로는 여러 갈래라서(renderBackground가 아니라 아래 메서드를 직접 부르는 화면/모드도 있음)
    // 같은 조건으로 모두 막아 둔다.
    @Inject(method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noDimTransparent(GuiGraphics g, CallbackInfo ci) {
        if (AeTerminal.isTerminalScreen(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noDimMenu(GuiGraphics g, CallbackInfo ci) {
        if (AeTerminal.isTerminalScreen(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBlurredBackground(F)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noBlur(float partialTick, CallbackInfo ci) {
        if (AeTerminal.isTerminalScreen(this)) {
            ci.cancel();
        }
    }
}
