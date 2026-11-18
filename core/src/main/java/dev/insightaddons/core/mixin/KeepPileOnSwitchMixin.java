package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.GlassSkin;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 배낭 화면에서 배낭 설정 화면으로(또는 그 반대로) 넘어갈 때, InSight Chests가 새 화면의 메뉴로 3D 아이템 더미를 다시 만들어
 * (설정 화면에는 아이템 칸이 없어서) 쌓인 아이템이 사라지는 것을 막는다. 보관함 화면끼리 넘어갈 때는 기존 더미와 카메라를 그대로 둔다.
 */
@Mixin(targets = "com.mrsun.insightchests.client.gui.GlassmorphismChestScreenHandler", remap = false)
public abstract class KeepPileOnSwitchMixin {

    @Inject(method = "onScreenOpening", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$keepPile(ScreenEvent.Opening event, CallbackInfo ci) {
        if (GlassSkin.isStorageScreenClass(event.getCurrentScreen()) && GlassSkin.isStorageScreenClass(event.getNewScreen())) {
            ci.cancel();
        }
    }
}
