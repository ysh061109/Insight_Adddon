package dev.insightaddons.extras.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** "모드:이름" 패턴 목록(*는 와일드카드)과 ID를 대조합니다. 결과는 캐시합니다. */
public final class GlobList {
    private List<? extends String> lastSource;
    private List<Pattern> compiled = List.of();
    private final Map<ResourceLocation, Boolean> cache = new HashMap<>();

    public boolean matches(List<? extends String> source, ResourceLocation id) {
        if (lastSource == null || !source.equals(lastSource)) {
            lastSource = List.copyOf(source);
            List<Pattern> list = new ArrayList<>();
            for (String raw : source) {
                StringBuilder regex = new StringBuilder();
                String[] parts = raw.trim().split("\\*", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        regex.append(".*");
                    }
                    regex.append(Pattern.quote(parts[i]));
                }
                list.add(Pattern.compile(regex.toString()));
            }
            compiled = list;
            cache.clear();
        }
        Boolean hit = cache.get(id);
        if (hit == null) {
            String text = id.toString();
            boolean found = false;
            for (Pattern p : compiled) {
                if (p.matcher(text).matches()) {
                    found = true;
                    break;
                }
            }
            cache.put(id, found);
            hit = found;
        }
        return hit;
    }
}
