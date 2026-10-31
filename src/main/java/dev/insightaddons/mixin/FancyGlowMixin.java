package dev.insightaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.insightaddons.client.ItemGlow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * InSight Chests가 보관함 안 아이템을 그리는 곳(ChestItemsLevelRenderer.render)의 renderStatic 호출을 가로채,
 * Fancy Items 계열 모드가 있을 때 아이템 뒤에 등급 색 후광을 먼저 그린 다음 원래대로 아이템을 그립니다.
 */
@Mixin(targets = "com.mrsun.insightchests.client.render.ChestItemsLevelRenderer", remap = false)
public abstract class FancyGlowMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V"),
            remap = false, require = 0)
    private static void insightaddons$glow(ItemRenderer renderer, ItemStack stack, ItemDisplayContext ctx,
                                           int light, int overlay, PoseStack ps, MultiBufferSource buffers,
                                           Level level, int seed) {
        if (ItemGlow.enabled()) {
            ItemGlow.draw(ps, buffers, stack);
        }
        renderer.renderStatic(stack, ctx, light, overlay, ps, buffers, level, seed);
    }
}
