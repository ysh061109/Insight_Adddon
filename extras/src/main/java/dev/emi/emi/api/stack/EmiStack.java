package dev.emi.emi.api.stack;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 컴파일용 stub (EMI 실제 클래스를 쓰며 jar에 포함되지 않음). */
public abstract class EmiStack implements EmiIngredient {
    public abstract boolean isEmpty();

    public ItemStack getItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public List<EmiStack> getEmiStacks() {
        return List.of(this);
    }
}
