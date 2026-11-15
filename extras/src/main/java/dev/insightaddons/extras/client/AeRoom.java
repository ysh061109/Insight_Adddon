package dev.insightaddons.extras.client;

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
    static final double PILE_X = -1.9;
    static final double PILE_Z = 2.6;
    /** 더미 구역의 확대 배율 (물리 상자 -> 방) */
    static final double PILE_K = 2.0;

    private static final double E = 0.004;

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
    private static final int NEON = 0xA85AFF;
    private static final int NEON_CORE = 0xE0C4FF;
    private static final int CYAN = 0x3CD2F0;

    static void render(PoseStack ps, MultiBufferSource.BufferSource buffers, Frame f, float a, float time,
                       double arenaHalfW, double arenaHalfD, boolean blocks) {
        Ctx c = new Ctx(buffers.getBuffer(ModRenderTypes.QUADS_DEPTH), ps.last().pose(), f, a);
        floor(c, arenaHalfW, arenaHalfD, blocks);
        ceiling(c);
        frontWall(c);
        sideWall(c, X0, 1, time, blocks);
        sideWall(c, X1, -1, time, blocks);
        buffers.endBatch(ModRenderTypes.QUADS_DEPTH);
    }

    private static void floor(Ctx c, double arenaHalfW, double arenaHalfD, boolean blocks) {
        c.flatY(Y0, X0, X1, Z0, Z1, SEAM, 255);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 10; j++) {
                int d = (hash(i, j, 1) % 13) - 6;
                c.flatY(Y0 + E, X0 + i + 0.04, X0 + i + 0.96, Z0 + j + 0.04, Z0 + j + 0.96, shade(PANEL, d), 255);
            }
        }
        // 보라색 네온 십자 (세로줄은 방 중앙, 가로줄은 앞쪽 z=3)
        double zc = 3.0;
        c.flatY(Y0 + 2 * E, -0.3, 0.3, Z0, Z1, NEON, 70);
        c.flatY(Y0 + 2 * E, X0, X1, zc - 0.3, zc + 0.3, NEON, 70);
        c.flatY(Y0 + 3 * E, -0.1, 0.1, Z0, Z1, NEON, 255);
        c.flatY(Y0 + 3 * E, X0, X1, zc - 0.1, zc + 0.1, NEON, 255);
        c.flatY(Y0 + 4 * E, -0.03, 0.03, Z0, Z1, NEON_CORE, 255);
        c.flatY(Y0 + 4 * E, X0, X1, zc - 0.03, zc + 0.03, NEON_CORE, 255);
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
        double y = Y0 + 6 * E;
        c.flatY(y, ax - arenaHalfW, ax + arenaHalfW, az - arenaHalfD, az + arenaHalfD, CYAN, 40);
        double t = 0.05;
        c.flatY(y + E, ax - arenaHalfW, ax + arenaHalfW, az - arenaHalfD, az - arenaHalfD + t, CYAN, 230);
        c.flatY(y + E, ax - arenaHalfW, ax + arenaHalfW, az + arenaHalfD - t, az + arenaHalfD, CYAN, 230);
        c.flatY(y + E, ax - arenaHalfW, ax - arenaHalfW + t, az - arenaHalfD, az + arenaHalfD, CYAN, 230);
        c.flatY(y + E, ax + arenaHalfW - t, ax + arenaHalfW, az - arenaHalfD, az + arenaHalfD, CYAN, 230);
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
        c.flatY(Y1 - 2 * E, -0.8, 0.8, zc - 0.8, zc + 0.8, NEON, 255);
        c.flatY(Y1 - 3 * E, -0.62, 0.62, zc - 0.62, zc + 0.62, 0xF4EEFF, 255);
        // 천장 모서리 네온
        c.flatY(Y1 - 2 * E, X0 + 0.05, X0 + 0.25, Z0, Z1, NEON, 255);
        c.flatY(Y1 - 2 * E, X1 - 0.25, X1 - 0.05, Z0, Z1, NEON, 255);
        c.flatY(Y1 - 2 * E, X0, X1, Z1 - 0.25, Z1 - 0.05, NEON, 255);
    }

    private static void frontWall(Ctx c) {
        c.wallZ(Z1, X0, X1, Y0, Y1, SEAM, 255);
        double rowH = (Y1 - Y0) / 3.0;
        for (int i = 0; i < 8; i++) {
            for (int r = 0; r < 3; r++) {
                int d = (hash(i, r, 3) % 13) - 6;
                c.wallZ(Z1 - E, X0 + i + 0.04, X0 + i + 0.96, Y0 + r * rowH + 0.04, Y0 + (r + 1) * rowH - 0.04, shade(PANEL, d), 255);
            }
        }
        // 가운데 세로 네온 기둥 + 노드
        c.wallZ(Z1 - 2 * E, -0.28, 0.28, Y0, Y1, 0x241C3C, 255);
        c.wallZ(Z1 - 3 * E, -0.1, 0.1, Y0, Y1, NEON, 255);
        c.wallZ(Z1 - 4 * E, -0.03, 0.03, Y0, Y1, NEON_CORE, 255);
        for (int k = 0; k < 3; k++) {
            double yc = Y0 + (k + 0.5) * rowH;
            c.wallZ(Z1 - 5 * E, -0.2, 0.2, yc - 0.2, yc + 0.2, 0x3A3A52, 255);
            c.wallZ(Z1 - 6 * E, -0.09, 0.09, yc - 0.09, yc + 0.09, 0xB8E4FF, 255);
        }
        // 위/아래 네온 띠
        c.wallZ(Z1 - 2 * E, X0, X1, Y1 - 0.22, Y1 - 0.12, NEON, 255);
        c.wallZ(Z1 - 2 * E, X0, X1, Y0 + 0.12, Y0 + 0.22, NEON, 160);
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
        c.wallX(x + dir * 2 * E, Y1 - 0.22, Y1 - 0.12, Z0, Z1, NEON, 255);
        c.wallX(x + dir * 2 * E, Y0 + 0.12, Y0 + 0.22, Z0, Z1, NEON, 160);
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
