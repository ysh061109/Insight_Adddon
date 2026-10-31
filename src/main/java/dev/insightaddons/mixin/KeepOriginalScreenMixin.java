package dev.insightaddons.mixin;

import dev.insightaddons.client.InsightConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * InSight Chests는 상자 화면을 열 때 자체 유리 화면(GlassChestScreen)으로 바꿔 끼웁니다.
 * Sophisticated 계열(배낭, 통, 상자)은 업그레이드 탭 등 고유 화면이 중요하므로,
 * 화면 교체만 건너뛰고 카메라 전환과 3D 아이템 연출은 그대로 둡니다.
 *
 * 주입 지점을 못 찾으면 require=0 때문에 조용히 건너뛰고, InSight Chests 기본 동작이 유지됩니다.
 */
@Mixin(targets = "com.mrsun.insightchests.client.gui.GlassmorphismChestScreenHandler", remap = false)
public abstract class KeepOriginalScreenMixin {

    @Redirect(method = "onScreenOpening",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/event/ScreenEvent$Opening;setNewScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
            remap = false, require = 0)
    private void insightaddons$keepOriginal(ScreenEvent.Opening event, Screen replacement) {
        if (InsightConfig.KEEP_ORIGINAL_SCREEN.get()
                && event.getScreen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().getClass().getName().startsWith("net.p3pp3rf1y.sophisticated")) {
            return;
        }
        event.setNewScreen(replacement);
    }
}
