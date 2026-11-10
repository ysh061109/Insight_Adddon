package dev.insightaddons.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 목록 설정(대상 블록, 렌치 도구, 모드 ID)을 편집하는 화면.
 * 왼쪽에는 지금 목록(아이콘 + 이름 + ID, 클릭하면 삭제)이, 오른쪽에는 검색창과 전체 등록 목록이 있어서
 * 검색해서 클릭만 하면 추가됩니다. Shift+클릭은 그 모드 전체(`모드:*`)를 추가합니다.
 * 바라보는 블록이나 손에 든 아이템을 한 번에 추가하는 버튼도 있습니다. 바꾼 내용은 바로 설정 파일에 저장됩니다.
 */
public final class ListEditorScreen extends Screen {
    public enum Kind { BLOCK, ITEM, MODID }

    private static final int ROW_H = 22;
    private static final int MARGIN = 12;

    private final Screen parent;
    private final ModConfigSpec.ConfigValue<List<? extends String>> value;
    private final Kind kind;
    private final List<String> entries = new ArrayList<>();
    private final List<ResourceLocation> catalog = new ArrayList<>();
    private List<ResourceLocation> results = new ArrayList<>();
    private EditBox search;
    private double leftScroll;
    private double rightScroll;
    private Component status = Component.empty();
    private int statusColor = 0xA0A0A0;
    private int leftX, leftW, rightX, rightW, listTop, listBottom;

    @SuppressWarnings("unchecked")
    public ListEditorScreen(Screen parent, ModConfigSpec.ConfigValue<?> value, Kind kind, Component title) {
        super(title);
        this.parent = parent;
        this.value = (ModConfigSpec.ConfigValue<List<? extends String>>) value;
        this.kind = kind;
        for (String s : this.value.get()) {
            entries.add(s);
        }
        buildCatalog();
    }

    private void buildCatalog() {
        List<ResourceLocation> all = new ArrayList<>();
        switch (kind) {
            case BLOCK -> BuiltInRegistries.BLOCK.keySet().forEach(rl -> {
                if (BuiltInRegistries.BLOCK.get(rl).asItem() != Items.AIR) {
                    all.add(rl);
                }
            });
            case ITEM -> all.addAll(BuiltInRegistries.ITEM.keySet());
            case MODID -> ModList.get().getMods().forEach(m -> all.add(ResourceLocation.fromNamespaceAndPath(m.getModId(), "-")));
        }
        all.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        catalog.addAll(all);
        results = new ArrayList<>(catalog);
    }

