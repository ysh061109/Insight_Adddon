package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * InSight Chests의 화면은 월드를 어둡게/흐리게 만들지 않아서 3D 아이템이 그대로 보입니다.
 * Sophisticated 화면도 같은 방식으로 하기 위해, 그 화면들의 기본 배경(어두운 막)을 그리지 않습니다.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noDim(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (GlassSkin.isStorageScreen(this) || GlassSkin.isAeTerminal(this)) {
            ci.cancel();
        }
    }
}
