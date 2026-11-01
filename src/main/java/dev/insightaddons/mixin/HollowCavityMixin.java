package dev.insightaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrsun.insightchests.physics.ChestBoundingBox;
import dev.insightaddons.client.BackpackCavity;
import dev.insightaddons.client.InsightConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 배낭을 열 때 InSight Chests가 그리는 "속 빈 내부(바닥/벽)"가 검게 나오는 문제를 피하기 위해,
 * 설정 backpackHideCavity가 켜져 있으면 배낭 상자의 내부 면 그리기를 건너뜁니다.
 * 이 경우 아이템은 투명한 유리 상자 안에 쌓이듯 월드 위에 보입니다. 일반 상자/통/셜커는 그대로입니다.
 */
@Mixin(targets = "com.mrsun.insightchests.client.render.HollowChestDepthRenderer", remap = false)
public abstract class HollowCavityMixin {
    @Inject(method = "renderHollowCavity(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mrsun/insightchests/physics/ChestBoundingBox;I)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$skipBackpack(PoseStack ps, MultiBufferSource buffers, ChestBoundingBox box, int light, CallbackInfo ci) {
        if (box != null && box.cavityTexture != null && box.cavityTexture == BackpackCavity.get()
                && InsightConfig.BACKPACK_HIDE_CAVITY.get()) {
            ci.cancel();
        }
    }
}
