package dev.insightaddons.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 단말기용 "물리로 쌓이는" 연출(설정에서 켤 때만).
 * 유리 상자 안으로 아이템이 쏟아져 내려 쌓입니다. 종류별 개수에 비례해 아이템 조각 수가 정해지고,
 * 단말기에서 꺼내면 해당 조각이 플레이어에게 날아가고, 넣으면 위에서 새로 떨어집니다.
 * 물리는 아주 단순한 구(球) 충돌이며 클라이언트에서만 계산합니다.
 */
final class AePile {
    private static final double HALF_W = 0.75;
    private static final double HALF_D = 0.5;
    private static final double HEIGHT = 1.25;
    private static final double R = 0.085;
    /** 상자를 플레이어 쪽으로 기울여 바닥 위의 아이템 더미가 위에서 내려다보이게 하는 각도(도) */
    static final float TILT = 18f;
    private static final int MAX_PER_KIND = 24;
    private static final int MAX_KINDS = 64;
    private static final RandomSource RNG = RandomSource.create();

    private static final class Body {
        final Item item;
        double x;
        double y;
        double z;
        double px;
        double py;
        double pz;
        double vx;
        double vy;
        double vz;
        float yaw;
        float tilt;
        float roll;
        float spinYaw;
        float spinTilt;
        boolean leaving;
        boolean asleep;
        int still;
        int leaveTick;

        Body(Item item) {
            this.item = item;
        }
    }

    private static final List<Body> BODIES = new ArrayList<>();
    private static final List<Item> PENDING = new ArrayList<>();
    private static final Map<Item, Integer> LABEL_COUNT = new HashMap<>();
    private static final List<Item> LABEL_ORDER = new ArrayList<>();

    private AePile() {}

    static void clear() {
        BODIES.clear();
        PENDING.clear();
        LABEL_COUNT.clear();
        LABEL_ORDER.clear();
    }

    private static int target(int count, int max) {
        if (count <= 0 || max <= 0) {
            return 0;
        }
        // 개수가 많을수록 조각이 많아지되, 로그로 완만하게 (몇 개든 보이게 하고 많은 건 수북하게)
        double ratio = Math.log(1.0 + count) / Math.log(1.0 + Math.max(1, max));
        return Math.max(1, Math.min(MAX_PER_KIND, (int) Math.round(1 + (MAX_PER_KIND - 1) * ratio * ratio)));
    }

