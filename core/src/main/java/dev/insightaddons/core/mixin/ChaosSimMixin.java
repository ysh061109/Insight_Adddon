package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.InsightChaos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** InSight의 보관함 물리 틱을 가로채 "난장판" 동안 쌓인 3D 아이템을 날려 보낸다. */
@Mixin(targets = "com.mrsun.insightchests.core.physics.CoreChestSimulation", remap = false)
public abstract class ChaosSimMixin {
    @Inject(method = "tick(Z)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$chaos(boolean flag, CallbackInfo ci) {
        if (InsightChaos.tick(this)) {
            ci.cancel();
        }
    }
}
