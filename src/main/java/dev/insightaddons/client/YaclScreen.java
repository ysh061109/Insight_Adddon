package dev.insightaddons.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * YetAnotherConfigLib(YACL 3)가 설치되어 있으면 그 화면으로 모드 설정을 보여줍니다.
 * 섹션마다 색이 다른 탭(카테고리)으로 나누고, 숫자는 슬라이더, 선택지는 순환 버튼, 켜기/끄기는 색 버튼으로 보여 줍니다.
 * YACL에 컴파일 의존성을 두지 않으려고 전부 리플렉션으로 만들며, 핵심 단계가 실패하면 null을 돌려줘서
 * 호출한 쪽이 NeoForge 기본 설정 화면으로 대신하게 합니다. (꾸밈 요소가 실패하는 것은 조용히 건너뜁니다.)
 * 설정 항목은 InsightConfig.SPEC에서 자동으로 읽어 오므로 항목을 추가해도 이 파일은 고칠 필요가 없습니다.
 */
public final class YaclScreen {
    private static final String P = "dev.isxander.yacl3.api.";

    /** 탭 순서와 색 */
    private static final Map<String, Integer> SECTION_COLORS = new LinkedHashMap<>();
    /** 큰 섹션을 소제목 묶음으로 나누는 배치: 섹션 -> (묶음 ID -> 항목 키들) */
    private static final Map<String, Map<String, List<String>>> LAYOUT = new LinkedHashMap<>();

    static {
        SECTION_COLORS.put("containers", 0xFFB347);
        SECTION_COLORS.put("nearDisplay", 0x59C4F5);
        SECTION_COLORS.put("memory", 0xA0E070);
        SECTION_COLORS.put("insightChestsIntegration", 0xC792EA);
        SECTION_COLORS.put("recipeFocus", 0xFF7AA2);
        SECTION_COLORS.put("ae2", 0x5CE1E6);
        SECTION_COLORS.put("mekanism", 0xFFD166);
        SECTION_COLORS.put("craftEffect", 0xFF9F68);
        SECTION_COLORS.put("effects", 0xF4F1BB);
        SECTION_COLORS.put("wrenchScan", 0x9AD1D4);

        Map<String, List<String>> ins = new LinkedHashMap<>();
        ins.put("basic", List.of("enabled", "blocks", "keepOriginalScreen"));
        ins.put("glass", List.of("glassSkin", "glassAlpha", "glassSlotAlpha", "followInsightPosition"));
        ins.put("items", List.of("itemScale", "itemScaleBackpack", "itemScaleShulker", "itemScaleBarrel", "dropRatio", "dropMaxTotal", "itemSizeScaling", "itemGlow", "fancyModIds"));
        LAYOUT.put("insightChestsIntegration", ins);
    }

    private YaclScreen() {}

