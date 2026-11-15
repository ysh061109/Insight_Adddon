package dev.insightaddons.extras.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.extras.client.AeTerminal;
import dev.insightaddons.extras.client.InsightConfig;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 단말기 창의 배경 그림을 반투명하게 그려서 뒤의 서버룸이 비쳐 보이게 합니다.
 * 슬롯 속 아이템과 글씨는 renderBg 바깥에서 그려지므로 불투명 그대로입니다.
 */
@Mixin(targets = "appeng.client.gui.AEBaseScreen")
public abstract class AeGuiAlphaMixin {

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("HEAD"), remap = false, require = 0)
    private void insightaddons$alphaOn(GuiGraphics g, float partial, int mx, int my, CallbackInfo ci) {
        int pct = InsightConfig.AE2_GUI_ALPHA.get();
        if (pct < 100 && InsightConfig.AE2_PHYSICS.get() && AeTerminal.isTerminalScreen(this)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, pct / 100f);
        }
    }

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("RETURN"), remap = false, require = 0)
    private void insightaddons$alphaOff(GuiGraphics g, float partial, int mx, int my, CallbackInfo ci) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
