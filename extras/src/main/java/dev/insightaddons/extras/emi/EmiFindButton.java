package dev.insightaddons.extras.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.insightaddons.extras.client.HoverTracker;
import dev.insightaddons.extras.client.RecipeFocus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** EMI 레시피 화면의 "이 아이템 찾기" 버튼(12x12). 마우스를 올려 둔 아이템, 없으면 결과 아이템을 찾는다. */
public final class EmiFindButton extends Widget {
    private static final int SIZE = 12;
    private static final net.minecraft.resources.ResourceLocation TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("insight_addons_extras", "textures/gui/button_find.png");

    private final int x;
    private final int y;
    private final EmiRecipe recipe;

    public EmiFindButton(int x, int y, EmiRecipe recipe) {
        this.x = x;
        this.y = y;
        this.recipe = recipe;
    }

    /** 이 레시피에 들어 있는 아이템(재료, 촉매, 결과 전부) */
    private java.util.Set<Item> recipeItems() {
        java.util.Set<Item> items = new java.util.HashSet<>();
        List<dev.emi.emi.api.stack.EmiIngredient> all = new ArrayList<>();
        all.addAll(recipe.getInputs());
        all.addAll(recipe.getCatalysts());
        all.addAll(recipe.getOutputs());
        for (dev.emi.emi.api.stack.EmiIngredient ing : all) {
            for (EmiStack st : ing.getEmiStacks()) {
                ItemStack item = st.getItemStack();
                if (!item.isEmpty()) {
                    items.add(item.getItem());
                }
            }
        }
        return items;
    }

    /** 이 레시피 안에서 최근에 마우스를 올려 둔 아이템, 없으면 결과 아이템. 옆 목록에서 올렸던 아이템은 무시한다. */
    private Item target() {
        Item hovered = HoverTracker.lastRecent();
        if (hovered != null && recipeItems().contains(hovered)) {
            return hovered;
        }
        for (EmiStack out : recipe.getOutputs()) {
            ItemStack shown = out.getItemStack();
            if (!shown.isEmpty()) {
                return shown.getItem();
            }
        }
        return null;
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, SIZE, SIZE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean hover = getBounds().contains(mouseX, mouseY);
        boolean on = RecipeFocus.isActive();
        BevelButton.draw(g, x, y, SIZE, hover, on);
        g.blit(TEX, x + 1, y + 1, 0, 0, 9, 9, 9, 9);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        List<ClientTooltipComponent> out = new ArrayList<>();
        out.add(ClientTooltipComponent.create(Component.translatable("insight_addons.find.button").getVisualOrderText()));
        Item t = target();
        if (t != null) {
            out.add(ClientTooltipComponent.create(
                    Component.translatable("insight_addons.find.target", new ItemStack(t).getHoverName()).getVisualOrderText()));
        }
        out.add(ClientTooltipComponent.create(Component.translatable("insight_addons.find.hint").getVisualOrderText()));
        return out;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (RecipeFocus.isActive()) {
            RecipeFocus.clear();
            return true;
        }
        RecipeFocus.toggleItem(target());
        return true;
    }
}