    private static Component colored(String text, int rgb, boolean bold) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));
    }

    private static MutableComponent sectionName(String key) {
        int rgb = SECTION_COLORS.getOrDefault(key, 0xFFFFFF);
        return Component.translatable("insight_addons.configuration." + key)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
    }

    public static Screen create(Screen parent) {
        try {
            Class<?> libC = Class.forName(P + "YetAnotherConfigLib");
            Class<?> libB = Class.forName(P + "YetAnotherConfigLib$Builder");
            Class<?> catC = Class.forName(P + "ConfigCategory");
            Class<?> catB = Class.forName(P + "ConfigCategory$Builder");
            Class<?> grpC = Class.forName(P + "OptionGroup");
            Class<?> grpB = Class.forName(P + "OptionGroup$Builder");
            Class<?> optC = Class.forName(P + "Option");
            Class<?> optB = Class.forName(P + "Option$Builder");
            Class<?> descC = Class.forName(P + "OptionDescription");

            Object lib = libC.getMethod("createBuilder").invoke(null);
            lib = libB.getMethod("title", Component.class).invoke(lib,
                    Component.translatable("insight_addons.configuration.title").withStyle(
                            Style.EMPTY.withColor(TextColor.fromRgb(0x59C4F5)).withBold(true)));

            UnmodifiableConfig values = InsightConfig.SPEC.getValues();
            Map<String, UnmodifiableConfig> sections = new LinkedHashMap<>();
            for (String key : SECTION_COLORS.keySet()) {
                if (values.get(List.of(key)) instanceof UnmodifiableConfig sec) {
                    sections.put(key, sec);
                }
            }
            for (UnmodifiableConfig.Entry entry : values.entrySet()) {
                if (entry.getValue() instanceof UnmodifiableConfig sec && !sections.containsKey(entry.getKey())) {
                    sections.put(entry.getKey(), sec);
                }
            }

            for (Map.Entry<String, UnmodifiableConfig> se : sections.entrySet()) {
                String sKey = se.getKey();
                UnmodifiableConfig section = se.getValue();

                Object cat = catC.getMethod("createBuilder").invoke(null);
                cat = catB.getMethod("name", Component.class).invoke(cat, sectionName(sKey));
                String descKey = "insight_addons.configuration." + sKey + ".desc";
                if (I18n.exists(descKey)) {
                    Component tip = Component.translatable(descKey);
                    try {
                        cat = catB.getMethod("tooltip", Component[].class).invoke(cat, (Object) new Component[]{tip});
                    } catch (Throwable ignored) {
                        // 툴팁은 꾸밈 요소라 실패해도 계속한다
                    }
                    cat = addHeader(cat, catB, tip);
                }

                // 항목을 묶음으로 나눈다
                Map<String, List<ModConfigSpec.ConfigValue<?>>> groups = new LinkedHashMap<>();
                Map<String, String> keyToGroup = new LinkedHashMap<>();
                Map<String, List<String>> layout = LAYOUT.get(sKey);
                if (layout != null) {
                    for (Map.Entry<String, List<String>> g : layout.entrySet()) {
                        for (String k : g.getValue()) {
                            keyToGroup.put(k, g.getKey());
                        }
                        groups.put(g.getKey(), new ArrayList<>());
                    }
                } else {
                    groups.put("", new ArrayList<>());
                }
                Map<String, String> leafKeys = new LinkedHashMap<>();
                for (UnmodifiableConfig.Entry leaf : section.entrySet()) {
                    if (!(leaf.getValue() instanceof ModConfigSpec.ConfigValue<?> cv)) {
                        continue;
                    }
                    String gid = layout == null ? "" : keyToGroup.getOrDefault(leaf.getKey(), "other");
                    groups.computeIfAbsent(gid, x -> new ArrayList<>()).add(cv);
                    leafKeys.put(String.valueOf(System.identityHashCode(cv)), leaf.getKey());
                }

                boolean anyCat = false;
                for (Map.Entry<String, List<ModConfigSpec.ConfigValue<?>>> ge : groups.entrySet()) {
                    List<Object> built = new ArrayList<>();
                    for (ModConfigSpec.ConfigValue<?> cv : ge.getValue()) {
                        String leafKey = leafKeys.get(String.valueOf(System.identityHashCode(cv)));
                        Object option = buildOption(optC, optB, descC, sKey, leafKey, cv);
                        if (option != null) {
                            built.add(option);
                        }
                    }
                    if (built.isEmpty()) {
                        continue;
                    }
                    anyCat = true;
                    if (ge.getKey().isEmpty()) {
                        for (Object o : built) {
                            cat = catB.getMethod("option", optC).invoke(cat, o);
                        }
                    } else {
                        Object group = grpC.getMethod("createBuilder").invoke(null);
                        group = grpB.getMethod("name", Component.class).invoke(group,
                                colored("", 0xFFFFFF, false).copy().append(
                                        Component.translatable("insight_addons.configuration.group." + ge.getKey())
                                                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(SECTION_COLORS.getOrDefault(sKey, 0xFFFFFF))))));
                        for (Object o : built) {
                            group = grpB.getMethod("option", optC).invoke(group, o);
                        }
                        cat = catB.getMethod("group", grpC).invoke(cat, grpB.getMethod("build").invoke(group));
                    }
                }
                if (anyCat) {
                    lib = libB.getMethod("category", catC).invoke(lib, catB.getMethod("build").invoke(cat));
                }
            }
            Runnable save = InsightConfig.CRAFT_FX::save;
            lib = libB.getMethod("save", Runnable.class).invoke(lib, save);
            Object built = libB.getMethod("build").invoke(lib);
            return (Screen) libC.getMethod("generateScreen", Screen.class).invoke(built, parent);
        } catch (Throwable t) {
            dev.insightaddons.InsightAddons.LOGGER.warn("YACL 설정 화면을 만들지 못해 기본 설정 화면으로 대신합니다: {}", t.toString());
            return null;
        }
    }

    /** 탭 맨 위에 설명 글을 한 줄 넣는다 (LabelOption). 실패하면 그냥 넘어간다. */
    private static Object addHeader(Object cat, Class<?> catB, Component text) {
        try {
            Class<?> labelC = Class.forName(P + "LabelOption");
            Object label = labelC.getMethod("create", Component.class).invoke(null, text);
            return catB.getMethod("option", Class.forName(P + "Option")).invoke(cat, label);
        } catch (Throwable t) {
            return cat;
        }
    }

    private static Method find(Class<?> c, String name, int args) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args) {
                return m;
            }
        }
        return null;
    }

    private static Object tryInvoke(Object target, String name, Object... args) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    Object r = m.invoke(target, args);
                    return r == null ? target : r;
                } catch (Throwable ignored) {
                    // 같은 이름의 다른 정의를 계속 시도한다
                }
            }
        }
        return target;
    }

    /** ValueSpec의 범위(min,max). 범위가 없으면 null */
    private static Number[] range(ModConfigSpec.ConfigValue<?> cv) {
        try {
            Object spec = InsightConfig.SPEC.getSpec().get(cv.getPath());
            if (spec instanceof ModConfigSpec.ValueSpec vs) {
                Object r = vs.getRange();
                if (r != null) {
                    Object min = r.getClass().getMethod("getMin").invoke(r);
                    Object max = r.getClass().getMethod("getMax").invoke(r);
                    if (min instanceof Number a && max instanceof Number b) {
                        return new Number[]{a, b};
                    }
                }
            }
        } catch (Throwable ignored) {
            // 범위를 못 읽으면 숫자 입력칸으로 대신한다
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object buildOption(Class<?> optC, Class<?> optB, Class<?> descC, String section, String key,
                                      ModConfigSpec.ConfigValue cv) throws Exception {
        Object current = cv.get();
        Object def = cv.getDefault();
        Supplier getter;
        Consumer setter;
        Object defaultForUi;
        Function<Object, Object> factory;
        Number[] range = range(cv);

        if (current instanceof Boolean) {
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
            factory = controller("BooleanControllerBuilder", optC, b -> {
                Object x = tryInvoke(b, "coloured", true);
                return tryInvoke(x, "onOffFormatter");
            });
        } else if (current instanceof Integer) {
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
            if (range != null && range[1].longValue() - range[0].longValue() <= 1000) {
                Integer lo = range[0].intValue();
                Integer hi = range[1].intValue();
                factory = controller("IntegerSliderControllerBuilder", optC, b -> {
                    Object x = tryInvoke(b, "range", lo, hi);
                    return tryInvoke(x, "step", 1);
                });
            } else {
                Integer lo = range == null ? null : range[0].intValue();
                Integer hi = range == null ? null : range[1].intValue();
                factory = controller("IntegerFieldControllerBuilder", optC, b -> lo == null ? b : tryInvoke(b, "range", lo, hi));
            }
        } else if (current instanceof Double) {
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
            if (range != null) {
                Double lo = range[0].doubleValue();
                Double hi = range[1].doubleValue();
                double span = hi - lo;
                Double step = span <= 2.5 ? 0.01 : span <= 10 ? 0.05 : 0.5;
                factory = controller("DoubleSliderControllerBuilder", optC, b -> {
                    Object x = tryInvoke(b, "range", lo, hi);
                    return tryInvoke(x, "step", step);
                });
            } else {
                factory = controller("DoubleFieldControllerBuilder", optC, b -> b);
            }
        } else if (current instanceof String str && ("auto".equals(str) || "on".equals(str) || "off".equals(str)
                || "bright".equals(str) || "insight".equals(str))) {
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
            List<String> choices = List.of("auto", "on", "off");
            factory = controller("CyclingListControllerBuilder", optC, b -> {
                Object x = tryInvoke(b, "values", choices);
                Function<Object, Component> fmt = v -> Component.translatable("insight_addons.configuration.choice." + v);
                return tryInvoke(x, "formatValue", fmt);
            });
        } else if (current instanceof String) {
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
            factory = controller("StringControllerBuilder", optC, b -> b);
        } else if (current instanceof List<?>) {
            // 목록은 쉼표로 구분한 글자 한 줄로 편집한다
            defaultForUi = join((List<?>) def);
            getter = () -> join((List<?>) cv.get());
            setter = v -> {
                List<String> parsed = new ArrayList<>();
                for (String part : String.valueOf(v).split(",")) {
                    if (!part.trim().isEmpty()) {
                        parsed.add(part.trim());
                    }
                }
                set(cv, parsed);
            };
            factory = controller("StringControllerBuilder", optC, b -> b);
        } else {
            return null;
        }

        String langKey = "insight_addons.configuration." + section + "." + key;
        Object b = optC.getMethod("createBuilder").invoke(null);
        b = optB.getMethod("name", Component.class).invoke(b, Component.translatable(langKey));

        Object spec = InsightConfig.SPEC.getSpec().get(cv.getPath());
        String comment = spec instanceof ModConfigSpec.ValueSpec vs && vs.getComment() != null ? vs.getComment() : null;
        if (comment != null) {
            List<Component> lines = new ArrayList<>();
            for (String line : comment.split("\n")) {
                if (!line.isBlank()) {
                    lines.add(Component.literal(line.trim()));
                }
            }
            Object desc = descC.getMethod("of", Component[].class).invoke(null, (Object) lines.toArray(new Component[0]));
            b = optB.getMethod("description", descC).invoke(b, desc);
        }
        b = optB.getMethod("binding", Object.class, Supplier.class, Consumer.class).invoke(b, defaultForUi, getter, setter);
        b = optB.getMethod("controller", Function.class).invoke(b, factory);
        return optB.getMethod("build").invoke(b);
    }

    /** 컨트롤러 빌더를 만들고 꾸밈 단계(범위, 단계, 색 등)를 적용하는 함수 */
    private static Function<Object, Object> controller(String name, Class<?> optC, Function<Object, Object> decorate) throws Exception {
        Class<?> ctl = Class.forName(P + "controller." + name);
        Method create = ctl.getMethod("create", optC);
        return o -> {
            try {
                Object builder = create.invoke(null, o);
                return decorate.apply(builder);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(ModConfigSpec.ConfigValue cv, Object value) {
        try {
            cv.set(value);
        } catch (Throwable ignored) {
            // 범위를 벗어난 값 등은 무시
        }
    }

    private static String join(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(o);
        }
        return sb.toString();
    }
}
