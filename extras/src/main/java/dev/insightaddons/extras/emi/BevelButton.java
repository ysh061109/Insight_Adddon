package dev.insightaddons.extras.emi;

import net.minecraft.client.gui.GuiGraphics;

/** EMI 레시피 화면 버튼의 배경: 바닐라 버튼처럼 위·왼쪽은 밝고 아래·오른쪽은 어두운 입체 테두리. */
final class BevelButton {
    private BevelButton() {}

    static void draw(GuiGraphics g, int x, int y, int size, boolean hover, boolean on) {
        int base = on ? 0xFF3B6EA8 : hover ? 0xFF6A6A78 : 0xFF4A4A56;
        int light = on ? 0xFF7FB2E8 : hover ? 0xFFC8C8D8 : 0xFF9A9AA8;
        int dark = on ? 0xFF1E3A5F : 0xFF22222A;
        // 바깥 윤곽
        g.fill(x, y, x + size, y + size, 0xFF101014);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, base);
        // 위·왼쪽 하이라이트, 아래·오른쪽 그림자
        g.fill(x + 1, y + 1, x + size - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + size - 1, light);
        g.fill(x + 2, y + size - 2, x + size - 1, y + size - 1, dark);
        g.fill(x + size - 2, y + 2, x + size - 1, y + size - 1, dark);
        if (hover) {
            g.fill(x, y, x + size, y + 1, 0xFFFFFFFF);
            g.fill(x, y + size - 1, x + size, y + size, 0xFFFFFFFF);
            g.fill(x, y, x + 1, y + size, 0xFFFFFFFF);
            g.fill(x + size - 1, y, x + size, y + size, 0xFFFFFFFF);
        }
    }
}
