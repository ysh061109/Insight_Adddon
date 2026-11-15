package dev.insightaddons.core.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.core.client.WindowDrag;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 마인크래프트 1.21.1의 가위(scissor)는 이동(translate)한 좌표계를 반영하지 않아서,
 * 창을 끌어 옮기면 가위로 잘라 그리는 부분(칸 선, 스크롤 영역 등)이 원래 자리에 잘려 사라집니다.
 * GuiGraphics를 거치든 RenderSystem을 직접 부르든 결국 여기로 오므로, 가장 아래 단계에서
 * 창을 옮겨 그리는 동안만 가위 영역을 같은 만큼 옮깁니다. (GuiGraphics 스택의 되돌리기 호출도 함께 보정됩니다)
 */
@Mixin(RenderSystem.class)
public abstract class ScissorOffsetMixin {
    private static boolean insightaddons$busy;

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$shift(int x, int y, int w, int h, CallbackInfo ci) {
        int dx = WindowDrag.scissorDx();
        int dy = WindowDrag.scissorDy();
        if (insightaddons$busy || (dx == 0 && dy == 0)) {
            return;
        }
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        insightaddons$busy = true;
        try {
            RenderSystem.enableScissor(x + (int) Math.round(dx * scale), y - (int) Math.round(dy * scale), w, h);
        } finally {
            insightaddons$busy = false;
        }
        ci.cancel();
    }
}