    @Override
    protected void init() {
        leftX = MARGIN;
        int total = width - MARGIN * 3;
        leftW = total / 2;
        rightX = MARGIN * 2 + leftW;
        rightW = total - leftW;
        listTop = 56;
        listBottom = height - 40;

        search = new EditBox(font, rightX, 32, rightW, 18, Component.translatable("insight_addons.configuration.listEditor.search"));
        search.setHint(Component.translatable("insight_addons.configuration.listEditor.search"));
        search.setResponder(this::refilter);
        addRenderableWidget(search);
        setInitialFocus(search);

        int by = height - 28;
        int bw = Math.max(60, (width - MARGIN * 2 - 12) / 4);
        int x = MARGIN;
        if (kind != Kind.MODID) {
            addRenderableWidget(Button.builder(Component.translatable(kind == Kind.BLOCK
                    ? "insight_addons.configuration.listEditor.addLooking" : "insight_addons.configuration.listEditor.addHeldOnly"),
                    b -> addLooking()).bounds(x, by, bw, 20).build());
            x += bw + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("insight_addons.configuration.listEditor.addHeld"),
                b -> addHeld()).bounds(x, by, bw, 20).build());
        x += bw + 4;
        addRenderableWidget(Button.builder(Component.translatable("insight_addons.configuration.listEditor.reset"),
                b -> resetDefault()).bounds(x, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> onClose()).bounds(width - MARGIN - bw, by, bw, 20).build());
    }

    private void refilter(String text) {
        String q = text.toLowerCase(Locale.ROOT).trim();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation rl : catalog) {
            if (q.isEmpty() || matches(rl, q)) {
                out.add(rl);
                if (out.size() >= 600) {
                    break;
                }
            }
        }
        results = out;
        rightScroll = 0;
    }

    private boolean matches(ResourceLocation rl, String q) {
        if (rl.toString().contains(q)) {
            return true;
        }
        return label(rl).getString().toLowerCase(Locale.ROOT).contains(q);
    }

    private Component label(ResourceLocation rl) {
        if (kind == Kind.MODID) {
            return ModList.get().getModContainerById(rl.getNamespace())
                    .map(c -> Component.literal(c.getModInfo().getDisplayName())).orElse(Component.literal(rl.getNamespace()));
        }
        return stackOf(rl).getHoverName();
    }

    private ItemStack stackOf(ResourceLocation rl) {
        if (kind == Kind.BLOCK) {
            Block b = BuiltInRegistries.BLOCK.get(rl);
            return b == null ? ItemStack.EMPTY : new ItemStack(b.asItem());
        }
        if (kind == Kind.ITEM) {
            Item i = BuiltInRegistries.ITEM.get(rl);
            return i == null ? ItemStack.EMPTY : new ItemStack(i);
        }
        return ItemStack.EMPTY;
    }

    private String idOf(ResourceLocation rl) {
        return kind == Kind.MODID ? rl.getNamespace() : rl.toString();
    }

    // ── 목록 편집 ──

    private void commit() {
        value.set(new ArrayList<>(entries));
        value.save();
    }

    private void add(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (entries.contains(id)) {
            say("insight_addons.configuration.listEditor.exists", 0xFFD166, id);
            return;
        }
        entries.add(id);
        commit();
        say("insight_addons.configuration.listEditor.added", 0x7CFC9A, id);
        leftScroll = Double.MAX_VALUE;
    }

    private void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            String removed = entries.remove(index);
            commit();
            say("insight_addons.configuration.listEditor.removed", 0xFF8A8A, removed);
        }
    }

    private void resetDefault() {
        entries.clear();
        for (String s : value.getDefault()) {
            entries.add(s);
        }
        commit();
        leftScroll = 0;
        say("insight_addons.configuration.listEditor.resetDone", 0x7CFC9A, "");
    }

    private void addLooking() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock());
            add(id.toString());
        } else {
            say("insight_addons.configuration.listEditor.noLooking", 0xFF8A8A, "");
        }
    }

    private void addHeld() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (held.isEmpty()) {
            say("insight_addons.configuration.listEditor.noHeld", 0xFF8A8A, "");
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        switch (kind) {
            case BLOCK -> {
                Block b = Block.byItem(held.getItem());
                if (b == net.minecraft.world.level.block.Blocks.AIR) {
                    say("insight_addons.configuration.listEditor.notBlock", 0xFF8A8A, "");
                } else {
                    add(BuiltInRegistries.BLOCK.getKey(b).toString());
                }
            }
            case ITEM -> add(id.toString());
            case MODID -> add(id.getNamespace());
        }
    }

    private void say(String key, int color, String arg) {
        status = Component.translatable(key, arg);
        statusColor = color;
    }

    // ── 그리기 ──

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawString(font, Component.translatable("insight_addons.configuration.listEditor.current", entries.size()), leftX, 38, 0xCFE8FF);
        g.fill(leftX - 1, listTop - 1, leftX + leftW + 1, listBottom + 1, 0xFF000000);
        g.fill(leftX, listTop, leftX + leftW, listBottom, 0xC0141820);
        g.fill(rightX - 1, listTop - 1, rightX + rightW + 1, listBottom + 1, 0xFF000000);
        g.fill(rightX, listTop, rightX + rightW, listBottom, 0xC0141820);

        // 왼쪽: 현재 목록
        g.enableScissor(leftX, listTop, leftX + leftW, listBottom);
        clampScroll(0);
        for (int i = 0; i < entries.size(); i++) {
            int y = listTop + i * ROW_H - (int) leftScroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean hover = mx >= leftX && mx < leftX + leftW && my >= Math.max(y, listTop) && my < Math.min(y + ROW_H, listBottom);
            if (hover) {
                g.fill(leftX, y, leftX + leftW, y + ROW_H, 0x40FFFFFF);
            }
            drawEntry(g, leftX, y, leftW - 18, entries.get(i));
            boolean xHover = hover && mx >= leftX + leftW - 18;
            g.drawString(font, "✕", leftX + leftW - 13, y + 7, xHover ? 0xFFFF5555 : 0xFF999999);
        }
        g.disableScissor();
        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("insight_addons.configuration.listEditor.empty"), leftX + leftW / 2, listTop + 20, 0x808080);
        }

        // 오른쪽: 검색 결과
        g.enableScissor(rightX, listTop, rightX + rightW, listBottom);
        clampScroll(1);
        for (int i = 0; i < results.size(); i++) {
            int y = listTop + i * ROW_H - (int) rightScroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            ResourceLocation rl = results.get(i);
            boolean hover = mx >= rightX && mx < rightX + rightW && my >= Math.max(y, listTop) && my < Math.min(y + ROW_H, listBottom);
            boolean has = entries.contains(idOf(rl));
            if (hover) {
                g.fill(rightX, y, rightX + rightW, y + ROW_H, 0x40FFFFFF);
            }
            drawCatalogRow(g, rightX, y, rightW, rl, has);
        }
        g.disableScissor();
        if (results.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("insight_addons.configuration.listEditor.noResult"), rightX + rightW / 2, listTop + 20, 0x808080);
        }

        g.drawString(font, Component.translatable("insight_addons.configuration.listEditor.hint"), rightX, listBottom + 4, 0x909090);
        if (!status.getString().isEmpty()) {
            g.drawString(font, status, leftX, listBottom + 4, statusColor);
        }
        super.render(g, mx, my, pt);
    }

    private void drawEntry(GuiGraphics g, int x, int y, int w, String id) {
        boolean wildcard = id.contains("*");
        ResourceLocation rl = id.contains(":") && !wildcard ? ResourceLocation.tryParse(id) : null;
        ItemStack stack = ItemStack.EMPTY;
        Component name;
        int color = 0xFFFFFF;
        if (kind == Kind.MODID) {
            name = ModList.get().getModContainerById(id).map(c -> (Component) Component.literal(c.getModInfo().getDisplayName()))
                    .orElse(Component.translatable("insight_addons.configuration.listEditor.notInstalled", id));
            if (ModList.get().getModContainerById(id).isEmpty()) {
                color = 0xA0A0A0;
            }
        } else if (wildcard) {
            stack = new ItemStack(Items.CHEST);
            name = Component.translatable("insight_addons.configuration.listEditor.wildcard", id.contains(":") ? id.substring(0, id.indexOf(':')) : id);
            color = 0xC792EA;
        } else if (rl != null && stackOf(rl).isEmpty() == false) {
            stack = stackOf(rl);
            name = stack.getHoverName();
        } else {
            name = Component.translatable("insight_addons.configuration.listEditor.notInstalled", id);
            color = 0xA0A0A0;
        }
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 3, y + 3);
        }
        g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 28), x + 24, y + 3, color);
        g.drawString(font, font.plainSubstrByWidth(id, w - 28), x + 24, y + 13, 0xFF808080);
    }

    private void drawCatalogRow(GuiGraphics g, int x, int y, int w, ResourceLocation rl, boolean has) {
        ItemStack stack = stackOf(rl);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 3, y + 3);
        }
        Component name = label(rl);
        g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 50), x + 24, y + 3, has ? 0xFF7CFC9A : 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(idOf(rl), w - 50), x + 24, y + 13, 0xFF808080);
        if (has) {
            g.drawString(font, "✔", x + w - 14, y + 7, 0xFF7CFC9A);
        }
    }

    private void clampScroll(int side) {
        int count = side == 0 ? entries.size() : results.size();
        double max = Math.max(0, count * ROW_H - (listBottom - listTop));
        if (side == 0) {
            leftScroll = Math.max(0, Math.min(leftScroll, max));
        } else {
            rightScroll = Math.max(0, Math.min(rightScroll, max));
        }
    }

    // ── 입력 ──

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (my >= listTop && my < listBottom) {
            if (mx < rightX - 6) {
                leftScroll -= sy * ROW_H * 2;
            } else {
                rightScroll -= sy * ROW_H * 2;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != 0 || my < listTop || my >= listBottom) {
            return false;
        }
        if (mx >= leftX && mx < leftX + leftW) {
            int i = (int) ((my - listTop + leftScroll) / ROW_H);
            if (mx >= leftX + leftW - 18 && i >= 0 && i < entries.size()) {
                remove(i);
                return true;
            }
        } else if (mx >= rightX && mx < rightX + rightW) {
            int i = (int) ((my - listTop + rightScroll) / ROW_H);
            if (i >= 0 && i < results.size()) {
                ResourceLocation rl = results.get(i);
                if (kind == Kind.MODID) {
                    add(rl.getNamespace());
                } else if (hasShiftDown()) {
                    add(rl.getNamespace() + ":*");
                } else {
                    add(rl.toString());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
