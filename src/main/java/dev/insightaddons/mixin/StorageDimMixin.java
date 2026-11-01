package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sophisticated 저장소 화면은 render()에서 super.renderBackground()를 직접 부릅니다.
 * "원래 화면 유지"를 켜면 이 호출이 월드를 어둡게 만들므로, 유리 스킨이 켜져 있을 때는 이 호출 자체를 건너뜁니다.
 * (Screen 쪽 주입이 어떤 이유로 적용되지 않아도 막히도록 한 겹 더 둔 것입니다.)
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class StorageDimMixin {

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            remap = false, require = 0)
    private void insightaddons$skipDim(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (GlassSkin.isStorageScreen(screen)) {
            return;
        }
        screen.renderBackground(g, mouseX, mouseY, partialTick);
    }
}
