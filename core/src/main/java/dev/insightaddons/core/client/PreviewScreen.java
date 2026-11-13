package dev.insightaddons.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 설정 미리보기: 유리 화면(투명도, 슬롯 농도)과 3D 아이템 크기 배율을 2D 견본으로 보여줍니다.
 * 아래 슬라이더를 움직이면 견본이 바로 바뀌고, 값은 바로 설정 파일에 저장됩니다.
 * (실제 3D 장면과 완전히 같지는 않고 느낌을 보는 용도입니다.)
 */
public final class PreviewScreen extends Screen {
    private final Screen parent;

    public PreviewScreen(Screen parent) {
        super(Component.translatable("insight_addons.configuration.preview.title"));
        this.parent = parent;
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final ModConfigSpec.IntValue cfg;
        private final int min, max;
        private final String key;

        IntSlider(int x, int y, int w, String key, ModConfigSpec.IntValue cfg, int min, int max) {
            super(x, y, w, 20, Component.empty(), (cfg.get() - min) / (double) (max - min));
            this.cfg = cfg;
            this.min = min;
            this.max = max;
            this.key = key;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, min + (int) Math.round(value * (max - min))));
        }

        @Override
        protected void applyValue() {
            cfg.set(min + (int) Math.round(value * (max - min)));
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            cfg.save();
        }
    }

    private static final class ScaleSlider extends AbstractSliderButton {
        private final ModConfigSpec.DoubleValue cfg;
        private final double min, max;

        ScaleSlider(int x, int y, int w, ModConfigSpec.DoubleValue cfg, double min, double max) {
            super(x, y, w, 20, Component.empty(), (cfg.get() - min) / (max - min));
            this.cfg = cfg;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        private double current() {
            return Math.round((min + value * (max - min)) * 100) / 100.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("insight_addons.configuration.preview.scale", current()));
        }

        @Override
        protected void applyValue() {
            cfg.set(current());
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            cfg.save();
        }
    }

    @Override
    protected void init() {
        int w = Math.min(180, width / 2 - 20);
        int y = height - 52;
        addRenderableWidget(new IntSlider(width / 2 - w - 5, y, w, "insight_addons.configuration.preview.alpha",
                InsightConfig.GLASS_ALPHA, 0, 255));
        addRenderableWidget(new IntSlider(width / 2 + 5, y, w, "insight_addons.configuration.preview.slotAlpha",
                InsightConfig.GLASS_SLOT_ALPHA, 0, 255));
        addRenderableWidget(new ScaleSlider(width / 2 - w - 5, y + 24, w, InsightConfig.ITEM_SCALE, 0.3, 2.0));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 5, y + 24, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        // 뒤 배경(투명도가 보이도록 줄무늬)
        int px = width / 2 - 110, py = 28, pw = 220, ph = 92;
        for (int i = 0; i < pw; i += 10) {
            g.fill(px + i, py, px + i + 10, py + ph, ((i / 10) % 2 == 0) ? 0xFF3A6EA5 : 0xFF8FB87A);
        }
        int alpha = InsightConfig.GLASS_ALPHA.get();
        if (alpha < 0) {
            alpha = 150;
        }
        g.fill(px, py, px + pw, py + ph, (alpha << 24) | 0x101820);
        int slotAlpha = InsightConfig.GLASS_SLOT_ALPHA.get();
        ItemStack[] sample = {new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.EMERALD), new ItemStack(Items.REDSTONE), new ItemStack(Items.COAL)};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int sx = px + 8 + c * 23, sy = py + 8 + r * 27;
                g.fill(sx, sy, sx + 20, sy + 20, (slotAlpha << 24) | 0xFFFFFF);
                int idx = r * 9 + c;
                if (idx < sample.length) {
                    g.renderItem(sample[idx], sx + 2, sy + 2);
                }
            }
        }
        g.drawCenteredString(font, Component.translatable("insight_addons.configuration.preview.glass"), width / 2, py + ph + 3, 0xA0C8FF);

        // 3D 아이템 크기 견본
        double base = InsightConfig.ITEM_SCALE.get();
        double[] mult = {1.0, InsightConfig.ITEM_SCALE_BACKPACK.get(), InsightConfig.ITEM_SCALE_SHULKER.get(), InsightConfig.ITEM_SCALE_BARREL.get()};
        String[] names = {"basic", "backpack", "shulker", "barrel"};
        boolean glow = !"off".equals(InsightConfig.ITEM_GLOW.get());
        int cy = py + ph + 22;
        int cellW = 56;
        int startX = width / 2 - cellW * 2;
        for (int i = 0; i < 4; i++) {
            double s = Math.max(0.1, base * mult[i]);
            int cx = startX + i * cellW + cellW / 2;
            if (glow) {
                int rad = (int) (11 * s) + 4;
                for (int k = 3; k >= 1; k--) {
                    int a = 0x18 * (4 - k);
                    g.fill(cx - rad * k / 3, cy + 16 - rad * k / 3, cx + rad * k / 3, cy + 16 + rad * k / 3, (a << 24) | 0x55AAFF);
                }
            }
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy + 16, 0);
            pose.scale((float) s, (float) s, 1f);
            g.renderItem(new ItemStack(Items.DIAMOND), -8, -8);
            pose.popPose();
            g.drawCenteredString(font, Component.translatable("insight_addons.configuration.preview.kind." + names[i]), cx, cy + 34, 0xFFFFFF);
            g.drawCenteredString(font, String.format("x%.2f", s), cx, cy + 45, 0xA0A0A0);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
