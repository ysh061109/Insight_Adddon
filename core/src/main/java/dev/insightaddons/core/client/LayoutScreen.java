package dev.insightaddons.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 보관함을 열 때 나오는 조작 버튼(3D 표시 비율, 창 투명도, 난장판, 난장판 속도/랜덤, HUD 숨김)의 위치를 직접 끌어서 옮기고
 * 버튼마다 표시/숨김을 고르는 화면. 오른쪽 미리보기는 일반 보관함(창 아래 2열 격자)의 기본 배치를 보여 주고,
 * 옮긴 양은 "기본 위치 기준 이동량"으로 저장되어 AE2, 인벤토리 등 모든 화면의 기본 위치에 같은 양만큼 적용된다.
 */
public final class LayoutScreen extends Screen {
    private static final int COL_W = 84;
    private static final int CELL_H = 14;

    private final Screen parent;
    private final double[] dx = new double[InsightConfig.LAYOUT_IDS.length];
    private final double[] dy = new double[InsightConfig.LAYOUT_IDS.length];
    private int dragging = -1;
    private float f;
    private int px, py, guiW, guiH;

    public LayoutScreen(Screen parent) {
        super(Component.translatable("insight_addons.configuration.buttonLayout.title"));
        this.parent = parent;
        for (int i = 0; i < dx.length; i++) {
            dx[i] = InsightConfig.LAYOUT_DX[i].get();
            dy[i] = InsightConfig.LAYOUT_DY[i].get();
        }
    }

    private Component name(int i) {
        return Component.translatable("insight_addons.configuration.layout." + InsightConfig.LAYOUT_IDS[i] + ".name");
    }

    @Override
    protected void init() {
        guiW = width;
        guiH = height;
        f = Math.min(0.62f, Math.max(0.3f, (width - 200) / (float) width));
        px = width - Math.round(guiW * f) - 8;
        py = 30;
        int y = 36;
        for (int i = 0; i < InsightConfig.LAYOUT_IDS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(showText(i), b -> {
                InsightConfig.LAYOUT_SHOW[idx].set(!InsightConfig.LAYOUT_SHOW[idx].get());
                InsightConfig.LAYOUT_SHOW[idx].save();
                b.setMessage(showText(idx));
            }).bounds(10, y, Math.max(120, Math.min(170, px - 20)), 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("insight_addons.configuration.layout.reset"), b -> {
            for (int i = 0; i < dx.length; i++) {
                dx[i] = 0;
                dy[i] = 0;
                InsightConfig.LAYOUT_DX[i].set(0);
                InsightConfig.LAYOUT_DY[i].set(0);
                InsightConfig.LAYOUT_SHOW[i].set(true);
                InsightConfig.LAYOUT_DX[i].save();
                InsightConfig.LAYOUT_DY[i].save();
                InsightConfig.LAYOUT_SHOW[i].save();
            }
            rebuildWidgets();
        }).bounds(10, y + 6, Math.max(120, Math.min(170, px - 20)), 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    private Component showText(int i) {
        boolean on = InsightConfig.LAYOUT_SHOW[i].get();
        return Component.empty().append(on ? Component.literal("✔ ").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.literal("✘ ").withStyle(net.minecraft.ChatFormatting.RED)).append(name(i));
    }

    /** 미리보기에서 버튼 i가 놓이는 사각형(실제 GUI 좌표) */
    private int[] rect(int i) {
        int winL = (guiW - 176) / 2;
        int winB = (guiH - 166) / 2 + 166;
        int x = winL + (i % 2) * (COL_W + 2) + (int) Math.round(dx[i]);
        int y = winB + 3 + (i / 2) * (CELL_H + 2) + (int) Math.round(dy[i]);
        return new int[]{x, y, x + COL_W, y + CELL_H};
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        bgDone = false;
        renderBackground(g, mx, my, pt);
        bgDone = true;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawString(font, Component.translatable("insight_addons.configuration.layout.hint"), 10, 22, 0xA0A0A0);
        super.render(g, mx, my, pt);

        // 미리보기: 화면 전체를 f배로 줄여서 그린다
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(f, f, 1f);
        g.fill(0, 0, guiW, guiH, 0xFF10131A);
        int winL = (guiW - 176) / 2;
        int winT = (guiH - 166) / 2;
        g.fill(winL, winT, winL + 176, winT + 166, 0xFF2A3140);
        g.renderOutline(winL, winT, 176, 166, 0x66FFFFFF);
        g.drawCenteredString(font, Component.translatable("insight_addons.configuration.layout.window"), winL + 88, winT + 78, 0x8890A0);
        for (int i = 0; i < dx.length; i++) {
            if (!InsightConfig.LAYOUT_SHOW[i].get()) {
                continue;
            }
            int[] r = rect(i);
            boolean hover = i == dragging || (dragging < 0 && inside(i, mx, my));
            g.fill(r[0], r[1], r[2], r[3], hover ? 0xFFE0A060 : 0xFFC08040);
            g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], 0xFF5A3A18);
            g.drawCenteredString(font, name(i), (r[0] + r[2]) / 2, r[1] + 3, 0xFFFFFF);
        }
        g.pose().popPose();
        g.drawString(font, Component.translatable("insight_addons.configuration.layout.previewLabel"), px, py - 10, 0xA0A0A0);
    }

    private boolean inside(int i, double mx, double my) {
        double ax = (mx - px) / f;
        double ay = (my - py) / f;
        int[] r = rect(i);
        return ax >= r[0] && ax < r[2] && ay >= r[1] && ay < r[3];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = dx.length - 1; i >= 0; i--) {
                if (InsightConfig.LAYOUT_SHOW[i].get() && inside(i, mx, my)) {
                    dragging = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (dragging >= 0 && button == 0) {
            dx[dragging] = Math.max(-1500, Math.min(1500, dx[dragging] + ddx / f));
            dy[dragging] = Math.max(-1500, Math.min(1500, dy[dragging] + ddy / f));
            return true;
        }
        return super.mouseDragged(mx, my, button, ddx, ddy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0 && button == 0) {
            int i = dragging;
            dragging = -1;
            InsightConfig.LAYOUT_DX[i].set((int) Math.round(dx[i]));
            InsightConfig.LAYOUT_DY[i].set((int) Math.round(dy[i]));
            InsightConfig.LAYOUT_DX[i].save();
            InsightConfig.LAYOUT_DY[i].save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private boolean bgDone;

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (!bgDone) {
            super.renderBackground(g, mx, my, pt);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
