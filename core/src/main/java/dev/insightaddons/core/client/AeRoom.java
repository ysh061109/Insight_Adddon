package dev.insightaddons.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * AE2 단말기를 열었을 때 플레이어를 둘러싸는 SF 서버룸. 흰 패널 벽, 보라색 네온, 양쪽 벽의 서버 랙, 바닥 중앙의 발광 패드.
 * 텍스처 없이 색 면으로만 직접 그립니다. 카메라가 방 한가운데에 있고, 방은 카메라를 기준으로 그려집니다.
 * 방 좌표계: x=오른쪽, y=위(카메라 눈높이가 0), z=앞.
 */
final class AeRoom {
    static final double X0 = -4.0;
    static final double X1 = 4.0;
    static final double Y0 = -1.62;
    static final double Y1 = 2.0;
    static final double Z0 = -5.0;
    static final double Z1 = 5.0;
    /** 아이템 더미가 쌓이는 곳(바닥 중심)의 방 좌표 */
    static final double PILE_X = 0.0;
    static final double PILE_Z = 3.0;
    /** 더미 구역의 확대 배율 (물리 상자 -> 방) */
    static final double PILE_K = 2.0;

    /** ME 제어기(납작한 패드) 위에 아이템이 얹히도록 더미 바닥을 올리는 높이 */
    static final double PAD_LIFT = 0.13;

    /** 제어기 윗면 높이: 바닥 홈에 묻힌 진짜 케이블 모드에서는 바닥과 같은 높이(조금만 띄움), 아니면 납작 패드 두께 */
    static double padLift() {
        return AeBlocks.busReady() ? 0.02 : PAD_LIFT;
    }

    private static final double E = 0.004;

    /** 케이블을 묻는 바닥 홈(xa, xb, za, zb)과 깊이. 홈 부분은 바닥을 파내고 그 아래에 케이블을 둔다 */
    static final double TRENCH_D = 1.0;
    /** {xa, xb, za, zb, 옆면 생략 비트(1=xa,2=xb,4=za,8=zb)}: 제어기·다른 홈과 맞닿은 면은 벽을 그리지 않는다 */
    static final double[][] TRENCH = {
            {-0.5, 0.5, Z0, 2.0, 8},
            {-4.0, -1.5, 2.5, 3.5, 2},
            {1.5, 4.0, 2.5, 3.5, 1},
            {-3.5, 3.5, 4.0, Z1, 4},
            {-1.5, 1.5, 2.0, 4.0, 15}, // ME 제어기가 들어앉는 구덩이
    };





    /** 방 좌표 -> 카메라 기준 좌표 */
    record Frame(Vec3 right, Vec3 up, Vec3 fwd, double s) {
        Vec3 rel(double x, double y, double z) {
            return right.scale(x * s).add(up.scale(y * s)).add(fwd.scale(z * s));
        }
    }

    private AeRoom() {}

