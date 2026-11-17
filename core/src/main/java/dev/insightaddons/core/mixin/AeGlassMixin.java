package dev.insightaddons.core.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.core.client.AeGlass;
import dev.insightaddons.core.client.AeTerminal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ME 단말기의 배경(머리/행/아래 그림)을 그리지 않고 투명 유리 패널 + 슬롯 칸으로 대신 그립니다.
 * 버튼, 스크롤바 같은 위젯은 renderBg의 다른 부분에서 그려지므로 그대로 남습니다.
 */
@Mixin(targets = "appeng.client.gui.me.common.MEStorageScreen")
public abstract class AeGlassMixin {

    @Inject(method = "drawBG(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$glass(GuiGraphics g, int offsetX, int offsetY, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (AeGlass.on() && AeTerminal.isTerminalScreen(this)) {
            AeGlass.draw((AbstractContainerScreen<?>) (Object) this, g, offsetX, offsetY, mouseX, mouseY, partial);
            if (AeGlass.orig() <= 0.02f) {
                ci.cancel();
            } else {
                // 투명도를 올린 만큼 AE2 원래 창 그림을 겹쳐 그린다 (원래 그리기를 이어서 실행)
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1f, 1f, 1f, AeGlass.orig());
            }
        }
    }

    @Inject(method = "drawBG(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V", at = @At("RETURN"), remap = false, require = 0)
    private void insightaddons$glassEnd(GuiGraphics g, int offsetX, int offsetY, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (AeGlass.on() && AeTerminal.isTerminalScreen(this)) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}
