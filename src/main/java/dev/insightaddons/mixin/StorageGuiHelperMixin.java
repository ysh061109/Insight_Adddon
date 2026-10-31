package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 저장소/설정 화면의 메인 패널 이미지 그리기를 InSight 스타일 유리 패널로 바꿉니다. */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageGuiHelper", remap = false)
public abstract class StorageGuiHelperMixin {

    @Inject(method = "renderStorageBackground(Lnet/p3pp3rf1y/sophisticatedcore/client/gui/utils/Position;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;II)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$panel(Position pos, GuiGraphics g, ResourceLocation tex, int w, int h, CallbackInfo ci) {
        if (GlassSkin.inStorageScreen()) {
            GlassSkin.panel(g, pos.x(), pos.y(), w, h);
            ci.cancel();
        }
    }
}
