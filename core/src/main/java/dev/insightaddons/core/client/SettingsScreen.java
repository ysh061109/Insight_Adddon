package dev.insightaddons.core.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 직접 만든 설정 화면: 왼쪽에 카테고리 탭, 위에 검색창, 가운데에 스크롤되는 설정 목록.
 * 검색은 항목 이름·설명·키를 모두 훑고, 탭은 "전체"와 섹션별로 나뉩니다.
 * 항목은 InsightConfig.SPEC에서 자동으로 읽으므로 항목을 추가해도 이 파일은 고칠 필요가 없습니다.
 */
public final class SettingsScreen extends Screen {
    private int tabW = 118;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 18;
    private int ctrlW = 150;

    private static final String ALL = "";

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Component> sectionNames = new LinkedHashMap<>();
    private final List<ModConfigSpec.ConfigValue<?>> touched = new ArrayList<>();
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();

    private String tab = ALL;
    private String query = "";
    private EditBox search;
    private double scroll;
    private int listX, listY, listW, listH;

    private record Placed(Entry entry, int offset) {}

    private static final class Entry {
        final String section;
        final String key;
        final Component label;
        final String haystack;
        final String comment;
        final ModConfigSpec.ConfigValue<?> value;
        final int kind; // 0 bool, 1 int, 2 double, 3 cycle, 4 text, 5 list, 6 preview

        Entry(String section, String key, Component label, String comment, ModConfigSpec.ConfigValue<?> value, int kind) {
            this.section = section;
            this.key = key;
            this.label = label;
            this.comment = comment;
            this.value = value;
            this.kind = kind;
            this.haystack = (label.getString() + " " + key + " " + (comment == null ? "" : comment)).toLowerCase(Locale.ROOT);
        }
    }

    public SettingsScreen(Screen parent) {
        super(Component.translatable("insight_addons.configuration.title"));
        this.parent = parent;
        collect();
    }

    private void collect() {
        UnmodifiableConfig values = InsightConfig.SPEC.getValues();
        for (UnmodifiableConfig.Entry se : values.entrySet()) {
            if (!(se.getValue() instanceof UnmodifiableConfig sec)) {
                continue;
            }
            String sKey = se.getKey();
            sectionNames.put(sKey, Component.translatable("insight_addons.configuration." + sKey));
            if ("insightChestsIntegration".equals(sKey)) {
                entries.add(new Entry(sKey, "preview", Component.translatable("insight_addons.configuration.preview.open"),
                        null, null, 6));
            }
            for (UnmodifiableConfig.Entry ve : sec.entrySet()) {
                if (!(ve.getValue() instanceof ModConfigSpec.ConfigValue<?> cv)) {
                    continue;
                }
                Object cur = cv.get();
                int kind;
                if (cur instanceof Boolean) {
                    kind = 0;
                } else if (cur instanceof Integer) {
                    kind = range(cv) != null ? 1 : 4;
                } else if (cur instanceof Double) {
                    kind = range(cv) != null ? 2 : 4;
                } else if (cur instanceof String s && ("auto".equals(s) || "on".equals(s) || "off".equals(s))) {
                    kind = 3;
                } else if (cur instanceof String) {
                    kind = 4;
                } else if (cur instanceof List<?>) {
                    kind = 5;
                } else {
                    continue;
                }
                ModConfigSpec.ValueSpec vs = spec(cv);
                String tr = vs != null ? vs.getTranslationKey() : null;
                Component label = tr != null && I18n.exists(tr) ? Component.translatable(tr) : Component.literal(ve.getKey());
                String comment = vs != null ? vs.getComment() : null;
                entries.add(new Entry(sKey, ve.getKey(), label, comment, cv, kind));
            }
        }
    }

