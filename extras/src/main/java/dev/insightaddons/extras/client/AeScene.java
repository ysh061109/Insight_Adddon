package dev.insightaddons.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 ME 단말기(조합 단말기, 패턴 인코딩 단말기 등)를 열면, 네트워크(셀)에 든 아이템을
 * 화면 옆 공중에 3D 진열대로 띄워 보여줍니다. 개수 포함, 단말기에서 꺼내면 그 아이템이 플레이어에게 날아가고,
 * 넣으면 플레이어에게서 진열대로 날아옵니다.
 * 데이터는 단말기가 클라이언트에 이미 받아 둔 목록(AeTerminal)을 쓰므로 서버에 이 모드가 없어도 동작합니다.
 */
public final class AeScene {
    private static final int MAX_SHOWN = 40;
    private static final int COLS = 5;
    private static final double PITCH = 0.27;
    private static final int FLY_TICKS = 14;

    private record Entry(Item item, int count) {}

    private record Fly(Item item, int slot, boolean out, long start) {}

    private static List<Entry> shown = List.of();
    private static Map<Item, Integer> last = Map.of();
    private static final List<Fly> FLIES = new ArrayList<>();
    private static float anim;
    private static float prevAnim;
    private static boolean open;
    private static int kinds;
    private static long total;

    private AeScene() {}

    public static void clear() {
        AePile.clear();
        shown = List.of();
        last = Map.of();
        FLIES.clear();
        anim = 0;
        prevAnim = 0;
        open = false;
    }

    public static boolean visible() {
        return anim > 0.01f;
    }

