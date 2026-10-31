package dev.insightaddons.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 설정의 블록 ID 패턴("모드:이름", *는 와일드카드)과 블록을 대조합니다. */
public final class ContainerFilter {
    private static List<? extends String> lastSource;
    private static List<Pattern> compiled = List.of();
    private static final Map<ResourceLocation, Boolean> CACHE = new HashMap<>();

    private ContainerFilter() {}

    /** 위치까지 보는 판별: AE2 케이블은 단말기가 붙은 것만 대상으로 한다. */
    public static boolean matches(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, ResourceLocation id) {
        if (!matches(id)) {
            return false;
        }
        if (id.getNamespace().equals("ae2") && id.getPath().equals("cable_bus")) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            return be != null && dev.insightaddons.AeBridge.hasTerminal(be);
        }
        return true;
    }

    public static boolean matches(ResourceLocation id) {
        if (id.getNamespace().equals("ae2") && dev.insightaddons.AeBridge.isAeBlock(id)) {
            return InsightConfig.AE2_ENABLED.get();
        }
        if (id.getNamespace().equals("mekanism") && InsightConfig.MEKANISM_ENABLED.get()
                && (id.getPath().equals("personal_chest") || id.getPath().endsWith("_bin"))) {
            return true;
        }
        List<? extends String> source = InsightConfig.CONTAINER_BLOCKS.get();
        if (lastSource == null || !source.equals(lastSource)) {
            lastSource = List.copyOf(source);
            List<Pattern> list = new ArrayList<>();
            for (String raw : source) {
                String regex = Arrays.stream(raw.trim().split("\\*", -1))
                        .map(Pattern::quote)
                        .collect(Collectors.joining(".*"));
                list.add(Pattern.compile(regex));
            }
            compiled = list;
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(id, key -> {
            String text = key.toString();
            for (Pattern p : compiled) {
                if (p.matcher(text).matches()) {
                    return true;
                }
            }
            return false;
        });
    }
}
