package dev.insightaddons.extras.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.extras.client.AeWindow;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1의 가위(scissor)는 이동(translate)한 좌표계를 반영하지 않아서, 창을 끌어 옮기면 잘라 그리는 부분이 사라집니다.
 * 가장 아래 단계(RenderSystem)에서 창을 옮겨 그리는 동안만 가위 영역을 같은 만큼 옮깁니다.
 * (Plus와 같은 클래스에 둘이 동시에 걸려도 각자 자기 창을 그리는 동안만 움직이므로 겹치지 않습니다)
 */
@Mixin(RenderSystem.class)
public abstract class ScissorOffsetMixin {
    private static boolean insightaddons$busy;

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$shiftAe(int x, int y, int w, int h, CallbackInfo ci) {
        int dx = AeWindow.scissorDx();
        int dy = AeWindow.scissorDy();
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
