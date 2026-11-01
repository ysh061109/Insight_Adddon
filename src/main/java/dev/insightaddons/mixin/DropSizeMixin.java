package dev.insightaddons.mixin;

import com.mrsun.insightchests.bridge.IItemBridge;
import dev.insightaddons.client.InsightConfig;
import dev.insightaddons.client.ItemSize;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * 떨어지는 3D 아이템 하나(CorePhysicsItem)의 크기를 정합니다.
 *  - 개수를 늘렸을 때 상자 밖으로 넘치지 않도록 실제 개수(1:1)는 0.72배, 절반은 0.85배로 작게 합니다.
 *  - Fancy Items가 있으면(또는 itemSizeScaling = on) 아이템의 실제 크기(작은 조각은 작게, 도구는 크게)도 반영합니다.
 * InSight Chests 클래스의 final 필드(radius, renderScale)는 리플렉션으로 바꿉니다. 실패하면 원래 크기 그대로입니다.
 */
@Mixin(targets = "com.mrsun.insightchests.core.physics.CorePhysicsItem", remap = false)
public abstract class DropSizeMixin {
    private static Field fRadius;
    private static Field fScale;
    private static boolean failed;

    @Inject(method = "<init>(Lcom/mrsun/insightchests/bridge/IItemBridge;IIFFFLjava/util/Random;)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void insightaddons$resize(IItemBridge item, int slot, int sub, float x, float y, float z, Random random,
                                      CallbackInfo ci) {
        if (failed) {
            return;
        }
        try {
            int mode = InsightConfig.DROP_RATIO.get();
            float factor = mode == 0 ? 0.72f : mode == 1 ? 0.85f : 1.0f;
            factor *= dev.insightaddons.client.ContainerKind.itemScale();
            if (ItemSize.enabled() && item != null && item.getRawStack() instanceof ItemStack stack) {
                factor *= ItemSize.factor(stack);
            }
            if (Math.abs(factor - 1.0f) < 0.001f) {
                return;
            }
            if (fRadius == null) {
                Class<?> cls = this.getClass();
                fRadius = cls.getField("radius");
                fScale = cls.getField("renderScale");
                fRadius.setAccessible(true);
                fScale.setAccessible(true);
            }
            fRadius.setFloat(this, fRadius.getFloat(this) * factor);
            fScale.setFloat(this, fScale.getFloat(this) * factor);
        } catch (Throwable t) {
            failed = true;
        }
    }
}
