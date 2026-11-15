package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.WindowDrag;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 마인크래프트 1.21.1의 GuiGraphics.enableScissor는 이동(translate)한 좌표계를 반영하지 않아서,
 * 창을 끌어 옮기면 가위(scissor)로 잘라 그리는 부분(칸 선 등)이 원래 자리에 잘려 사라집니다.
 * 창을 옮겨 그리는 동안만 가위 영역도 같은 만큼 옮깁니다.
 */
@Mixin(GuiGraphics.class)
public abstract class ScissorOffsetMixin {
    private static boolean insightaddons$busy;

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$shift(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        int dx = WindowDrag.scissorDx();
        int dy = WindowDrag.scissorDy();
        if (insightaddons$busy || (dx == 0 && dy == 0)) {
            return;
        }
        insightaddons$busy = true;
        try {
            ((GuiGraphics) (Object) this).enableScissor(x1 + dx, y1 + dy, x2 + dx, y2 + dy);
        } finally {
            insightaddons$busy = false;
        }
        ci.cancel();
    }
}
