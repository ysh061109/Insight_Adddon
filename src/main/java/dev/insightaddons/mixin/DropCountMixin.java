package dev.insightaddons.mixin;

import com.mrsun.insightchests.bridge.IItemBridge;
import dev.insightaddons.client.InsightConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InSight Chests는 스택 하나당 3D 아이템을 최대 5개까지만 떨어뜨립니다. (1개, 2~4개, 5~16개, ... 단계별 상한)
 * 설정 dropRatio에 따라 실제 개수(1:1) 또는 절반으로 바꿔 줍니다. 2이면 원래 방식 그대로입니다.
 * 한꺼번에 너무 많이 떨어지지 않도록 전체 상한(dropMaxTotal)은 InSight Chests의 "전체 제한" 기능을 그대로 이용합니다.
 */
@Mixin(targets = "com.mrsun.insightchests.core.physics.CoreChestSimulation", remap = false)
public abstract class DropCountMixin {

    @Inject(method = "calculateRepresentationCount(Lcom/mrsun/insightchests/bridge/IItemBridge;II)I",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$count(IItemBridge item, int nonBlockCap, int blockCap,
                                     CallbackInfoReturnable<Integer> cir) {
        int mode = InsightConfig.DROP_RATIO.get();
        if (mode >= 2 || item == null || item.isEmpty()) {
            return;
        }
        int count = item.getCount();
        int shown = mode == 0 ? count : (count + 1) / 2;
        cir.setReturnValue(Math.max(1, shown));
    }

    /** 전체 개수 상한(세 번째 int). 원래 방식이 아닐 때는 우리 설정값을 쓴다. */
    @ModifyVariable(method = "syncWithContainer(Lcom/mrsun/insightchests/bridge/IContainerBridge;III)V",
            at = @At("HEAD"), index = 4, argsOnly = true, remap = false, require = 0)
    private int insightaddons$globalCap(int original) {
        return InsightConfig.DROP_RATIO.get() >= 2 ? original : InsightConfig.DROP_MAX_TOTAL.get();
    }
}