    private static int hash(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    /** 사각형 [xa,xb]x[za,zb]에서 홈 사각형들을 뺀 나머지를 그린다 */
    private static void flatMinus(Ctx c, double y, double xa, double xb, double za, double zb, int col, int al, double[][] holes) {
        java.util.List<double[]> rects = new java.util.ArrayList<>();
        rects.add(new double[]{xa, xb, za, zb});
        if (holes != null) {
            for (double[] h : holes) {
                java.util.List<double[]> next = new java.util.ArrayList<>();
                for (double[] r : rects) {
                    if (h[1] <= r[0] || h[0] >= r[1] || h[3] <= r[2] || h[2] >= r[3]) {
                        next.add(r);
                        continue;
                    }
                    double ix0 = Math.max(r[0], h[0]);
                    double ix1 = Math.min(r[1], h[1]);
                    if (r[0] < ix0) next.add(new double[]{r[0], ix0, r[2], r[3]});
                    if (ix1 < r[1]) next.add(new double[]{ix1, r[1], r[2], r[3]});
                    if (r[2] < h[2]) next.add(new double[]{ix0, ix1, r[2], h[2]});
                    if (h[3] < r[3]) next.add(new double[]{ix0, ix1, h[3], r[3]});
                }
                rects = next;
            }
        }
        for (double[] r : rects) {
            c.flatY(y, r[0], r[1], r[2], r[3], col, al);
        }
    }

    private static final class Ctx {
        final VertexConsumer vc;
        final Matrix4f m;
        final Frame f;
        final float a;

        Ctx(VertexConsumer vc, Matrix4f m, Frame f, float a) {
            this.vc = vc;
            this.m = m;
            this.f = f;
            this.a = a;
        }

        void v(double x, double y, double z, int r, int g, int b, int al) {
            Vec3 p = f.rel(x, y, z);
            vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Math.max(0, Math.min(255, (int) (al * a))));
        }

        /** y=고정 평면 (바닥/천장) */
        void flatY(double y, double xa, double xb, double za, double zb, int c, int al) {
            int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            v(xa, y, za, r, g, b, al);
            v(xb, y, za, r, g, b, al);
            v(xb, y, zb, r, g, b, al);
            v(xa, y, zb, r, g, b, al);
        }

        /** x=고정 평면 (옆 벽) */
        void wallX(double x, double ya, double yb, double za, double zb, int c, int al) {
            int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            v(x, ya, za, r, g, b, al);
            v(x, yb, za, r, g, b, al);
            v(x, yb, zb, r, g, b, al);
            v(x, ya, zb, r, g, b, al);
        }

        /** z=고정 평면 (앞 벽) */
        void wallZ(double z, double xa, double xb, double ya, double yb, int c, int al) {
            int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            v(xa, ya, z, r, g, b, al);
            v(xb, ya, z, r, g, b, al);
            v(xb, yb, z, r, g, b, al);
            v(xa, yb, z, r, g, b, al);
        }
    }

    private static int shade(int c, int d) {
        int r = Math.max(0, Math.min(255, (c >> 16 & 255) + d));
        int g = Math.max(0, Math.min(255, (c >> 8 & 255) + d));
        int b = Math.max(0, Math.min(255, (c & 255) + d));
        return r << 16 | g << 8 | b;
    }

    private static final int SEAM = 0x4C4C58;
    private static final int PANEL = 0xC4C4CC;
    private static final int CYAN = 0x3CD2F0;

    /** 가장 최근에 그린 아이템 더미 구역의 반쪽 크기 (제어기를 이 구역에 맞춰 깔 때 쓴다) */
    static double lastHalfW = 1.5;
    static double lastHalfD = 1.0;

    static void render(PoseStack ps, MultiBufferSource.BufferSource buffers, Frame f, float a, float time,
                       double arenaHalfW, double arenaHalfD, boolean blocks) {
        lastHalfW = arenaHalfW;
        lastHalfD = arenaHalfD;
        Ctx c = new Ctx(buffers.getBuffer(ModRenderTypes.QUADS_DEPTH), ps.last().pose(), f, a);
        floor(c, arenaHalfW, arenaHalfD, blocks);
        ceiling(c);
        frontWall(c, blocks);
        sideWall(c, X0, 1, time, blocks);
        sideWall(c, X1, -1, time, blocks);
        buffers.endBatch(ModRenderTypes.QUADS_DEPTH);
    }

