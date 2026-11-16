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

    /** Fancy Items와 같은 색 규칙: 이름 색(흰색 제외) -> 희귀도 색, 일반 등급은 없음. 마법 부여 빛만 있으면 보라색 */
    private static int colorOf(ItemStack stack) {
        Integer nameColor = findColor(stack.getHoverName());
        if (nameColor != null && nameColor != 0xFFFFFF) {
            return nameColor;
        }
        Rarity rarity = stack.getRarity();
        Integer rgb = rarity.color().getColor();
        if (rarity != Rarity.COMMON && rgb != null) {
            return rgb;
        }
        return stack.hasFoil() ? 0xB070FF : -1;
    }

    private static Integer findColor(net.minecraft.network.chat.Component c) {
        var color = c.getStyle().getColor();
        if (color != null) {
            return color.getValue();
        }
        for (var child : c.getSiblings()) {
            Integer v = findColor(child);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    // --- 아래 별빛(스타버스트) 효과는 Fancy Items(Fooze, MIT)의 StarburstEffect를 가져와 3D 아이템용으로 옮긴 것입니다. ---
    private static final int RAYS = 9;
    private static final float RAY_LENGTH = 0.5f;
    private static final float RAY_WIDTH = 0.25f;
    private static final float LENGTH_VAR = 0.1f;
    private static final int LENGTH_VAR_COUNT = 3;
    private static final float WIDTH_VAR = 0.05f;
    private static final int WIDTH_VAR_COUNT = 2;
    private static final float PULSE_LEN_MIN = 0.9f;
    private static final float PULSE_LEN_MAX = 1.1f;
    private static final float PULSE_W_MIN = 0.33f;
    private static final float PULSE_W_MAX = 1.5f;
    private static final int PULSE_DURATION = 80;
    private static final int ROTATION_DURATION = 400;

    /**
     * 현재 변환(아이템의 위치/회전/크기가 적용된 상태) 기준으로, 카메라를 향한 별빛(회전하고 맥동하는 광선)을 아이템 뒤에 그립니다.
     * 아이템 본체를 그리기 전에 호출하면 광선이 아이템 뒤에 깔립니다.
     */
    public static void draw(PoseStack ps, MultiBufferSource buffers, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
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

            float time = net.minecraft.Util.getMillis() / 50.0F;
            int offset = Math.floorMod(ItemStack.hashItemAndComponents(stack), Math.max(PULSE_DURATION, ROTATION_DURATION));
            float phase = ((float) Math.sin((time + offset) * Math.PI * 2.0F / PULSE_DURATION) + 1.0F) / 2.0F;
            float pulseLength = PULSE_LEN_MIN + (PULSE_LEN_MAX - PULSE_LEN_MIN) * phase;
            float pulseWidth = PULSE_W_MIN + (PULSE_W_MAX - PULSE_W_MIN) * phase;
            float rotation = (float) ((time + offset) * Math.PI * 2.0D / ROTATION_DURATION);

            ps.pushPose();
            ps.mulPose(local);
            ps.scale(1.5f, 1.5f, 1.5f);
            VertexConsumer rays = buffers.getBuffer(RenderType.dragonRays());
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            for (int ray = 0; ray < RAYS; ray++) {
                float length = pulseLength * (RAY_LENGTH + (ray % LENGTH_VAR_COUNT) * LENGTH_VAR);
                float width = pulseWidth * (RAY_WIDTH + (ray % WIDTH_VAR_COUNT) * WIDTH_VAR);
                float angle = (float) (ray * (Math.PI * 2.0D / RAYS)) + rotation;
                ps.pushPose();
                ps.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
                Matrix4f m = ps.last().pose();
                rays.addVertex(m, 0f, 0f, 0f).setColor(r, g, b, 255);
                rays.addVertex(m, width, length, 0f).setColor(r, g, b, 0);
                rays.addVertex(m, -width, length, 0f).setColor(r, g, b, 0);
                ps.popPose();
            }
            ps.popPose();
        } catch (Throwable ignored) {
            // 빛은 장식이므로 실패해도 아이템 표시에는 영향이 없다
        }
    }
}
