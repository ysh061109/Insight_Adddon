package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.GlassSkin;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 화면이 바뀔 때는 새 화면 준비(Opening)가 먼저, 이전 화면 닫힘(Closing)이 나중에 옵니다.
 * 배낭 화면에서 설정 화면으로 넘어가면 InSight Chests가 "닫힘"으로 보고 카메라를 원래 시점으로 되돌리고
 * 내부 3D 아이템 연출도 지워 버립니다. 보관함 화면끼리 넘어가는 중에는 그 닫힘 처리를 건너뛰어
 * 상자 속 시점이 그대로 유지되게 합니다. 실제로 모든 화면이 닫힐 때는 평소처럼 처리됩니다.
 */
@Mixin(targets = "com.mrsun.insightchests.client.gui.GlassmorphismChestScreenHandler", remap = false)
public abstract class KeepCameraOnSwitchMixin {

    @Inject(method = "onScreenClosing", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$keepCamera(ScreenEvent.Closing event, CallbackInfo ci) {
        if (GlassSkin.switching()) {
            ci.cancel();
        }
    }
}
