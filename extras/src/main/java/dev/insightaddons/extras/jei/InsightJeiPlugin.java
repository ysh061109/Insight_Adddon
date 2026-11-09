package dev.insightaddons.extras.jei;

import dev.insightaddons.extras.InsightAddons;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import dev.insightaddons.extras.client.HoverTracker;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 플러그인. 레시피 화면의 "+" 버튼 줄 아래에 "조합 재료 표시" 버튼을 추가합니다.
 * 이 클래스는 JEI가 설치된 경우에만 JEI가 직접 불러옵니다.
 */
@JeiPlugin
public class InsightJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new RecipeFocusButtonFactory());
        registration.addRecipeButtonFactory(new FindItemButtonFactory());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // 마우스를 올려 둔 아이템을 알려 주는 함수 (JEI 클래스를 다른 코드가 직접 참조하지 않게 한다)
        HoverTracker.setJeiProbe(() -> runtime.getRecipesGui()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                .map(ItemStack::getItem)
                .orElse(null));
    }

    @Override
    public void onRuntimeUnavailable() {
        HoverTracker.setJeiProbe(null);
    }
}