    private static ModConfigSpec.ValueSpec spec(ModConfigSpec.ConfigValue<?> cv) {
        try {
            Object o = InsightConfig.SPEC.getSpec().get(cv.getPath());
            return o instanceof ModConfigSpec.ValueSpec vs ? vs : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Number[] range(ModConfigSpec.ConfigValue<?> cv) {
        try {
            ModConfigSpec.ValueSpec vs = spec(cv);
            if (vs != null && vs.getRange() != null) {
                Object r = vs.getRange();
                Object min = r.getClass().getMethod("getMin").invoke(r);
                Object max = r.getClass().getMethod("getMax").invoke(r);
                if (min instanceof Number a && max instanceof Number b) {
                    return new Number[]{a, b};
                }
            }
        } catch (Throwable ignored) {
            // 범위를 못 읽으면 글자 입력칸으로 대신한다
        }
        return null;
    }

    @Override
    protected void init() {
        tabW = Math.max(70, Math.min(118, width / 5));
        listX = 12 + tabW + 10;
        listY = 52;
        listW = Math.max(120, width - listX - 14);
        ctrlW = Math.max(70, Math.min(150, listW * 2 / 5));
        listH = height - listY - 36;

        String old = search == null ? query : search.getValue();
        search = new EditBox(font, listX, 26, Math.min(listW, 260), 18, Component.translatable("insight_addons.configuration.search"));
        search.setHint(Component.translatable("insight_addons.configuration.search").withStyle(net.minecraft.ChatFormatting.GRAY));
        search.setValue(old);
        search.setResponder(s -> {
            query = s.trim().toLowerCase(Locale.ROOT);
            scroll = 0;
            rebuildList();
        });
        addRenderableWidget(search);

        // 카테고리 탭
        int ty = 52;
        addRenderableWidget(Button.builder(tabLabel(ALL, Component.translatable("insight_addons.configuration.tab.all")), b -> selectTab(ALL))
                .bounds(12, ty, tabW, 20).build());
        ty += 22;
        for (Map.Entry<String, Component> e : sectionNames.entrySet()) {
            String key = e.getKey();
            addRenderableWidget(Button.builder(tabLabel(key, e.getValue()), b -> selectTab(key)).bounds(12, ty, tabW, 20).build());
            ty += 22;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());

        query = search.getValue().trim().toLowerCase(Locale.ROOT);
        rebuildList();
    }

    private Component tabLabel(String key, Component name) {
        boolean sel = key.equals(tab);
        return sel ? Component.empty().append(Component.literal("▶ ")).append(name).withStyle(net.minecraft.ChatFormatting.YELLOW)
                : name;
    }

    private void selectTab(String key) {
        tab = key;
        scroll = 0;
        rebuildWidgets();
    }

    private boolean matches(Entry e) {
        if (!tab.equals(ALL) && !tab.equals(e.section)) {
            return false;
        }
        return query.isEmpty() || e.haystack.contains(query);
    }

    private void rebuildList() {
        for (AbstractWidget w : listWidgets) {
            removeWidget(w);
        }
        listWidgets.clear();
        placed.clear();
        headerSections.clear();
        widgetOffset.clear();
        String lastSection = null;
        int off = 0;
        for (Entry e : entries) {
            if (!matches(e)) {
                continue;
            }
            if (!e.section.equals(lastSection)) {
                lastSection = e.section;
                placed.add(new Placed(null, off));
                headerSections.put(off, e.section);
                off += HEADER_H;
            }
            placed.add(new Placed(e, off));
            AbstractWidget w = createWidget(e);
            if (w != null) {
                listWidgets.add(w);
                addRenderableWidget(w);
                widgetOffset.put(w, off);
            }
            off += ROW_H;
        }
        contentH = off;
        clampScroll();
    }

    private final Map<Integer, String> headerSections = new LinkedHashMap<>();
    private final Map<AbstractWidget, Integer> widgetOffset = new java.util.IdentityHashMap<>();
    private int contentH;

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - listH));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractWidget createWidget(Entry e) {
        int x = listX + listW - ctrlW - 8;
        switch (e.kind) {
            case 0: {
                ModConfigSpec.ConfigValue<Boolean> cv = (ModConfigSpec.ConfigValue<Boolean>) e.value;
                return Button.builder(boolText(cv.get()), b -> {
                    commit(cv, !cv.get());
                    b.setMessage(boolText(cv.get()));
                }).bounds(x, 0, ctrlW, 18).build();
            }
            case 1: {
                ModConfigSpec.ConfigValue<Integer> cv = (ModConfigSpec.ConfigValue<Integer>) e.value;
                Number[] r = range(cv);
                return new NumSlider(x, ctrlW, r[0].doubleValue(), r[1].doubleValue(), cv.get(), true,
                        v -> commit(cv, (int) Math.round(v)));
            }
            case 2: {
                ModConfigSpec.ConfigValue<Double> cv = (ModConfigSpec.ConfigValue<Double>) e.value;
                Number[] r = range(cv);
                return new NumSlider(x, ctrlW, r[0].doubleValue(), r[1].doubleValue(), cv.get(), false,
                        v -> commit(cv, Math.round(v * 100.0) / 100.0));
            }
            case 3: {
                ModConfigSpec.ConfigValue<String> cv = (ModConfigSpec.ConfigValue<String>) e.value;
                String[] order = {"auto", "on", "off"};
                return Button.builder(choiceText(cv.get()), b -> {
                    String cur = cv.get();
                    int i = 0;
                    for (int k = 0; k < order.length; k++) {
                        if (order[k].equals(cur)) {
                            i = k;
                        }
                    }
                    commit(cv, order[(i + 1) % order.length]);
                    b.setMessage(choiceText(cv.get()));
                }).bounds(x, 0, ctrlW, 18).build();
            }
            case 4: {
                ModConfigSpec.ConfigValue<Object> cv = (ModConfigSpec.ConfigValue<Object>) e.value;
                EditBox box = new EditBox(font, x, 0, ctrlW, 18, e.label);
                box.setMaxLength(256);
                box.setValue(String.valueOf(cv.get()));
                box.setResponder(s -> {
                    Object cur = cv.get();
                    try {
                        if (cur instanceof Integer) {
                            commit(cv, Integer.parseInt(s.trim()));
                        } else if (cur instanceof Double) {
                            commit(cv, Double.parseDouble(s.trim()));
                        } else {
                            commit(cv, s);
                        }
                    } catch (NumberFormatException ignored) {
                        // 숫자가 아니면 무시
                    }
                });
                return box;
            }
            case 5: {
                ModConfigSpec.ConfigValue<?> cv = e.value;
                String up = e.key.toUpperCase(Locale.ROOT);
                ListEditorScreen.Kind kind = up.contains("MOD") ? ListEditorScreen.Kind.MODID
                        : up.contains("TOOL") ? ListEditorScreen.Kind.ITEM : ListEditorScreen.Kind.BLOCK;
                int count = ((List<?>) cv.get()).size();
                return Button.builder(Component.translatable("insight_addons.configuration.listEditor.openText", count), b -> {
                    touched.add(cv);
                    Minecraft.getInstance().setScreen(new ListEditorScreen(this, cv, kind,
                            Component.translatable("insight_addons.configuration.listEditor.title")));
                }).bounds(x, 0, ctrlW, 18).build();
            }
            case 6:
                return Button.builder(Component.translatable("insight_addons.configuration.preview.openText"),
                        b -> Minecraft.getInstance().setScreen(new PreviewScreen(this))).bounds(x, 0, ctrlW, 18).build();
            default:
                return null;
        }
    }

