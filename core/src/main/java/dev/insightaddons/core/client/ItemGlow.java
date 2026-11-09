package dev.insightaddons.core.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Fancy Items 계열 모드가 있을 때, 보관함 안에서 떨어지는 3D 아이템에도 같은 종류의 빛 효과(등급 색 후광)를 줍니다.
 * 그 모드의 내부 코드를 쓰지 않고, 아이템 뒤에 등급 색 후광 한 장을 직접 그립니다.
 * 일반 등급은 그리지 않고, 희귀도가 높거나 마법 부여 빛이 있는 아이템만 빛납니다.
 */
public final class ItemGlow {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("insight_addons", "dynamic/glow");
    private static boolean textureReady;
    private static boolean textureFailed;
    private static Boolean modPresent;

    private ItemGlow() {}

    /** auto = 설정한 모드 ID 중 하나라도 설치되어 있을 때만, on = 항상, off = 끔 */
    public static boolean enabled() {
        String mode = InsightConfig.ITEM_GLOW.get();
        if ("on".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("off".equalsIgnoreCase(mode)) {
            return false;
        }
        if (modPresent == null) {
            modPresent = false;
            for (String id : InsightConfig.FANCY_MOD_IDS.get()) {
                if (ModList.get().isLoaded(id.trim())) {
                    modPresent = true;
                    break;
                }
            }
        }
        return modPresent;
    }

    private static boolean ensureTexture() {
        if (textureReady) {
            return true;
        }
        if (textureFailed) {
            return false;
        }
        try {
            int size = 64;
            NativeImage image = new NativeImage(size, size, false);
            float c = (size - 1) / 2f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                    float a = Math.max(0f, 1f - d);
                    a = a * a * a;
                    int alpha = Math.min(255, (int) (a * 255));
                    // ABGR: 흰색 + 알파 (색은 정점 색으로 입힌다)
                    image.setPixelRGBA(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(image));
            textureReady = true;
        } catch (Throwable t) {
            textureFailed = true;
        }
        return textureReady;
    }

    private static int colorOf(ItemStack stack) {
        Rarity rarity = stack.getRarity();
        Integer rgb = rarity.color().getColor();
        if (rarity != Rarity.COMMON && rgb != null) {
            return rgb;
        }
        return stack.hasFoil() ? 0xB070FF : -1;
    }

    /**
     * 현재 변환(아이템의 위치/회전/크기가 적용된 상태) 기준으로 카메라를 향한 후광을 그립니다.
     * 아이템 본체를 그리기 전에 호출하면 후광이 아이템 뒤에 깔립니다.
     */
    public static void draw(PoseStack ps, MultiBufferSource buffers, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ensureTexture()) {
            return;
        }
        int rgb = colorOf(stack);
        if (rgb < 0) {
            return;
        }
        try {
            Quaternionf cam = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            Quaternionf item = new Quaternionf();
            ps.last().pose().getNormalizedRotation(item);
            // 아이템 회전을 상쇄해서 카메라와 같은 방향으로 맞춘다
            Quaternionf local = new Quaternionf(item).invert().mul(cam);

            ps.pushPose();
            ps.mulPose(local);
            Matrix4f m = ps.last().pose();
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            float half = 0.95f;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int a = 150;
            vertex(vc, ps, m, -half, -half, 0f, 0f, 1f, r, g, b, a);
            vertex(vc, ps, m, half, -half, 0f, 1f, 1f, r, g, b, a);
            vertex(vc, ps, m, half, half, 0f, 1f, 0f, r, g, b, a);
            vertex(vc, ps, m, -half, half, 0f, 0f, 0f, r, g, b, a);
            ps.popPose();
        } catch (Throwable ignored) {
            // 후광은 장식이므로 실패해도 아이템 표시에는 영향이 없다
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack ps, Matrix4f m, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(ps.last(), 0f, 0f, 1f);
    }
}
