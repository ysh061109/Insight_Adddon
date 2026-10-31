package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sophisticated의 설정 화면(SettingsScreen)은 저장소 화면과 다른 클래스라서,
 * 따로 처리하지 않으면 어두운 막이 그대로 남아 화면이 까맣게 보입니다. 그 막을 그리지 않습니다.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen", remap = false)
public abstract class SettingsScreenSkinMixin {

    @Inject(method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noGradient(GuiGraphics g, CallbackInfo ci) {
        if (GlassSkin.enabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$noBackground(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (GlassSkin.enabled()) {
            ci.cancel();
        }
    }
}
