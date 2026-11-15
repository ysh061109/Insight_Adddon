package dev.insightaddons.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;

/**
 * AE2 단말기 창을 Insight Chests Plus의 배낭/통 화면과 같은 투명한 유리로 그리는 도우미.
 * 색과 모양(어두운 유리 + 흰 테두리 + 슬롯 칸)은 Plus의 GlassSkin과 같은 값을 씁니다.
 */
public final class AeGlass {
    private static Field searchField;
    private static boolean searchTried;

    private AeGlass() {}

    public static boolean on() {
        return InsightConfig.AE2_GLASS.get() && InsightConfig.AE2_PHYSICS.get();
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, argb(InsightConfig.AE2_GLASS_ALPHA.get(), 0x141428));
        g.renderOutline(x, y, w, h, 0x45FFFFFF);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x18FFFFFF);
    }

    public static void cell(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + 17, y + 17, argb(InsightConfig.AE2_GLASS_SLOT_ALPHA.get(), 0xFFFFFF));
        g.renderOutline(x, y, 18, 18, 0x36FFFFFF);
    }

    /** 단말기 배경 대신 유리 패널과 모든 슬롯 칸을 그린다. 검색창은 원래 배경 그리기 안에서 그려지므로 같이 그려 준다. */
    public static void draw(AbstractContainerScreen<?> s, GuiGraphics g, int left, int top, int mx, int my, float pt) {
        panel(g, left, top, s.getXSize(), s.getYSize());
        for (Slot slot : s.getMenu().slots) {
            if (slot.isActive()) {
                cell(g, left + slot.x - 1, top + slot.y - 1);
            }
        }
        try {
            if (!searchTried) {
                searchTried = true;
                for (Class<?> c = s.getClass(); c != null && searchField == null; c = c.getSuperclass()) {
                    try {
                        searchField = c.getDeclaredField("searchField");
                        searchField.setAccessible(true);
                    } catch (NoSuchFieldException ignored) {
                        // 상위 클래스에서 찾는다
                    }
                }
            }
            if (searchField != null && searchField.get(s) instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                w.render(g, mx, my, pt);
            }
        } catch (Throwable ignored) {
            // 검색창을 못 그려도 나머지는 정상
        }
    }

    /** AE2의 어두운 회색 글씨를 유리 위에서 읽히는 밝은 색으로 */
    public static int recolor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (r < 0x70 && g < 0x70 && b < 0x70) {
            return (color & 0xFF000000) | 0x00E6EAF5;
        }
        return color;
    }
}
