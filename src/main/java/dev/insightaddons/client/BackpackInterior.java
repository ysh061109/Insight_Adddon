package dev.insightaddons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrsun.insightchests.physics.ChestBoundingBox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 배낭 내부를 "밝게" 그리는 자체 렌더러.
 * InSight Chests는 엔티티 조명이 걸리는 렌더 타입으로 내부를 그리는데, 배낭에서는 이것이 매우 어둡거나 검게 나오는 경우가 있었습니다.
 * 여기서는 조명 계산이 없는 렌더 타입(비콘 빔)에 InSight의 "항상 깊이 통과" 상태를 덧씌워 질감 색 그대로 환하게 그립니다.
 * InSight 내부 클래스는 리플렉션으로만 접근하며, 하나라도 실패하면 false를 돌려줘서 호출한 쪽이 InSight 원래 그리기로 되돌립니다.
 */
final class BackpackInterior {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static RenderStateShard alwaysDepth;
    private static Method wrap;
    private static boolean failed;

    private BackpackInterior() {}

    private static RenderType type(ResourceLocation tex) throws Exception {
        RenderType t = TYPES.get(tex);
        if (t != null) {
            return t;
        }
        if (alwaysDepth == null) {
            Class<?> hollow = Class.forName("com.mrsun.insightchests.client.render.HollowChestDepthRenderer");
            Field f = hollow.getDeclaredField("ALWAYS_DEPTH_TEST");
            f.setAccessible(true);
            alwaysDepth = (RenderStateShard) f.get(null);
            Class<?> outer = Class.forName("com.mrsun.insightchests.client.render.OuterWrappedRenderType");
            wrap = outer.getMethod("wrapExactlyOnce", String.class, RenderType.class, RenderStateShard.class);
        }
        RenderType base = RenderType.beaconBeam(tex, false);
        t = (RenderType) wrap.invoke(null, "insight_addons_backpack_" + tex.getPath(), base, alwaysDepth);
        TYPES.put(tex, t);
        return t;
    }

    private static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, float u, float vv, int shade) {
        c.addVertex(m, x, y, z).setColor(shade, shade, shade, 255).setUv(u, vv).setLight(15728880);
    }

    /** 앞뒤 어느 쪽에서 봐도 보이게 두 방향 모두 그린다 */
    private static void quad(VertexConsumer c, Matrix4f m, float[] a, float[] b, float[] d, float[] e, float us, float vs, int shade) {
        v(c, m, a[0], a[1], a[2], 0, 0, shade);
        v(c, m, b[0], b[1], b[2], us, 0, shade);
        v(c, m, d[0], d[1], d[2], us, vs, shade);
        v(c, m, e[0], e[1], e[2], 0, vs, shade);
        v(c, m, e[0], e[1], e[2], 0, vs, shade);
        v(c, m, d[0], d[1], d[2], us, vs, shade);
        v(c, m, b[0], b[1], b[2], us, 0, shade);
        v(c, m, a[0], a[1], a[2], 0, 0, shade);
    }

    static boolean render(PoseStack ps, MultiBufferSource buffers, ChestBoundingBox box) {
        if (failed || box == null || box.cavityTexture == null) {
            return false;
        }
        try {
            VertexConsumer c = buffers.getBuffer(type(box.cavityTexture));
            Matrix4f m = ps.last().pose();
            float x0 = box.minX;
            float x1 = box.maxX;
            float y0 = box.minY;
            float y1 = box.maxY + 0.045f;
            float z0 = box.minZ;
            float z1 = box.maxZ;
            float tx = Math.max(1f, (x1 - x0) * 2f);
            float tz = Math.max(1f, (z1 - z0) * 2f);
            float ty = Math.max(1f, (y1 - y0) * 2f);
            // 바닥
            quad(c, m, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, new float[]{x0, y0, z1}, tx, tz, 215);
            // 네 벽
            quad(c, m, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y1, z0}, new float[]{x0, y1, z0}, tx, ty, 255);
            quad(c, m, new float[]{x0, y0, z1}, new float[]{x1, y0, z1}, new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, tx, ty, 235);
            quad(c, m, new float[]{x0, y0, z0}, new float[]{x0, y0, z1}, new float[]{x0, y1, z1}, new float[]{x0, y1, z0}, tz, ty, 245);
            quad(c, m, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, new float[]{x1, y1, z1}, new float[]{x1, y1, z0}, tz, ty, 225);
            return true;
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.InsightAddons.LOGGER.warn("배낭 내부 밝게 그리기에 실패해 InSight 원래 방식으로 되돌립니다: {}", t.toString());
            return false;
        }
    }
}