    private static void sound(net.minecraft.sounds.SoundEvent e, float pitch, float vol) {
        Fx.sound(e, pitch, vol);
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        open = InsightConfig.AE2_ENABLED.get() && AeTerminal.isTerminalScreen(mc.screen);
        prevAnim = anim;
        anim += ((open ? 1f : 0f) - anim) * 0.25f;
        if (!open && anim < 0.02f) {
            anim = 0;
            if (!shown.isEmpty()) {
                clear();
            }
            AePile.clear();
            return;
        }
        FLIES.removeIf(f -> now - f.start() > FLY_TICKS + 2);
        boolean physics = InsightConfig.AE2_PHYSICS.get();
        if (physics && open) {
            AePile.step(mc);
        }
        if (!open || now % 6 != 0) {
            return;
        }
        Map<Item, Integer> data = AeTerminal.contents(mc);
        if (data == null) {
            return;
        }
        // 이전 목록과 비교해서 꺼낸/넣은 아이템에 날아가는 연출을 붙인다
        if (!last.isEmpty()) {
            for (Map.Entry<Item, Integer> e : last.entrySet()) {
                int after = data.getOrDefault(e.getKey(), 0);
                int slot = slotOf(e.getKey());
                if (after < e.getValue() && slot >= 0) {
                    FLIES.add(new Fly(e.getKey(), slot, true, now));
                    sound(SoundEvents.ITEM_PICKUP, 1.2f, 0.35f);
                }
            }
            for (Map.Entry<Item, Integer> e : data.entrySet()) {
                int before = last.getOrDefault(e.getKey(), 0);
                int slot = slotOf(e.getKey());
                if (e.getValue() > before && slot >= 0) {
                    FLIES.add(new Fly(e.getKey(), slot, false, now));
                    sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0f, 0.4f);
                }
            }
        } else if (!data.isEmpty()) {
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.4f);
        }
        last = new HashMap<>(data);
        if (physics) {
            AePile.sync(data);
        }

        List<Entry> list = new ArrayList<>();
        long sum = 0;
        for (Map.Entry<Item, Integer> e : data.entrySet()) {
            list.add(new Entry(e.getKey(), e.getValue()));
            sum += e.getValue();
        }
        list.sort((a, b) -> Integer.compare(b.count(), a.count()));
        kinds = list.size();
        total = sum;
        int maxShown = InsightConfig.AE2_MAX_SHOWN.get();
        shown = list.size() > maxShown ? new ArrayList<>(list.subList(0, maxShown)) : list;
    }

    private static int slotOf(Item item) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).item() == item) {
                return i;
            }
        }
        return -1;
    }

    private static String compact(long n) {
        if (n >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000f);
        }
        if (n >= 1_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", n / 1_000f);
        }
        return Long.toString(n);
    }

    private static Vec3 slotPos(Vec3 origin, Vec3 right, Vec3 up, int i, int rows) {
        int c = i % COLS;
        int r = i / COLS;
        double x = (c - (COLS - 1) / 2.0) * PITCH;
        double y = (rows - 1) / 2.0 * PITCH - r * PITCH;
        return origin.add(right.scale(x)).add(up.scale(y));
    }

    public static void render(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial,
                              MultiBufferSource.BufferSource buffers) {
        if (!visible() || mc.level == null || mc.player == null || shown.isEmpty()) {
            return;
        }
        // 아이템은 실제 월드 공간에 그려지므로, 단말기 옆 공중이 다른 블록(AE 건물 등) 뒤에 있으면 가려져 숫자만 보인다.
        // 장면을 그리기 전에 깊이 버퍼를 비워 항상 월드 위에 보이게 한다(장면 안의 앞뒤 순서는 그대로 유지).
        buffers.endBatch();
        com.mojang.blaze3d.systems.RenderSystem.clear(com.mojang.blaze3d.platform.GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        boolean physics = InsightConfig.AE2_PHYSICS.get();
        float a = prevAnim + (anim - prevAnim) * partial;
        long now = mc.level.getGameTime();
        float time = now + partial;
        Player p = mc.player;
        Vec3 look = p.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 up = new Vec3(0, 1, 0);
        int rows = (shown.size() + COLS - 1) / COLS;
        // 화면 가운데는 단말기 창이 차지하므로, 창 왼쪽 옆 공중에 띄운다
        Vec3 eye = p.getEyePosition(partial);
        Vec3 origin = eye.add(flat.scale(2.6)).add(right.scale(-1.9)).add(0, 0.1 + Math.sin(time * 0.05) * 0.01, 0);
        // 단말기 창 왼쪽의 빈 공간 한가운데에 오도록 화면 좌표에서 거꾸로 위치를 계산한다 (창이 넓어도 가려지지 않게)
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                && acs.getGuiLeft() > 40 && mc.screen.width > 0) {
            org.joml.Matrix4f pm = StorageRenderer.projection();
            float m00 = pm.m00();
            float m11 = pm.m11();
            if (m00 > 0.01f && m11 > 0.01f) {
                double freeNdc = acs.getGuiLeft() * 2.0 / mc.screen.width;
                double nx = acs.getGuiLeft() / (double) mc.screen.width - 1.0;
                double w = physics ? 2.7 : COLS * PITCH + 0.3;
                double h = physics ? 1.9 : rows * PITCH + 0.5;
                double d = Math.max(2.2, Math.max(w * m00 / (freeNdc * 0.9), h * m11 / 1.6));
                d = Math.min(d, 14.0);
                org.joml.Vector3f look3 = camera.getLookVector();
                org.joml.Vector3f left3 = camera.getLeftVector();
                org.joml.Vector3f up3 = camera.getUpVector();
                double rx = nx * d / m00;
                double uy = 0.1 * d / m11;
                origin = camPos.add(look3.x * d - left3.x * rx + up3.x * uy,
                        look3.y * d - left3.y * rx + up3.y * uy,
                        look3.z * d - left3.z * rx + up3.z * uy)
                        .add(0, Math.sin(time * 0.05) * 0.01, 0);
            }
        }
        Vec3 chest = eye.add(0, -0.5, 0).add(flat.scale(0.3));

        if (physics) {
            double tr = Math.toRadians(AePile.TILT);
            Vec3 tiltUp = up.scale(Math.cos(tr)).add(flat.scale(-Math.sin(tr)));
            Vec3 tiltFlat = up.scale(Math.sin(tr)).add(flat.scale(Math.cos(tr)));
            AePile.render(ps, camera, camPos, mc, partial, a, buffers, origin.add(0, 0.2, 0), right, tiltUp, tiltFlat, chest);
            return;
        }

        // 진열대 테두리
        double halfW = COLS * PITCH / 2.0 + 0.12;
        double halfH = rows * PITCH / 2.0 + 0.12;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        int alpha = (int) (150 * a);
        Vec3 c0 = origin.subtract(camPos);
        Vec3 p00 = c0.add(right.scale(-halfW)).add(up.scale(-halfH));
        Vec3 p10 = c0.add(right.scale(halfW)).add(up.scale(-halfH));
        Vec3 p11 = c0.add(right.scale(halfW)).add(up.scale(halfH));
        Vec3 p01 = c0.add(right.scale(-halfW)).add(up.scale(halfH));
        line(lines, pose, p00, p10, alpha);
        line(lines, pose, p10, p11, alpha);
        line(lines, pose, p11, p01, alpha);
        line(lines, pose, p01, p00, alpha);
        buffers.endBatch(RenderType.lines());

        for (int i = 0; i < shown.size(); i++) {
            Entry e = shown.get(i);
            Vec3 pos = slotPos(origin, right, up, i, rows);
            ItemStack stack = StorageRenderer.icon(e.item());
            StorageRenderer.drawIcon(ps, camPos, pos, stack, 0.15f * a, time * 2f + i * 17f, buffers, mc);
            StorageRenderer.drawLabel(ps, camera, camPos, pos.add(up.scale(-0.16)), Component.literal(compact(e.count())),
                    0.011f, 0xFFFFFFFF, PITCH * 0.95, 1);
        }
        // 꺼낸/넣은 아이템: 진열대와 플레이어 사이를 날아간다
        for (Fly f : FLIES) {
            double t = Math.min(1.0, ((now - f.start()) + partial) / FLY_TICKS);
            double ease = 1 - Math.pow(1 - t, 3);
            Vec3 slot = slotPos(origin, right, up, Math.min(f.slot(), shown.size() - 1), rows);
            Vec3 from = f.out() ? slot : chest;
            Vec3 to = f.out() ? chest : slot;
            Vec3 pos = from.add(to.subtract(from).scale(ease)).add(0, Math.sin(t * Math.PI) * 0.25, 0);
            float s = (float) (0.2 * (f.out() ? 1.0 - 0.5 * t : 0.5 + 0.5 * t));
            StorageRenderer.drawIcon(ps, camPos, pos, StorageRenderer.icon(f.item()), s, time * 8f, buffers, mc);
        }
        StorageRenderer.drawLabel(ps, camera, camPos, origin.add(up.scale(halfH + 0.12)),
                Component.translatable("insight_addons.ae.header", kinds, compact(total)), 0.014f, 0xFF59C4F5, 0.0, 4);
        buffers.endBatch();
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 a, Vec3 b, int alpha) {
        Vec3 d = b.subtract(a);
        double len = Math.max(1.0e-4, d.length());
        float nx = (float) (d.x / len);
        float ny = (float) (d.y / len);
        float nz = (float) (d.z / len);
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(89, 196, 245, alpha).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(89, 196, 245, alpha).setNormal(pose, nx, ny, nz);
    }
}
