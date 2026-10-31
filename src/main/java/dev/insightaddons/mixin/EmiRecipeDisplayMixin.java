package dev.insightaddons.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.insightaddons.emi.EmiFocusButton;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * EMI가 설치되어 있으면 JEI의 레시피 화면은 쓰이지 않으므로, EMI의 레시피 한 칸(RecipeDisplay)에
 * "주변 보관함에서 재료 찾기" 버튼을 직접 붙입니다. ("+" 버튼이 있는 오른쪽 줄)
 *
 * EMI 안쪽 클래스에 의존하므로 EMI 업데이트로 바뀌면 조용히 꺼집니다. (require = 0)
 */
@Mixin(targets = "dev.emi.emi.screen.RecipeDisplay", remap = false)
public abstract class EmiRecipeDisplayMixin {
    @Shadow(remap = false)
    @Final
    public EmiRecipe recipe;
    @Shadow(remap = false)
    @Final
    private int width;
    @Shadow(remap = false)
    @Final
    private int height;
    @Shadow(remap = false)
    private int rows;
    @Shadow(remap = false)
    private List<?> rightButtons;

    @Inject(method = "getWidgets", at = @At("RETURN"), remap = false, require = 0)
    private void insightaddons$addFocusButton(int x, int y, int availableWidth, int availableHeight,
                                              CallbackInfoReturnable<Object> cir) {
        try {
            if (recipe == null || !ModList.get().isLoaded("insightchests")) {
                return;
            }
            if (!(cir.getReturnValue() instanceof WidgetHolder holder)) {
                return;
            }
            int size = rightButtons.size();
            int perColumn = Math.max(1, rows);
            // EMI의 오른쪽 버튼 줄과 같은 계산: 맨 아래가 "+", 위로 쌓인다.
            int space = Math.min(8, height + 8 - (Math.min(perColumn, size) * 14 - 2));
            int bottom = height + 8 / 2 - 12 - space / 2;
            // 두 버튼(재료 찾기, 이 아이템 찾기)을 차례로 붙인다
            for (int n = 0; n < 2; n++) {
                int idx = size + n;
                int bx;
                int by;
                if (idx < perColumn) {
                    bx = width + 5;
                    by = bottom - 14 * idx;
                } else {
                    bx = width + 5 + 14 * ((idx + perColumn - 1) / perColumn);
                    by = bottom;
                }
                holder.add(n == 0 ? new EmiFocusButton(bx, by, recipe) : new dev.insightaddons.emi.EmiFindButton(bx, by, recipe));
            }
        } catch (Throwable ignored) {
            // EMI 내부 구조가 다르면 버튼만 안 붙는다
        }
    }
}