    private static Component boolText(boolean v) {
        return v ? Component.translatable("options.on").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("options.off").withStyle(net.minecraft.ChatFormatting.RED);
    }

    private static Component choiceText(String v) {
        return Component.translatable("insight_addons.configuration.choice." + v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void commit(ModConfigSpec.ConfigValue cv, Object value) {
        try {
            cv.set(value);
            if (!touched.contains(cv)) {
                touched.add(cv);
            }
        } catch (Throwable ignored) {
            // 범위를 벗어난 값 등은 무시
        }
    }

    /** 숫자 슬라이더 (정수/소수 공용) */
    private static final class NumSlider extends AbstractSliderButton {
        private final double min, max;
        private final boolean integer;
        private final java.util.function.DoubleConsumer apply;

        NumSlider(int x, int w, double min, double max, double cur, boolean integer, java.util.function.DoubleConsumer apply) {
            super(x, 0, w, 18, Component.empty(), Mth.clamp((cur - min) / (max - min), 0, 1));
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.apply = apply;
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            return integer ? Math.round(v) : Math.round(v * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            double v = current();
            setMessage(Component.literal(integer ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v)));
        }

        @Override
        protected void applyValue() {
            apply.accept(current());
        }
    }

    /** Screen.render가 배경을 한 번 더 그려서 우리 글씨를 흐리게 덮지 않도록, 직접 한 번만 그린다 */
    private boolean bgDone;

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (!bgDone) {
            super.renderBackground(g, mx, my, pt);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        bgDone = false;
        renderBackground(g, mx, my, pt);
        bgDone = true;
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        g.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, 0x80000000);

        // 항목 위치 잡기 + 보이는 것만 켜기
        for (AbstractWidget w : listWidgets) {
            Integer off = widgetOffset.get(w);
            if (off == null) {
                continue;
            }
            int y = listY + off - (int) scroll + (ROW_H - 18) / 2;
            w.setY(y);
            w.visible = y >= listY && y + 18 <= listY + listH;
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        Component tip = null;
        for (Placed p : placed) {
            int y = listY + p.offset() - (int) scroll;
            if (y + ROW_H < listY || y > listY + listH) {
                continue;
            }
            if (p.entry() == null) {
                String sec = headerSections.get(p.offset());
                Component name = sectionNames.getOrDefault(sec, Component.literal(String.valueOf(sec)));
                g.drawString(font, Component.empty().append(name).withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.AQUA),
                        listX + 6, y + 5, 0xFFFFFF);
                g.fill(listX + 4, y + HEADER_H - 3, listX + listW - 4, y + HEADER_H - 2, 0x55FFFFFF);
            } else {
                Entry e = p.entry();
                boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H && my >= listY && my < listY + listH;
                if (hover) {
                    g.fill(listX + 2, y, listX + listW - 2, y + ROW_H, 0x22FFFFFF);
                    if (e.comment != null && !e.comment.isBlank() && mx < listX + listW - ctrlW - 8) {
                        tip = Component.literal(e.comment.trim());
                    }
                }
                int maxLabel = listW - ctrlW - 24;
                g.drawString(font, font.plainSubstrByWidth(e.label.getString(), maxLabel), listX + 8, y + (ROW_H - 8) / 2, 0xE0E0E0);
            }
        }
        g.disableScissor();

        // 스크롤 막대
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barY = listY + (int) ((listH - barH) * (scroll / (contentH - listH)));
            g.fill(listX + listW - 4, listY, listX + listW - 1, listY + listH, 0x44000000);
            g.fill(listX + listW - 4, barY, listX + listW - 1, barY + barH, 0xAAFFFFFF);
        }
        if (placed.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("insight_addons.configuration.noResults"),
                    listX + listW / 2, listY + listH / 2 - 4, 0xA0A0A0);
        }

        super.render(g, mx, my, pt);
        if (tip != null) {
            g.renderTooltip(font, font.split(tip, 220), mx, my);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            scroll -= sy * 20;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        for (ModConfigSpec.ConfigValue<?> cv : touched) {
            try {
                cv.save();
            } catch (Throwable ignored) {
                // 저장 실패는 조용히 넘어간다
            }
        }
    }
}
