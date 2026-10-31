package dev.insightaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 화면 위치(leftPos/topPos)를 옮기기 위한 접근자 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor(value = "leftPos", remap = false)
    void insightaddons$setLeftPos(int value);

    @Accessor(value = "topPos", remap = false)
    void insightaddons$setTopPos(int value);
}
