package dev.insightaddons.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * YetAnotherConfigLib(YACL 3)가 설치되어 있으면 그 화면으로 모드 설정을 보여줍니다.
 * YACL에 컴파일 의존성을 두지 않으려고 전부 리플렉션으로 만들며, 하나라도 실패하면 null을 돌려줘서
 * 호출한 쪽이 NeoForge 기본 설정 화면으로 대신하게 합니다.
 * 설정 항목은 InsightConfig.SPEC에서 자동으로 읽어 오므로 항목을 추가해도 이 파일은 고칠 필요가 없습니다.
 */
public final class YaclScreen {
    private static final String P = "dev.isxander.yacl3.api.";

    private YaclScreen() {}

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
            lib = libB.getMethod("title", Component.class).invoke(lib, Component.translatable("insight_addons.configuration.title"));

            Object cat = catC.getMethod("createBuilder").invoke(null);
            cat = catB.getMethod("name", Component.class).invoke(cat, Component.translatable("insight_addons.configuration.title"));

            UnmodifiableConfig values = InsightConfig.SPEC.getValues();
            for (UnmodifiableConfig.Entry entry : values.entrySet()) {
                if (!(entry.getValue() instanceof UnmodifiableConfig section)) {
                    continue;
                }
                Object group = grpC.getMethod("createBuilder").invoke(null);
                group = grpB.getMethod("name", Component.class).invoke(group,
                        Component.translatable("insight_addons.configuration." + entry.getKey()));
                boolean any = false;
                for (UnmodifiableConfig.Entry leaf : section.entrySet()) {
                    if (!(leaf.getValue() instanceof ModConfigSpec.ConfigValue<?> cv)) {
                        continue;
                    }
                    Object option = buildOption(optC, optB, descC, entry.getKey(), leaf.getKey(), cv);
                    if (option != null) {
                        group = grpB.getMethod("option", optC).invoke(group, option);
                        any = true;
                    }
                }
                if (any) {
                    Object builtGroup = grpB.getMethod("build").invoke(group);
                    cat = catB.getMethod("group", grpC).invoke(cat, builtGroup);
                }
            }
            Object builtCat = catB.getMethod("build").invoke(cat);
            lib = libB.getMethod("category", catC).invoke(lib, builtCat);
            Runnable save = InsightConfig.CRAFT_FX::save;
            lib = libB.getMethod("save", Runnable.class).invoke(lib, save);
            Object built = libB.getMethod("build").invoke(lib);
            return (Screen) libC.getMethod("generateScreen", Screen.class).invoke(built, parent);
        } catch (Throwable t) {
            dev.insightaddons.InsightAddons.LOGGER.warn("YACL 설정 화면을 만들지 못해 기본 설정 화면으로 대신합니다: {}", t.toString());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object buildOption(Class<?> optC, Class<?> optB, Class<?> descC, String section, String key,
                                      ModConfigSpec.ConfigValue cv) throws Exception {
        Object current = cv.get();
        Object def = cv.getDefault();
        String controller;
        Supplier getter;
        Consumer setter;
        Object defaultForUi;

        if (current instanceof Boolean) {
            controller = "TickBoxControllerBuilder";
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
        } else if (current instanceof Integer) {
            controller = "IntegerFieldControllerBuilder";
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
        } else if (current instanceof Double) {
            controller = "DoubleFieldControllerBuilder";
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
        } else if (current instanceof String) {
            controller = "StringControllerBuilder";
            defaultForUi = def;
            getter = cv::get;
            setter = v -> set(cv, v);
        } else if (current instanceof List<?>) {
            // 목록은 쉼표로 구분한 글자 한 줄로 편집한다
            controller = "StringControllerBuilder";
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
        } else {
            return null;
        }

        String langKey = "insight_addons.configuration." + section + "." + key;
        Object b = optC.getMethod("createBuilder").invoke(null);
        b = optB.getMethod("name", Component.class).invoke(b, Component.translatable(langKey));

        Object spec = InsightConfig.SPEC.getSpec().get(cv.getPath());
        String comment = spec instanceof ModConfigSpec.ValueSpec vs && vs.getComment() != null ? vs.getComment() : null;
        if (comment != null) {
            Object desc = descC.getMethod("of", Component[].class).invoke(null, (Object) new Component[]{Component.literal(comment)});
            b = optB.getMethod("description", descC).invoke(b, desc);
        }
        b = optB.getMethod("binding", Object.class, Supplier.class, Consumer.class).invoke(b, defaultForUi, getter, setter);

        Class<?> ctl = Class.forName(P + "controller." + controller);
        Method create = ctl.getMethod("create", optC);
        Function<Object, Object> factory = o -> {
            try {
                return create.invoke(null, o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        b = optB.getMethod("controller", Function.class).invoke(b, factory);
        return optB.getMethod("build").invoke(b);
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