    private static void floor(Ctx c, double arenaHalfW, double arenaHalfD, boolean blocks) {
        double[][] holes = blocks && AeBlocks.busReady() ? TRENCH : null;
        flatMinus(c, Y0, X0, X1, Z0, Z1, SEAM, 255, holes);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 10; j++) {
                int d = (hash(i, j, 1) % 13) - 6;
                flatMinus(c, Y0 + E, X0 + i + 0.04, X0 + i + 0.96, Z0 + j + 0.04, Z0 + j + 0.96, shade(PANEL, d), 255, holes);
            }
        }
        if (holes != null) {
            // 바닥을 판 홈: 바닥면과 옆면 (케이블이 이 안에 놓인다)
            double yb = Y0 - TRENCH_D;
            for (double[] h : holes) {
                c.flatY(yb, h[0], h[1], h[2], h[3], 0x16161E, 255);
                int skip = (int) h[4];
                if ((skip & 1) == 0) c.wallX(h[0], yb, Y0, h[2], h[3], 0x2E2E38, 255);
                if ((skip & 2) == 0) c.wallX(h[1], yb, Y0, h[2], h[3], 0x2E2E38, 255);
                if ((skip & 4) == 0) c.wallZ(h[2], h[0], h[1], yb, Y0, 0x2A2A34, 255);
                if ((skip & 8) == 0) c.wallZ(h[3], h[0], h[1], yb, Y0, 0x2A2A34, 255);
            }
        }
        // 보라색 네온 십자 (세로줄은 방 중앙, 가로줄은 앞쪽 z=3)
        double zc = 3.0;
        // 중앙 발광 패드 (겹친 네모 고리). ME 제어기 블록을 깔 수 있으면 그것이 대신한다
        for (int k = 0; k < (blocks ? 0 : 3); k++) {
            double h = 0.85 - k * 0.3;
            double w = 0.07;
            double y = Y0 + (5 + k) * E;
            int col = k == 1 ? 0x2A3038 : CYAN;
            c.flatY(y, -h, h, zc - h, zc - h + w, col, 255);
            c.flatY(y, -h, h, zc + h - w, zc + h, col, 255);
            c.flatY(y, -h, -h + w, zc - h, zc + h, col, 255);
            c.flatY(y, h - w, h, zc - h, zc + h, col, 255);
        }
        if (!blocks) {
            c.flatY(Y0 + 8 * E, -0.22, 0.22, zc - 0.22, zc + 0.22, 0x1C2026, 255);
        }
        // 아이템 더미 구역 표시: 청록 테두리와 옅은 바탕
        double ax = PILE_X;
        double az = PILE_Z;
        double y = blocks ? Y0 + padLift() + 2 * E : Y0 + 6 * E;
        int line = blocks ? AeBlocks.controllerColor() : CYAN; // ME 제어기 색이 바뀌면 테두리도 따라 바뀐다
        if (!blocks) {
            c.flatY(y, ax - arenaHalfW, ax + arenaHalfW, az - arenaHalfD, az + arenaHalfD, line, 40);
        }
        double t = 0.05;
        c.flatY(y + E, ax - arenaHalfW, ax + arenaHalfW, az - arenaHalfD, az - arenaHalfD + t, line, 230);
        c.flatY(y + E, ax - arenaHalfW, ax + arenaHalfW, az + arenaHalfD - t, az + arenaHalfD, line, 230);
        c.flatY(y + E, ax - arenaHalfW, ax - arenaHalfW + t, az - arenaHalfD, az + arenaHalfD, line, 230);
        c.flatY(y + E, ax + arenaHalfW - t, ax + arenaHalfW, az - arenaHalfD, az + arenaHalfD, line, 230);
    }

    private static void ceiling(Ctx c) {
        c.flatY(Y1, X0, X1, Z0, Z1, SEAM, 255);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 10; j++) {
                int d = (hash(i, j, 2) % 11) - 5;
                c.flatY(Y1 - E, X0 + i + 0.04, X0 + i + 0.96, Z0 + j + 0.04, Z0 + j + 0.96, shade(0x9A9AA6, d), 255);
            }
        }
        // 천장 조명 (보라 테두리 + 밝은 면)
        double zc = 3.0;
        c.flatY(Y1 - 3 * E, -0.62, 0.62, zc - 0.62, zc + 0.62, 0xF4EEFF, 255);
        // 천장 모서리 네온
    }

    private static void frontWall(Ctx c, boolean blocks) {
        c.wallZ(Z1, X0, X1, Y0, Y1, SEAM, 255);
        double rowH = (Y1 - Y0) / 3.0;
        for (int i = 0; i < 8; i++) {
            for (int r = 0; r < 3; r++) {
                int d = (hash(i, r, 3) % 13) - 6;
                c.wallZ(Z1 - E, X0 + i + 0.04, X0 + i + 0.96, Y0 + r * rowH + 0.04, Y0 + (r + 1) * rowH - 0.04, shade(PANEL, d), 255);
            }
        }
        // 가운데 세로선은 어두운 면 대신 AE2 케이블(AeBlocks)로 그린다. 블록을 못 그릴 때만 어두운 기둥으로 대체
        if (!blocks) {
            c.wallZ(Z1 - 2 * E, -0.28, 0.28, Y0, Y1, 0x241C3C, 255);
        }
        // 위/아래 네온 띠
    }

    /** dir=1: 왼쪽 벽(안쪽이 +x 방향), dir=-1: 오른쪽 벽 */
    private static void sideWall(Ctx c, double x, int dir, float time, boolean blocks) {
        c.wallX(x, Y0, Y1, Z0, Z1, SEAM, 255);
        double rowH = (Y1 - Y0) / 3.0;
        double xi = x + dir * E;
        for (int j = 0; j < 10; j++) {
            for (int r = 0; r < 3; r++) {
                int d = (hash(j, r, 4 + dir) % 13) - 6;
                c.wallX(xi, Y0 + r * rowH + 0.04, Y0 + (r + 1) * rowH - 0.04, Z0 + j + 0.04, Z0 + j + 0.96, shade(PANEL, d), 255);
            }
        }
        if (!blocks) {
            racks(c, x, dir, time);
        }
        // 위/아래 네온 띠
    }

    /** ME 드라이브 블록을 못 그릴 때의 대체: 색 면으로 그린 서버 랙 */
    private static void racks(Ctx c, double x, int dir, float time) {
        double ry0 = -0.55;
        double ry1 = 0.95;
        double xr = x + dir * 3 * E;
        c.wallX(xr, ry0 - 0.06, ry1 + 0.06, Z0 + 0.3, Z1 - 0.3, 0x2A2C32, 255);
        double mw = (Z1 - Z0 - 0.8) / 5.0;
        for (int m = 0; m < 5; m++) {
            double za = Z0 + 0.4 + m * mw + 0.05;
            double zb = Z0 + 0.4 + (m + 1) * mw - 0.05;
            c.wallX(x + dir * 4 * E, ry0, ry1, za, zb, 0x3A3D44, 255);
            double uh = (ry1 - ry0 - 0.1) / 4.0;
            for (int u = 0; u < 4; u++) {
                double ya = ry0 + 0.05 + u * uh + 0.03;
                double yb = ry0 + 0.05 + (u + 1) * uh - 0.03;
                c.wallX(x + dir * 5 * E, ya, yb, za + 0.06, zb - 0.06, 0x1C1E22, 255);
                c.wallX(x + dir * 6 * E, ya + 0.02, yb - 0.02, zb - 0.55 * mw, zb - 0.1, 0x30333A, 255);
                int hv = hash(m, u, 7 + dir);
                int led = switch (hv % 8) {
                    case 0 -> 0x2F78FF;
                    case 1 -> 0xFF9A1E;
                    case 2 -> 0xFF3A2E;
                    default -> 0x5CFF3C;
                };
                // 일부 LED는 천천히 깜빡인다
                double blink = (hv % 5 == 0) ? 0.55 + 0.45 * Math.sin(time * 0.12 + hv) : 1.0;
                c.wallX(x + dir * 7 * E, ya + 0.03, yb - 0.03, za + 0.1, za + 0.1 + (yb - ya - 0.06),
                        shade(led, (int) (-60 * (1 - blink))), 255);
            }
        }
    }
}
