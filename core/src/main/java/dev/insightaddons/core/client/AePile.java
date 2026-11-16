package dev.insightaddons.core.client;

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
    private static final int MAX_PER_KIND = 24;
    private static final int MAX_KINDS = 64;
    private static final RandomSource RNG = RandomSource.create();
    /** 난장판 남은 틱, 끝난 뒤 제자리로 돌아오는 틱 */
    private static int chaosTicks;
    private static int returnTicks;
    /** 난장판 뒤에는 더미 구역 벽에 갇히지 않고, 날아가 있던 자리(방 전체) 그대로 떨어진다 */
    private static boolean spread;

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
        chaosTicks = 0;
        returnTicks = 0;
        spread = false;
        BODIES.clear();
        PENDING.clear();
        LABEL_COUNT.clear();
        LABEL_ORDER.clear();
    }

    /** 단말기 목록이 갱신될 때마다 호출: 종류별 목표 조각 수에 맞춘다. */
    static void sync(Map<Item, Integer> data) {
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(data.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int kinds = Math.min(MAX_KINDS, sorted.size());
        long sum = 0;
        for (int i = 0; i < kinds; i++) {
            sum += Math.max(0, sorted.get(i).getValue());
        }
        // 조각 수를 실제 보유량에 비례하게 나눈다: 전체 예산(설정 physicsMax * 비율%)을 개수 비율대로 배분하고, 종류마다 최소 1조각
        int cap = InsightConfig.AE2_PHYSICS_MAX.get();
        int budget = Math.max(kinds, (int) Math.round(cap * InsightConfig.AE2_PHYSICS_RATIO.get() / 100.0));
        budget = Math.min(budget, cap);
        Map<Item, Integer> want = new HashMap<>();
        LABEL_COUNT.clear();
        LABEL_ORDER.clear();
        int total = 0;
        for (int i = 0; i < kinds; i++) {
            Map.Entry<Item, Integer> e = sorted.get(i);
            int n = sum <= 0 ? 1 : Math.max(1, (int) Math.round(e.getValue() * (double) budget / sum));
            total += n;
            want.put(e.getKey(), n);
            LABEL_COUNT.put(e.getKey(), e.getValue());
            LABEL_ORDER.add(e.getKey());
        }
        // 반올림으로 예산을 넘으면 가장 많은 종류부터 줄인다
        for (int i = 0; i < kinds && total > cap; i++) {
            Item it = sorted.get(i).getKey();
            int cut = Math.min(want.get(it) - 1, total - cap);
            want.put(it, want.get(it) - cut);
            total -= cut;
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
            AeFx.sound(SoundEvents.ITEM_PICKUP, 1.2f, 0.35f);
        }
    }

    /** 난장판: 약 8초 동안 모든 조각이 방 전체를 빠르게 날아다닌다 */
    static void chaos() {
        if (BODIES.isEmpty()) {
            return;
        }
        chaosTicks = 160;
        returnTicks = 0;
        spread = true;
        for (Body b : BODIES) {
            b.leaving = false;
            b.asleep = false;
            b.still = 0;
            double ang = RNG.nextDouble() * Math.PI * 2;
            double el = (RNG.nextDouble() - 0.5) * 1.2;
            double sp = 0.18 * (InsightConfig.CHAOS_SPEED.get() / 100.0) * (1.0 + (RNG.nextDouble() * 2 - 1) * InsightConfig.CHAOS_RANDOM.get() / 100.0 * 0.9);
            b.vx = Math.cos(ang) * Math.cos(el) * sp;
            b.vz = Math.sin(ang) * Math.cos(el) * sp;
            b.vy = Math.sin(el) * sp + 0.05;
            b.spinYaw = (RNG.nextFloat() - 0.5f) * 50f;
            b.spinTilt = (RNG.nextFloat() - 0.5f) * 40f;
        }
        AeFx.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.6f);
    }

    /** 난장판 동안의 움직임: 중력 없이 방 안(방 좌표 기준 경계)을 튕겨 다닌다 */
    private static void chaosStep() {
        double k = AeRoom.PILE_K;
        double minX = (-3.4 - AeRoom.PILE_X) / k;
        double maxX = (3.4 - AeRoom.PILE_X) / k;
        double minY = 0.1;
        double maxY = (AeRoom.Y1 - AeRoom.Y0 - 0.4) / k;
        double minZ = (0.5 - AeRoom.PILE_Z) / k;
        double maxZ = (4.6 - AeRoom.PILE_Z) / k;
        for (Body b : BODIES) {
            if (b.leaving) {
                b.leaveTick++;
                continue;
            }
            b.px = b.x;
            b.py = b.y;
            b.pz = b.z;
            b.x += b.vx;
            b.y += b.vy;
            b.z += b.vz;
            if (b.x < minX) {
                b.x = minX;
                b.vx = Math.abs(b.vx);
            } else if (b.x > maxX) {
                b.x = maxX;
                b.vx = -Math.abs(b.vx);
            }
            if (b.y < minY) {
                b.y = minY;
                b.vy = Math.abs(b.vy);
            } else if (b.y > maxY) {
                b.y = maxY;
                b.vy = -Math.abs(b.vy);
            }
            if (b.z < minZ) {
                b.z = minZ;
                b.vz = Math.abs(b.vz);
            } else if (b.z > maxZ) {
                b.z = maxZ;
                b.vz = -Math.abs(b.vz);
            }
            // 가끔 방향을 틀어서 더 정신없게 (속력은 그대로)
            if (RNG.nextInt(25) == 0) {
                double sp0 = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
                b.vx += (RNG.nextDouble() - 0.5) * 0.12;
                b.vy += (RNG.nextDouble() - 0.5) * 0.12;
                b.vz += (RNG.nextDouble() - 0.5) * 0.12;
                double sp1 = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
                if (sp1 > 1.0e-6 && sp0 > 1.0e-6) {
                    double k2 = sp0 / sp1;
                    b.vx *= k2;
                    b.vy *= k2;
                    b.vz *= k2;
                }
            }
            b.yaw += b.spinYaw;
            b.tilt += b.spinTilt;
        }
    }

    /** 매 틱: 쏟아 붓기 + 단순 물리 */
    static void step(Minecraft mc) {
        if (chaosTicks > 0) {
            chaosTicks--;
            chaosStep();
            if (chaosTicks == 0) {
                // 끝나면 제자리로 돌아가지 않고, 있던 자리에서 중력으로 그대로 떨어진다 (랜덤 유지)
                returnTicks = 0;
                for (Body b : BODIES) {
                    b.asleep = false;
                    b.still = 0;
                    b.vx *= 0.5;
                    b.vy *= 0.5;
                    b.vz *= 0.5;
                    b.spinYaw *= 0.4f;
                    b.spinTilt *= 0.4f;
                }
            }
            return;
        }
        if (returnTicks > 0) {
            // 난장판이 끝나면 쌓이는 구역 위로 부드럽게 모여 떨어진다
            returnTicks--;
            for (Body b : BODIES) {
                if (b.leaving) {
                    continue;
                }
                b.asleep = false;
                b.still = 0;
                b.vx += (0 - b.x) * 0.02;
                b.vz += (0 - b.z) * 0.02;
                b.vx *= 0.85;
                b.vz *= 0.85;
                b.vy *= 0.85;
            }
        }
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
                b.vx *= 0.985;
                b.vz *= 0.985;
                b.x += b.vx;
                b.y += b.vy;
                b.z += b.vz;
                // 바닥과 벽
                if (b.y < R) {
                    b.y = R;
                    b.vy = -b.vy * 0.06;
                    b.vx *= 0.55;
                    b.vz *= 0.55;
                    b.yaw += b.spinYaw * 0.3f;
                    b.spinYaw *= 0.4f;
                    b.spinTilt *= 0.3f;
                }
                double wx = spread ? (3.4 - AeRoom.PILE_X) / AeRoom.PILE_K : HALF_W;
                double wxn = spread ? (-3.4 - AeRoom.PILE_X) / AeRoom.PILE_K : -HALF_W;
                double wzn = spread ? (0.5 - AeRoom.PILE_Z) / AeRoom.PILE_K : -HALF_D;
                double wz = spread ? (4.6 - AeRoom.PILE_Z) / AeRoom.PILE_K : HALF_D;
                if (b.x < wxn + R) {
                    b.x = wxn + R;
                    b.vx = Math.abs(b.vx) * 0.2;
                } else if (b.x > wx - R) {
                    b.x = wx - R;
                    b.vx = -Math.abs(b.vx) * 0.2;
                }
                if (b.z < wzn + R) {
                    b.z = wzn + R;
                    b.vz = Math.abs(b.vz) * 0.2;
                } else if (b.z > wz - R) {
                    b.z = wz - R;
                    b.vz = -Math.abs(b.vz) * 0.2;
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
                    // 살짝 겹친 정도(쉬고 있는 접촉)는 무시해서 끝없이 꿈틀대지 않게 한다
                    double lim = min * 0.94;
                    if (d2 < lim * lim && d2 > 1.0e-8) {
                        double sa = a.vx * a.vx + a.vy * a.vy + a.vz * a.vz;
                        double sc = c.vx * c.vx + c.vy * c.vy + c.vz * c.vz;
                        // 잠든 조각은 상대가 충분히 빠를 때만 깨운다
                        if (a.asleep && sc < 4.0e-4) {
                            continue;
                        }
                        if (c.asleep && sa < 4.0e-4) {
                            continue;
                        }
                        double d = Math.sqrt(d2);
                        double push = (min - d);
                        double wa = a.asleep ? 0.0 : (c.asleep ? 1.0 : 0.5);
                        double wc = c.asleep ? 0.0 : (a.asleep ? 1.0 : 0.5);
                        double nx = dx / d;
                        double ny = dy / d;
                        double nz = dz / d;
                        a.x -= nx * push * wa;
                        a.y -= ny * push * wa;
                        a.z -= nz * push * wa;
                        c.x += nx * push * wc;
                        c.y += ny * push * wc;
                        c.z += nz * push * wc;
                        if (wa > 0) {
                            a.asleep = false;
                            a.still = 0;
                        }
                        if (wc > 0) {
                            c.asleep = false;
                            c.still = 0;
                        }
                        double rel = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny + (c.vz - a.vz) * nz;
                        if (rel < 0) {
                            double imp = rel * 0.5 * 0.3;
                            a.vx += nx * imp * wa * 2;
                            a.vy += ny * imp * wa * 2;
                            a.vz += nz * imp * wa * 2;
                            c.vx -= nx * imp * wc * 2;
                            c.vy -= ny * imp * wc * 2;
                            c.vz -= nz * imp * wc * 2;
                        }
                    }
                }
            }
        }
        for (Body b : BODIES) {
            if (!b.leaving && !b.asleep) {
                double sp = b.vx * b.vx + b.vy * b.vy + b.vz * b.vz;
                if (sp < 3.0e-4 && Math.abs(b.spinYaw) < 0.8f) {
                    if (++b.still > 6) {
                        b.spinYaw = 0;
                        b.spinTilt = 0;
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
                b.spinYaw *= 0.9f;
                b.spinTilt *= 0.9f;
            } else {
                b.leaveTick++;
            }
        }
        BODIES.removeIf(b -> b.leaving && b.leaveTick > 14);
    }

    /** 더미가 쌓이는 구역(방 바닥)의 반쪽 크기. 방에서 구역 테두리를 그릴 때 쓴다. k=확대 배율 */
    static double halfW(double k) {
        return HALF_W * k;
    }

    static double halfD(double k) {
        return HALF_D * k;
    }

    /**
     * @param floor 더미 구역 바닥 중심(월드 좌표), right/up/flat: 방의 오른쪽/위/앞 방향, k: 물리 상자 -> 방 확대 배율
     */
    static void render(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial, float a,
                       MultiBufferSource.BufferSource buffers, Vec3 floor, Vec3 right, Vec3 up, Vec3 flat, Vec3 chest, double k) {
        drawBodies(ps, camera, camPos, mc, partial, a, buffers, floor, right, up, flat, chest, k);
    }

    private static void drawBodies(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial, float a,
                                   MultiBufferSource.BufferSource buffers, Vec3 floor, Vec3 right, Vec3 up, Vec3 flat, Vec3 chest, double k) {
        for (Body b : BODIES) {
            double t = partial;
            double lx = b.px + (b.x - b.px) * t;
            double ly = b.py + (b.y - b.py) * t;
            double lz = b.pz + (b.z - b.pz) * t;
            Vec3 pos = floor.add(right.scale(lx * k)).add(up.scale(ly * k)).add(flat.scale(lz * k));
            float scale = (float) (0.17f * a * k);
            if (b.leaving) {
                double f = Math.min(1.0, (b.leaveTick + partial) / 14.0);
                double ease = 1 - Math.pow(1 - f, 3);
                pos = pos.add(chest.subtract(pos).scale(ease)).add(0, Math.sin(f * Math.PI) * 0.25, 0);
                scale *= (float) (1.0 - 0.5 * f);
            }
            if (pos.distanceToSqr(camPos) > 40 * 40) {
                continue;
            }
            ps.pushPose();
            ps.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            ps.mulPose(Axis.YP.rotationDegrees(b.yaw));
            ps.mulPose(Axis.XP.rotationDegrees(b.tilt));
            ps.mulPose(Axis.ZP.rotationDegrees(b.roll));
            ps.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(AeDraw.icon(b.item), ItemDisplayContext.FIXED,
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
            AeDraw.drawLabel(ps, camera, camPos, floor.add(up.scale((HEIGHT + 0.12 + (shown - 1 - i) * 0.14) * k)),
                    line, (float) (0.011f * k), 0xFFFFFFFF, 0.0, i == 0 ? 4 : 3);
        }
    }
}
