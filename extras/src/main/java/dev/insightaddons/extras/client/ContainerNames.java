package dev.insightaddons.extras.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 보관함에 붙인 이름표. 내 컴퓨터(config 폴더)에만 저장되며 다른 플레이어에게는 보이지 않습니다. */
public final class ContainerNames {
    private static final Map<String, String> NAMES = new HashMap<>();
    private static boolean loaded;

    private ContainerNames() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("insight_addons_names.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, String> read = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                if (read != null) {
                    NAMES.putAll(read);
                }
            }
        } catch (Throwable t) {
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("보관함 이름표를 읽지 못했습니다: {}", t.toString());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), new Gson().toJson(NAMES), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("보관함 이름표를 저장하지 못했습니다: {}", t.toString());
        }
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 이름표가 없으면 null */
    public static String get(Level level, BlockPos pos) {
        load();
        return NAMES.get(key(level, pos));
    }

    /** 빈 글자면 이름표를 지운다 */
    public static void set(Level level, BlockPos pos, String name) {
        load();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            NAMES.remove(key(level, pos));
        } else {
            NAMES.put(key(level, pos), trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed);
        }
        save();
    }
}
