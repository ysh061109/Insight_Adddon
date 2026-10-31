package dev.insightaddons.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 아이템의 "실제 크기"를 추정합니다. 블록 아이템은 1, 납작한 아이템은 그림에서 불투명한 부분의 크기를 봅니다.
 * (작은 조각/가루는 작게, 검/도구는 크게). 리소스팩으로 질감이 바뀌면 다음 실행부터 반영됩니다.
 */
public final class ItemSize {
    private static final Map<Item, Float> CACHE = new HashMap<>();
    private static Boolean fancyPresent;

    private ItemSize() {}

    /** Fancy Items 계열 모드가 설치되어 있는지 */
    public static boolean fancyItemsPresent() {
        if (fancyPresent == null) {
            ModList list = ModList.get();
            fancyPresent = list.isLoaded("fancyitems") || list.isLoaded("fancy_items") || list.isLoaded("fancyitem");
        }
        return fancyPresent;
    }

    /** 설정(auto/on/off)에 따라 크기 조절을 해야 하는지 */
    public static boolean enabled() {
        String mode = InsightConfig.ITEM_SIZE_SCALING.get();
        if ("on".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("off".equalsIgnoreCase(mode)) {
            return false;
        }
        return fancyItemsPresent();
    }

    /** 0.6 ~ 1.15 사이의 배율 */
    public static float factor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0f;
        }
        return CACHE.computeIfAbsent(stack.getItem(), item -> compute(stack));
    }

    private static float compute(ItemStack stack) {
        try {
            if (stack.getItem() instanceof BlockItem) {
                return 1.0f;
            }
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(42L));
            if (quads.isEmpty()) {
                return 1.0f;
            }
            TextureAtlasSprite sprite = quads.get(0).getSprite();
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.getOriginalImage();
            int w = contents.width();
            int h = contents.height();
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int alpha = (image.getPixelRGBA(px, py) >>> 24) & 0xFF;
                    if (alpha > 16) {
                        minX = Math.min(minX, px);
                        maxX = Math.max(maxX, px);
                        minY = Math.min(minY, py);
                        maxY = Math.max(maxY, py);
                    }
                }
            }
            if (maxX < 0) {
                return 1.0f;
            }
            float extent = Math.max(maxX - minX + 1, maxY - minY + 1) / (float) Math.max(w, h);
            return Math.max(0.6f, Math.min(1.15f, 0.55f + 0.6f * extent));
        } catch (Throwable t) {
            return 1.0f;
        }
    }
}
