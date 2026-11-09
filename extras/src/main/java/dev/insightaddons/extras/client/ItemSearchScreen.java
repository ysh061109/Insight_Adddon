package dev.insightaddons.extras.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 보관함 검색: 아이템 이름을 입력하고 고르면, 주변 보관함 중 그 아이템이 든 곳이 3D로 표시됩니다.
 * (JEI/EMI "이 아이템 찾기" 버튼과 같은 표시를 쓰므로 휠 선택, 꺼내기 등이 그대로 됩니다.)
 */
public final class ItemSearchScreen extends Screen {
    private static final int ROWS = 8;
    private static List<String> names;
    private static List<Item> items;

    private EditBox box;
    private final List<Item> results = new ArrayList<>();

    public ItemSearchScreen() {
        super(Component.translatable("insight_addons.search.title"));
    }

    private static void index() {
        if (names != null) {
            return;
        }
        names = new ArrayList<>();
        items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String n = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            names.add(n + "\u0000" + BuiltInRegistries.ITEM.getKey(item).getPath());
            items.add(item);
        }
    }

    @Override
    protected void init() {
        index();
        box = new EditBox(font, width / 2 - 110, height / 2 - 90, 220, 18, Component.translatable("insight_addons.search.title"));
        box.setMaxLength(40);
        box.setHint(Component.translatable("insight_addons.search.hint"));
        box.setResponder(s -> refresh());
        addRenderableWidget(box);
        setInitialFocus(box);
        refresh();
    }

    private void refresh() {
        results.clear();
        String q = box.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return;
        }
        List<Item> starts = new ArrayList<>();
        List<Item> contains = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String[] parts = names.get(i).split("\u0000", 2);
            if (parts[0].startsWith(q)) {
                starts.add(items.get(i));
            } else if (parts[0].contains(q) || parts[1].contains(q)) {
                contains.add(items.get(i));
            }
        }
        starts.addAll(contains);
        for (int i = 0; i < Math.min(ROWS, starts.size()); i++) {
            results.add(starts.get(i));
        }
    }

    private void pick(Item item) {
        // 같은 버튼이 이미 켜져 있으면 끄는 동작이라서, 먼저 꺼서 항상 새로 시작한다
        RecipeFocus.clear();
        onClose();
        RecipeFocus.toggleItem(item);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !results.isEmpty()) {
            pick(results.get(0));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = width / 2 - 110;
        int y = height / 2 - 66;
        for (int i = 0; i < results.size(); i++) {
            if (mx >= x && mx <= x + 220 && my >= y + i * 20 && my < y + i * 20 + 20) {
                pick(results.get(i));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 108, 0xFFFFFF);
        int x = width / 2 - 110;
        int y = height / 2 - 66;
        for (int i = 0; i < results.size(); i++) {
            int rowY = y + i * 20;
            boolean hover = mx >= x && mx <= x + 220 && my >= rowY && my < rowY + 20;
            g.fill(x, rowY, x + 220, rowY + 20, hover ? 0xAA3A3A55 : 0x88000000);
            ItemStack stack = new ItemStack(results.get(i));
            g.renderItem(stack, x + 2, rowY + 2);
            g.drawString(font, stack.getHoverName(), x + 24, rowY + 6, i == 0 ? 0xFFFFD84A : 0xFFFFFF, true);
        }
        if (!results.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("insight_addons.search.enter"), width / 2,
                    y + results.size() * 20 + 6, 0xFFB0B0B0);
        } else if (!box.getValue().isBlank()) {
            g.drawCenteredString(font, Component.translatable("insight_addons.search.none"), width / 2, y + 6, 0xFFFF6B6B);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