    /** 단말기 목록이 갱신될 때마다 호출: 종류별 목표 조각 수에 맞춘다. */
    static void sync(Map<Item, Integer> data) {
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(data.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int max = sorted.isEmpty() ? 0 : sorted.get(0).getValue();
        Map<Item, Integer> want = new HashMap<>();
        int total = 0;
        LABEL_COUNT.clear();
        LABEL_ORDER.clear();
        for (int i = 0; i < Math.min(MAX_KINDS, sorted.size()); i++) {
            Map.Entry<Item, Integer> e = sorted.get(i);
            int n = target(e.getValue(), max);
            if (total + n > InsightConfig.AE2_PHYSICS_MAX.get()) {
                n = Math.max(0, InsightConfig.AE2_PHYSICS_MAX.get() - total);
            }
            total += n;
            want.put(e.getKey(), n);
            LABEL_COUNT.put(e.getKey(), e.getValue());
            LABEL_ORDER.add(e.getKey());
        }
        // 현재 조각 수 세기
        Map<Item, Integer> have = new HashMap<>();
        for (Body b : BODIES) {
            if (!b.leaving) {
                have.merge(b.item, 1, Integer::sum);
            }
        }
        for (Item p : PENDING) {
            have.merge(p, 1, Integer::sum);
        }
        // 부족하면 위에서 쏟아 붓고, 남으면 플레이어 쪽으로 날려 보낸다
        for (Map.Entry<Item, Integer> e : want.entrySet()) {
            int diff = e.getValue() - have.getOrDefault(e.getKey(), 0);
            for (int i = 0; i < diff; i++) {
                PENDING.add(e.getKey());
            }
        }
        boolean removed = false;
        for (Map.Entry<Item, Integer> e : have.entrySet()) {
            int diff = e.getValue() - want.getOrDefault(e.getKey(), 0);
            for (int i = BODIES.size() - 1; i >= 0 && diff > 0; i--) {
                Body b = BODIES.get(i);
                if (b.item == e.getKey() && !b.leaving) {
                    b.leaving = true;
                    b.leaveTick = 0;
                    diff--;
                    removed = true;
                }
            }
        }
        // 목록에서 사라진 종류
        for (Body b : BODIES) {
            if (!b.leaving && !want.containsKey(b.item)) {
                b.leaving = true;
                removed = true;
            }
        }
        if (removed) {
            for (Body b : BODIES) {
                b.asleep = false;
                b.still = 0;
            }
            Fx.sound(SoundEvents.ITEM_PICKUP, 1.2f, 0.35f);
        }
    }

    /** 매 틱: 쏟아 붓기 + 단순 물리 */
    static void step(Minecraft mc) {
        // 한 틱에 최대 3개씩 위에서 떨어뜨린다
        for (int i = 0; i < 8 && !PENDING.isEmpty(); i++) {
            Item item = PENDING.remove(PENDING.size() - 1);
            Body b = new Body(item);
            b.x = (RNG.nextDouble() - 0.5) * HALF_W * 1.4;
            b.z = (RNG.nextDouble() - 0.5) * HALF_D * 1.2;
            b.y = HEIGHT + 0.1 + RNG.nextDouble() * 0.3;
            b.px = b.x;
            b.py = b.y;
            b.pz = b.z;
            b.vx = (RNG.nextDouble() - 0.5) * 0.02;
            b.vz = (RNG.nextDouble() - 0.5) * 0.02;
            b.yaw = RNG.nextFloat() * 360f;
            b.tilt = RNG.nextFloat() * 90f - 45f;
            b.roll = RNG.nextFloat() * 90f - 45f;
            b.spinYaw = (RNG.nextFloat() - 0.5f) * 14f;
            b.spinTilt = (RNG.nextFloat() - 0.5f) * 10f;
            BODIES.add(b);
        }
        int n = BODIES.size();
        for (int sub = 0; sub < 2; sub++) {
            for (Body b : BODIES) {
                if (b.leaving || b.asleep) {
                    continue;
                }
                if (sub == 0) {
                    b.px = b.x;
                    b.py = b.y;
                    b.pz = b.z;
                }
                b.vy -= 0.022;
                b.vx *= 0.995;
                b.vz *= 0.995;
                b.x += b.vx;
                b.y += b.vy;
                b.z += b.vz;
                // 바닥과 벽
                if (b.y < R) {
                    b.y = R;
                    b.vy = -b.vy * 0.28;
                    b.vx *= 0.8;
                    b.vz *= 0.8;
                    b.yaw += b.spinYaw * 0.3f;
                    b.spinYaw *= 0.7f;
                    b.spinTilt *= 0.5f;
                }
                if (b.x < -HALF_W + R) {
                    b.x = -HALF_W + R;
                    b.vx = Math.abs(b.vx) * 0.4;
                } else if (b.x > HALF_W - R) {
                    b.x = HALF_W - R;
                    b.vx = -Math.abs(b.vx) * 0.4;
                }
                if (b.z < -HALF_D + R) {
                    b.z = -HALF_D + R;
                    b.vz = Math.abs(b.vz) * 0.4;
                } else if (b.z > HALF_D - R) {
                    b.z = HALF_D - R;
                    b.vz = -Math.abs(b.vz) * 0.4;
                }
            }
            // 서로 겹치면 밀어낸다 (구 충돌, O(n^2) 이지만 조각 수가 적다)
            for (int i = 0; i < n; i++) {
                Body a = BODIES.get(i);
                if (a.leaving) {
                    continue;
                }
                for (int j = i + 1; j < n; j++) {
                    Body c = BODIES.get(j);
                    if (c.leaving || (a.asleep && c.asleep)) {
                        continue;
                    }
                    double dx = c.x - a.x;
                    double dy = c.y - a.y;
                    double dz = c.z - a.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    double min = R * 1.9;
                    if (d2 < min * min && d2 > 1.0e-8) {
                        double d = Math.sqrt(d2);
                        double push = (min - d) * 0.5;
                        double nx = dx / d;
                        double ny = dy / d;
                        double nz = dz / d;
                        a.x -= nx * push;
                        a.y -= ny * push;
                        a.z -= nz * push;
                        c.x += nx * push;
                        c.y += ny * push;
                        c.z += nz * push;
                        a.asleep = false;
                        c.asleep = false;
                        a.still = 0;
                        c.still = 0;
                        double rel = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny + (c.vz - a.vz) * nz;
                        if (rel < 0) {
                            double imp = rel * 0.5 * 0.7;
                            a.vx += nx * imp;
                            a.vy += ny * imp;
                            a.vz += nz * imp;
                            c.vx -= nx * imp;
                            c.vy -= ny * imp;
                            c.vz -= nz * imp;
                        }
                    }
                }
            }
        }
        for (Body b : BODIES) {
            if (!b.leaving && !b.asleep) {
                double sp = b.vx * b.vx + b.vy * b.vy + b.vz * b.vz;
                if (sp < 1.0e-4 && Math.abs(b.spinYaw) < 0.3f) {
                    if (++b.still > 20) {
                        b.asleep = true;
                        b.px = b.x;
                        b.py = b.y;
                        b.pz = b.z;
                        b.vx = 0;
                        b.vy = 0;
                        b.vz = 0;
                    }
                } else {
                    b.still = 0;
                }
                b.yaw += b.spinYaw;
                b.tilt += b.spinTilt;
                b.spinYaw *= 0.97f;
                b.spinTilt *= 0.97f;
            } else {
                b.leaveTick++;
            }
        }
        BODIES.removeIf(b -> b.leaving && b.leaveTick > 14);
    }

    static void render(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial, float a,
                       MultiBufferSource.BufferSource buffers, Vec3 origin, Vec3 right, Vec3 up, Vec3 flat, Vec3 chest) {
        // 바닥: ME 제어기, 양쪽 벽: ME 드라이브 모델 (월드 블록이 아니라 모델만 빌려 그린다)
        Vec3 floorPos = origin.add(up.scale(-HEIGHT / 2));
        if (AeBox.render(ps, mc, buffers, camPos, floorPos, right, TILT)) {
            drawBodies(ps, camera, camPos, mc, partial, a, buffers, origin, right, up, flat, chest);
            return;
        }
        // 유리 상자 테두리 (가상 상자를 못 그릴 때의 대체)
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        int alpha = (int) (170 * a);
        Vec3 base = origin.add(up.scale(-HEIGHT / 2)).subtract(camPos);
        double[][] corners = new double[8][];
        int k = 0;
        for (int yy = 0; yy < 2; yy++) {
            for (int zz = 0; zz < 2; zz++) {
                for (int xx = 0; xx < 2; xx++) {
                    corners[k++] = new double[]{xx == 0 ? -HALF_W : HALF_W, yy == 0 ? 0 : HEIGHT, zz == 0 ? -HALF_D : HALF_D};
                }
            }
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            Vec3 p0 = base.add(right.scale(corners[e[0]][0])).add(up.scale(corners[e[0]][1])).add(flat.scale(corners[e[0]][2]));
            Vec3 p1 = base.add(right.scale(corners[e[1]][0])).add(up.scale(corners[e[1]][1])).add(flat.scale(corners[e[1]][2]));
            Vec3 d = p1.subtract(p0);
            double len = Math.max(1.0e-4, d.length());
            lines.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).setColor(89, 196, 245, alpha)
                    .setNormal(pose, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
            lines.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).setColor(89, 196, 245, alpha)
                    .setNormal(pose, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
        }
        buffers.endBatch(RenderType.lines());
        drawBodies(ps, camera, camPos, mc, partial, a, buffers, origin, right, up, flat, chest);
    }

    private static void drawBodies(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial, float a,
                                   MultiBufferSource.BufferSource buffers, Vec3 origin, Vec3 right, Vec3 up, Vec3 flat, Vec3 chest) {
        Vec3 floor = origin.add(up.scale(-HEIGHT / 2));
        for (Body b : BODIES) {
            double t = partial;
            double lx = b.px + (b.x - b.px) * t;
            double ly = b.py + (b.y - b.py) * t;
            double lz = b.pz + (b.z - b.pz) * t;
            Vec3 pos = floor.add(right.scale(lx)).add(up.scale(ly)).add(flat.scale(lz));
            float scale = 0.17f * a;
            if (b.leaving) {
                double f = Math.min(1.0, (b.leaveTick + partial) / 14.0);
                double ease = 1 - Math.pow(1 - f, 3);
                pos = pos.add(chest.subtract(pos).scale(ease)).add(0, Math.sin(f * Math.PI) * 0.25, 0);
                scale *= (float) (1.0 - 0.5 * f);
            }
            if (pos.distanceToSqr(camPos) > 24 * 24) {
                continue;
            }
            ps.pushPose();
            ps.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            ps.mulPose(Axis.YP.rotationDegrees(b.yaw));
            ps.mulPose(Axis.XP.rotationDegrees(b.tilt));
            ps.mulPose(Axis.ZP.rotationDegrees(b.roll));
            ps.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(StorageRenderer.icon(b.item), ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
            ps.popPose();
        }
        buffers.endBatch();

        // 많이 든 종류부터 이름과 개수를 상자 위에 한 줄씩
        int shown = Math.min(6, LABEL_ORDER.size());
        for (int i = 0; i < shown; i++) {
            Item item = LABEL_ORDER.get(i);
            Component line = new ItemStack(item).getHoverName().copy()
                    .append(Component.literal(" ×" + LABEL_COUNT.getOrDefault(item, 0)));
            StorageRenderer.drawLabel(ps, camera, camPos, floor.add(up.scale(HEIGHT + 0.12 + (shown - 1 - i) * 0.14)),
                    line, 0.011f, 0xFFFFFFFF, 0.0, i == 0 ? 4 : 3);
        }
    }
}
