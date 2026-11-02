package dev.insightaddons.mixin;

import dev.insightaddons.client.GlobList;
import dev.insightaddons.client.InsightConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InSight Chests는 "이 블록이 상자인가?"를 GlassmorphismChestScreenHandler.isChest()에서 판단하고,
 * 바닐라/엔더 상자가 아니면 IronChestsCompat.isIronChest(state, blockEntity)로 마지막 확인을 합니다.
 * 이 정적 메서드가 true를 돌려주면 카메라 전환, 유리 화면, 3D 아이템 시뮬레이션이 그대로 적용됩니다.
 * 설정에 등록한 모드의 블록(기본: Sophisticated Backpacks)도 true로 돌려줍니다.
 *
 * InSight Chests 쪽 메서드가 바뀌어 주입할 수 없으면 mixins.json의 defaultRequire=0 때문에 조용히 건너뜁니다.
 */
@Mixin(targets = "com.mrsun.insightchests.compat.ironchest.IronChestsCompat", remap = false)
public abstract class IronChestsCompatMixin {
    private static final GlobList BLOCKS = new GlobList();
    private static final java.util.List<String> MEKANISM_PERSONAL = java.util.List.of("mekanism:personal_*");

    @Inject(method = "isIronChest", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$treatAsChest(BlockState state, BlockEntity blockEntity,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (state == null || !InsightConfig.INSIGHT_MODE_ENABLED.get()) {
            return;
        }
        net.minecraft.resources.ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (BLOCKS.matches(InsightConfig.INSIGHT_MODE_BLOCKS.get(), id)) {
            cir.setReturnValue(true);
            return;
        }
        // Mekanism 개인 상자/개인 통은 설정 목록과 상관없이(이미 저장된 설정에도) 기본으로 적용한다
        if (InsightConfig.MEKANISM_ENABLED.get() && BLOCKS.matches(MEKANISM_PERSONAL, id)) {
            cir.setReturnValue(true);
        }
    }
}
