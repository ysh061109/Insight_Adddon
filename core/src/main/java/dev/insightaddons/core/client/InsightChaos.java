package dev.insightaddons.core.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/**
 * InSight Chests가 보관함 안에 쌓아 둔 3D 물리 아이템을 "난장판"으로 날려 보냅니다.
 * 시뮬레이션의 tick을 믹신으로 가로채서, 난장판 동안은 중력 없이 상자 안을 튕겨 다니게 하고
 * 끝나면 그 자리에서 InSight의 원래 물리(중력)로 떨어지게 둡니다. 전부 리플렉션이라 InSight가 없거나 바뀌어도 안전합니다.
 */
public final class InsightChaos {
    private static final Random RNG = new Random();
    private static Object lastSim;
    private static long lastTickMs;
    private static Object chaosSim;
    private static int ticks;
    private static boolean broken;

    private static Method mGetItems;
    private static Field fBounds;
    private static Field fMinX, fMaxX, fMinY, fMaxY, fMinZ, fMaxZ;
    private static Field fX, fY, fZ, fPX, fPY, fPZ, fVX, fVY, fVZ, fRadius, fRot, fAxis, fSpin, fSleep, fSleepTicks;

    private InsightChaos() {}

    private static void init(Object sim) throws Exception {
        if (fX != null) {
            return;
        }
        Class<?> sc = sim.getClass();
        mGetItems = sc.getMethod("getItems");
        Object bounds = sc.getMethod("getBounds").invoke(sim);
        Class<?> bc = bounds.getClass();
        fMinX = bc.getField("minX");
        fMaxX = bc.getField("maxX");
        fMinY = bc.getField("minY");
        fMaxY = bc.getField("maxY");
        fMinZ = bc.getField("minZ");
        fMaxZ = bc.getField("maxZ");
        Class<?> ic = Class.forName("com.mrsun.insightchests.core.physics.CorePhysicsItem", false, sc.getClassLoader());
        fX = ic.getField("x");
        fY = ic.getField("y");
        fZ = ic.getField("z");
        fPX = ic.getField("prevX");
        fPY = ic.getField("prevY");
        fPZ = ic.getField("prevZ");
        fVX = ic.getField("vx");
        fVY = ic.getField("vy");
        fVZ = ic.getField("vz");
        fRadius = ic.getField("radius");
        fRot = ic.getField("rotation");
        fAxis = ic.getField("spinAxis");
        fSpin = ic.getField("spinSpeed");
        fSleep = ic.getField("isSleeping");
        fSleepTicks = ic.getField("sleepTicks");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> items(Object sim) throws Exception {
        return (List<Object>) mGetItems.invoke(sim);
    }

    /** 지금 InSight가 쌓은 아이템을 틱 중인 보관함이 있는가 */
    public static boolean available() {
        return !broken && lastSim != null && System.currentTimeMillis() - lastTickMs < 400;
    }

    public static boolean running() {
        return chaosSim != null && ticks > 0;
    }

    public static boolean start() {
        if (!available()) {
            return false;
        }
        try {
            init(lastSim);
            for (Object it : items(lastSim)) {
                fSleep.setBoolean(it, false);
                fSleepTicks.setInt(it, 0);
                double ang = RNG.nextDouble() * Math.PI * 2;
                double el = (RNG.nextDouble() - 0.5) * 1.2;
                double sp = 0.035 + RNG.nextDouble() * 0.05;
                fVX.setFloat(it, (float) (Math.cos(ang) * Math.cos(el) * sp));
                fVZ.setFloat(it, (float) (Math.sin(ang) * Math.cos(el) * sp));
                fVY.setFloat(it, (float) (Math.sin(el) * sp + 0.02));
                fSpin.setFloat(it, 8f + RNG.nextFloat() * 22f);
                Vector3f axis = (Vector3f) fAxis.get(it);
                axis.set(RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f);
                if (axis.lengthSquared() < 1e-4f) {
                    axis.set(0, 1, 0);
                }
                axis.normalize();
            }
            chaosSim = lastSim;
            ticks = 160;
            return true;
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }

    public static void stop() {
        chaosSim = null;
        ticks = 0;
    }

    /** 믹신에서 호출: 난장판 중이면 직접 움직이고 true(원래 tick 취소). */
    public static boolean tick(Object sim) {
        if (broken) {
            return false;
        }
        lastSim = sim;
        lastTickMs = System.currentTimeMillis();
        if (sim != chaosSim || ticks <= 0) {
            return false;
        }
        try {
            ticks--;
            Object bounds = sim.getClass().getMethod("getBounds").invoke(sim);
            float minX = fMinX.getFloat(bounds), maxX = fMaxX.getFloat(bounds);
            float minY = fMinY.getFloat(bounds), maxY = fMaxY.getFloat(bounds);
            float minZ = fMinZ.getFloat(bounds), maxZ = fMaxZ.getFloat(bounds);
            boolean end = ticks == 0;
            for (Object it : items(sim)) {
                float r = fRadius.getFloat(it);
                float x = fX.getFloat(it), y = fY.getFloat(it), z = fZ.getFloat(it);
                fPX.setFloat(it, x);
                fPY.setFloat(it, y);
                fPZ.setFloat(it, z);
                float vx = fVX.getFloat(it), vy = fVY.getFloat(it), vz = fVZ.getFloat(it);
                x += vx;
                y += vy;
                z += vz;
                if (x < minX + r) {
                    x = minX + r;
                    vx = Math.abs(vx);
                } else if (x > maxX - r) {
                    x = maxX - r;
                    vx = -Math.abs(vx);
                }
                if (y < minY + r) {
                    y = minY + r;
                    vy = Math.abs(vy);
                } else if (y > maxY - r) {
                    y = maxY - r;
                    vy = -Math.abs(vy);
                }
                if (z < minZ + r) {
                    z = minZ + r;
                    vz = Math.abs(vz);
                } else if (z > maxZ - r) {
                    z = maxZ - r;
                    vz = -Math.abs(vz);
                }
                if (RNG.nextInt(25) == 0) {
                    vx += (RNG.nextFloat() - 0.5f) * 0.03f;
                    vy += (RNG.nextFloat() - 0.5f) * 0.03f;
                    vz += (RNG.nextFloat() - 0.5f) * 0.03f;
                }
                if (end) {
                    // 끝: 속도를 줄이고 깨워서, 그 자리에서 InSight 물리(중력)로 떨어지게 한다
                    vx *= 0.5f;
                    vy *= 0.5f;
                    vz *= 0.5f;
                    fSleep.setBoolean(it, false);
                    fSleepTicks.setInt(it, 0);
                }
                fX.setFloat(it, x);
                fY.setFloat(it, y);
                fZ.setFloat(it, z);
                fVX.setFloat(it, vx);
                fVY.setFloat(it, vy);
                fVZ.setFloat(it, vz);
                Quaternionf q = (Quaternionf) fRot.get(it);
                q.rotateAxis((float) Math.toRadians(fSpin.getFloat(it)), (Vector3f) fAxis.get(it));
            }
            if (end) {
                chaosSim = null;
            }
            return true;
        } catch (Throwable t) {
            broken = true;
            chaosSim = null;
            return false;
        }
    }
}
