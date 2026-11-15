package dev.insightaddons.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** AE2 서버룸 연출용 3D 아이콘/화면 투영 글씨 도우미 (Extras의 StorageRenderer에서 필요한 부분만 가져옴). */
public final class AeDraw {
    private static final Map<Item, ItemStack> ICONS = new IdentityHashMap<>();

    private AeDraw() {}

    static ItemStack icon(Item item) {
        return ICONS.computeIfAbsent(item, i -> new ItemStack(i));
    }

    static void drawIcon(PoseStack ps, Vec3 camPos, Vec3 p, ItemStack stack, float scale, float spinDeg,
                                 MultiBufferSource buffers, Minecraft mc) {
        ps.pushPose();
        ps.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        ps.scale(scale, scale, scale);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
        ps.popPose();
    }

    private record Label(float x, float y, float scale, Component text, int color, int priority, boolean fixed, boolean count) {}

    private static final List<Label> LABELS = new ArrayList<>();

    /**
     * 3D 좌표를 화면에 투영해 GUI 글씨로 쌓아 둔다.
     * maxWorldWidth(월드 길이)가 0보다 크면 그 폭 안에 들어가도록 글씨를 줄이고, 그래도 너무 작으면(읽을 수 없으면) 그리지 않는다.
     * priority가 2 이상이면 다른 글씨와 겹쳐도 항상 그린다. 그 아래는 이미 그려진 글씨와 겹치면 건너뛴다.
     */
    static void drawLabel(PoseStack ps, Camera camera, Vec3 camPos, Vec3 p, Component text, float scale,
                                  int color, double maxWorldWidth, int priority) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f vp = viewProj(ps, camera);
        org.joml.Vector4f c = new org.joml.Vector4f((float) (p.x - camPos.x), (float) (p.y - camPos.y), (float) (p.z - camPos.z), 1f);
        vp.transform(c);
        if (c.w <= 0.05f || c.w > 48f) {
            return; // 카메라 뒤쪽이거나 너무 멂
        }
        float nx = c.x / c.w;
        float ny = c.y / c.w;
        if (Math.abs(nx) > 1.4f || Math.abs(ny) > 1.4f) {
            return;
        }
        // 글자 한 줄의 월드 높이(8px * scale)가 화면에서 몇 픽셀인지로 글씨 크기를 정한다
        org.joml.Vector3f up = new org.joml.Vector3f(camera.getUpVector()).mul(8f * scale);
        org.joml.Vector4f q = new org.joml.Vector4f((float) (p.x - camPos.x) + up.x, (float) (p.y - camPos.y) + up.y,
                (float) (p.z - camPos.z) + up.z, 1f);
        vp.transform(q);
        float ny2 = q.w > 0.05f ? q.y / q.w : ny;
        float guiW = mc.getWindow().getGuiScaledWidth();
        float guiH = mc.getWindow().getGuiScaledHeight();
        float pixels = Math.abs(ny2 - ny) * 0.5f * guiH;
        float fs = Math.max(0.6f, Math.min(2.4f, pixels / 8f));
        if (maxWorldWidth > 0) {
            float pxPerWorld = pixels / (8f * scale);
            float maxPx = (float) (maxWorldWidth * pxPerWorld);
            int w = Math.max(1, mc.font.width(text));
            if (w * fs > maxPx) {
                fs = maxPx / w;
            }
            if (fs < 0.6f) {
                return; // 칸에 안 들어가면 지저분하게 겹치느니 그리지 않는다
            }
        }
        LABELS.add(new Label((nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH - 4f * fs, fs, text, color, priority, false, false));
    }


    /**
     * 월드 좌표(카메라 기준 상대값)를 화면으로 보내는 행렬.
     * 레벨 렌더 이벤트의 PoseStack에는 카메라 회전이 들어 있지 않은 경우가 있어서(회전은 RenderSystem 쪽 모델뷰에 있음),
     * 포즈 행렬이 회전 없는 단위 행렬이면 카메라 회전을 직접 곱해 준다. 이미 회전이 들어 있으면 그대로 쓴다.
     */
    private static Matrix4f eventProjection;

    /** 레벨 렌더 이벤트가 알려 주는 3D 투영 행렬을 저장한다. (이 시점의 RenderSystem 행렬은 GUI용일 수 있어서 이벤트 값을 우선 쓴다) */
    static void setProjection(Matrix4f m) {
        eventProjection = m == null ? null : new Matrix4f(m);
    }

    static Matrix4f projection() {
        return eventProjection != null ? eventProjection : RenderSystem.getProjectionMatrix();
    }

    private static Matrix4f viewProj(PoseStack ps, Camera camera) {
        Matrix4f pose = ps.last().pose();
        Matrix4f vp = new Matrix4f(projection());
        boolean noRotation = Math.abs(pose.m00() - 1f) < 1.0e-3f && Math.abs(pose.m11() - 1f) < 1.0e-3f
                && Math.abs(pose.m22() - 1f) < 1.0e-3f && Math.abs(pose.m01()) < 1.0e-3f && Math.abs(pose.m10()) < 1.0e-3f;
        if (noRotation) {
            vp.mul(new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf())));
        }
        return vp.mul(pose);
    }


    /** RenderGuiEvent.Post에서 호출: 이번 프레임에 모인 글씨를 그린다. */
    static void renderHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc) {
        if (LABELS.isEmpty()) {
            return;
        }
        // 중요한 것부터 그리고, 중요도가 낮은 글씨는 이미 그린 글씨와 겹치면 건너뛴다
        List<Label> order = new ArrayList<>(LABELS);
        order.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        List<float[]> placed = new ArrayList<>();
        for (Label l : order) {
            int w = mc.font.width(l.text());
            float hw = w * l.scale() / 2f + 2f;
            float top = l.y() - 1f;
            float bottom = l.y() + 9f * l.scale() + 1f;
            float left = l.count() ? l.x() - w * l.scale() - 1f : l.x() - hw;
            float right = l.count() ? l.x() + 1f : l.x() + hw;
            if (l.priority() < 2) {
                boolean clash = false;
                for (float[] r : placed) {
                    if (left < r[2] && right > r[0] && top < r[3] && bottom > r[1]) {
                        clash = true;
                        break;
                    }
                }
                if (clash) {
                    continue;
                }
            }
            placed.add(new float[]{left, top, right, bottom});

            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(l.x(), l.y(), 200f);
            ps.scale(l.scale(), l.scale(), 1f);
            if (l.count()) {
                g.drawString(mc.font, l.text(), -w, 0, l.color(), true);
            } else {
                g.fill(-w / 2 - 2, -1, w / 2 + 2, 9, 0x48000000);
                g.drawString(mc.font, l.text(), -w / 2, 0, l.color(), true);
            }
            ps.popPose();
        }
        LABELS.clear();
    }

}
