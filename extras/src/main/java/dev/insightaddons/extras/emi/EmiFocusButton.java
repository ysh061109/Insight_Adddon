package dev.insightaddons.extras.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** EMI 레시피 화면의 "+" 옆에 붙는 12x12 "주변 보관함에서 재료 찾기" 버튼. */
public final class EmiFocusButton extends Widget {
    private static final int SIZE = 12;
    private static final net.minecraft.resources.ResourceLocation TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("insight_addons_extras", "textures/gui/button_focus.png");
    private static final int MAX_OPTIONS_PER_SLOT = 48;

    private final int x;
    private final int y;
    private final EmiRecipe recipe;

    public EmiFocusButton(int x, int y, EmiRecipe recipe) {
        this.x = x;
        this.y = y;
        this.recipe = recipe;
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
        out.add(ClientTooltipComponent.create(Component.translatable("insight_addons.jei.button").getVisualOrderText()));
        out.add(ClientTooltipComponent.create(Component.translatable(RecipeFocus.isActive()
                ? "insight_addons.jei.button.off" : "insight_addons.jei.button.on").getVisualOrderText()));
        return out;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (RecipeFocus.isActive()) {
            RecipeFocus.clear();
            return true;
        }

        List<RecipeFocus.Need> needs = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            Set<Item> options = new LinkedHashSet<>();
            for (EmiStack stack : ingredient.getEmiStacks()) {
                ItemStack item = stack.getItemStack();
                if (!item.isEmpty() && options.size() < MAX_OPTIONS_PER_SLOT) {
                    options.add(item.getItem());
                }
            }
            if (!options.isEmpty()) {
                needs.add(new RecipeFocus.Need(List.copyOf(options),
                        (int) Math.max(1, Math.min(Integer.MAX_VALUE, ingredient.getAmount()))));
            }
        }
        if (needs.isEmpty()) {
            return true;
        }

        Component title = Component.empty();
        for (EmiStack out : recipe.getOutputs()) {
            ItemStack shown = out.getItemStack();
            if (!shown.isEmpty()) {
                title = shown.getHoverName();
                break;
            }
        }
        RecipeFocus.toggle(title, needs);
        return true;
    }
}
