package dev.insightaddons.extras.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.insightaddons.extras.client.RecipeFocus;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 레시피마다 "+" 버튼 아래 줄에 붙는 "조합 재료 표시" 버튼을 만듭니다. */
public final class RecipeFocusButtonFactory implements IRecipeButtonControllerFactory {
    private static final int MAX_OPTIONS_PER_SLOT = 48;
    private static final net.minecraft.resources.ResourceLocation TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("insight_addons_extras", "textures/gui/button_focus.png");

    /** 버튼 아이콘: 재료가 떠 있는 작은 상자 (9x9) */
    private static final IDrawable ICON = new IDrawable() {
        @Override
        public int getWidth() {
            return 9;
        }

        @Override
        public int getHeight() {
            return 9;
        }

        @Override
        public void draw(GuiGraphics g, int x, int y) {
            g.blit(TEX, x, y, 0, 0, 9, 9, 9, 9);
        }
    };

    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layout) {
        return new Controller(layout);
    }

    private static final class Controller implements IIconButtonController {
        private final IRecipeLayoutDrawable<?> layout;

        Controller(IRecipeLayoutDrawable<?> layout) {
            this.layout = layout;
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            // 왼쪽 클릭이나 키보드 입력만 받는다
            if (input.getKey().getType() == InputConstants.Type.MOUSE && input.getKey().getValue() != 0) {
                return false;
            }
            if (input.isSimulate()) {
                return true;
            }
            if (RecipeFocus.isActive()) {
                RecipeFocus.clear();
                return true;
            }

            List<RecipeFocus.Need> needs = new ArrayList<>();
            for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)) {
                Set<Item> options = new LinkedHashSet<>();
                int count = 1;
                boolean first = true;
                for (ItemStack stack : (Iterable<ItemStack>) slot.getItemStacks()::iterator) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    if (first) {
                        count = Math.max(1, stack.getCount());
                        first = false;
                    }
                    if (options.size() < MAX_OPTIONS_PER_SLOT) {
                        options.add(stack.getItem());
                    }
                }
                if (!options.isEmpty()) {
                    needs.add(new RecipeFocus.Need(List.copyOf(options), count));
                }
            }
            if (needs.isEmpty()) {
                return true;
            }

            Component title = Component.empty();
            for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) {
                Optional<ItemStack> shown = slot.getDisplayedItemStack();
                if (shown.isPresent() && !shown.get().isEmpty()) {
                    title = shown.get().getHoverName();
                    break;
                }
            }

            // 꺼져 있으면 시작하고 레시피 화면을 닫는다. 켜져 있으면 끈다.
            RecipeFocus.toggle(title, needs);
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("insight_addons.jei.button"));
            tooltip.add(Component.translatable(RecipeFocus.isActive()
                    ? "insight_addons.jei.button.off" : "insight_addons.jei.button.on"));
        }

        @Override
        public void initState(IButtonState state) {
            state.setIcon(ICON);
        }

        @Override
        public void updateState(IButtonState state) {
            state.setForcePressed(RecipeFocus.isActive());
        }
    }
}
