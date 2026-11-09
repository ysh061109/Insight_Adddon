package dev.insightaddons.extras.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.insightaddons.extras.client.HoverTracker;
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

import java.util.Optional;

/**
 * "이 아이템 찾기" 버튼: 조합법의 재료가 아니라, 마우스를 올려 두었던 그 아이템(없으면 레시피의 결과 아이템)을
 * 주변 보관함에서 찾아 3D로 띄우고 들어 있는 보관함을 표시합니다.
 */
public final class FindItemButtonFactory implements IRecipeButtonControllerFactory {
    /** 돋보기 모양 아이콘 (9x9) */
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
            int c = 0xFFE8E8F0;
            g.fill(x + 1, y, x + 5, y + 1, c);
            g.fill(x + 1, y + 5, x + 5, y + 6, c);
            g.fill(x, y + 1, x + 1, y + 5, c);
            g.fill(x + 5, y + 1, x + 6, y + 5, c);
            g.fill(x + 5, y + 5, x + 6, y + 6, 0xFFFFD84A);
            g.fill(x + 6, y + 6, x + 7, y + 7, 0xFFFFD84A);
            g.fill(x + 7, y + 7, x + 9, y + 9, 0xFFFFD84A);
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

        /** 이 레시피에 들어 있는 아이템(재료, 촉매, 결과 전부) */
        private java.util.Set<Item> layoutItems() {
            java.util.Set<Item> items = new java.util.HashSet<>();
            for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews()) {
                slot.getItemStacks().forEach(st -> {
                    if (!st.isEmpty()) {
                        items.add(st.getItem());
                    }
                });
            }
            return items;
        }

        /**
         * 이 레시피 안에서 최근에 마우스를 올려 둔 아이템, 없으면 이 레시피의 결과 아이템.
         * 옆 목록(아이템 목록 등)에 올렸던 아이템은 이 레시피와 상관없으므로 무시한다.
         */
        private Item target() {
            Item hovered = HoverTracker.lastRecent();
            if (hovered != null && layoutItems().contains(hovered)) {
                return hovered;
            }
            for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) {
                Optional<ItemStack> shown = slot.getDisplayedItemStack();
                if (shown.isPresent() && !shown.get().isEmpty()) {
                    return shown.get().getItem();
                }
            }
            return null;
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
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
            RecipeFocus.toggleItem(target());
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("insight_addons.find.button"));
            Item t = target();
            if (t != null) {
                tooltip.add(Component.translatable("insight_addons.find.target", new ItemStack(t).getHoverName()));
            }
            tooltip.add(Component.translatable("insight_addons.find.hint"));
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
