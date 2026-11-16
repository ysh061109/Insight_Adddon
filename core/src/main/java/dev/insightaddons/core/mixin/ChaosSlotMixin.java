package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.ChestChaos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 난장판으로 아이콘이 날아가 있는 동안에는 그 슬롯의 원래 아이콘을 그리지 않는다. */
@Mixin(targets = {"net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
        "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase"}, remap = false)
public abstract class ChaosSlotMixin {
    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$hide(GuiGraphics g, Slot slot, CallbackInfo ci) {
        if (ChestChaos.hides(slot)) {
            ci.cancel();
        }
    }
}
