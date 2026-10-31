package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sophisticated Core의 GuiHelper가 그리는 슬롯 격자, 컨트롤 패널, 탭 배경을 유리로 바꿉니다.
 * 위치와 크기는 원래 값 그대로 쓰므로 버튼과 슬롯 배치는 달라지지 않습니다.
 * Sophisticated 저장소/배낭 화면이 열려 있을 때만 적용됩니다.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper", remap = false)
public abstract class GuiHelperSkinMixin {

    @Inject(method = "renderSlotsBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$slots(GuiGraphics g, int x, int y, int columns, int rows, CallbackInfo ci) {
        if (GlassSkin.inStorageScreen()) {
            GlassSkin.cells(g, x, y, columns, rows);
            ci.cancel();
        }
    }

    @Inject(method = "renderControlBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$control(GuiGraphics g, int x, int y, int w, int h, CallbackInfo ci) {
        if (GlassSkin.inStorageScreen()) {
            GlassSkin.control(g, x, y, w, h);
            ci.cancel();
        }
    }

    @Inject(method = "renderTabBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$tab(GuiGraphics g, int x, int y, int w, int h, CallbackInfo ci) {
        if (GlassSkin.inStorageScreen()) {
            GlassSkin.control(g, x, y, w, h);
            ci.cancel();
        }
    }
}
