package dev.insightaddons.extras.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 필요 재료 목록(쇼핑 목록). JEI/EMI 재료 찾기를 켠 상태에서 "목록에 담기" 키를 누를 때마다 그 레시피의 재료가 합산됩니다.
 * 화면 왼쪽에 "인벤토리 개수/필요 개수"로 표시되고(목표에 가까울수록 초록), 다 모으면 체크 표시가 붙습니다.
 */
public final class ShoppingList {
    private static final Map<Item, Integer> NEED = new LinkedHashMap<>();
    private static boolean visible = true;
    private static boolean loaded;

    private ShoppingList() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("insight_addons_list.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, Integer> read = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Integer>>() {}.getType());
                if (read != null) {
                    for (Map.Entry<String, Integer> e : read.entrySet()) {
                        BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(e.getKey()))
                                .ifPresent(item -> NEED.put(item, e.getValue()));
                    }
                }
            }
        } catch (Throwable t) {
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("재료 목록을 읽지 못했습니다: {}", t.toString());
        }
    }

    private static void save() {
        try {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<Item, Integer> e : NEED.entrySet()) {
                out.put(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
            }
            Files.writeString(file(), new Gson().toJson(out), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("재료 목록을 저장하지 못했습니다: {}", t.toString());
        }
    }

    public static boolean isEmpty() {
        load();
        return NEED.isEmpty();
    }

    private static int owned(Minecraft mc, Item item) {
        if (mc.player == null) {
            return 0;
        }
        Inventory inv = mc.player.getInventory();
        int sum = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                sum += s.getCount();
            }
        }
        return sum;
    }

    /** 지금 켜져 있는 재료 찾기의 재료를 목록에 더한다. 태그로 대체 가능한 재료는 가장 많이 가진 것(없으면 첫 번째)으로 담는다. */
    public static int addCurrent() {
        load();
        Minecraft mc = Minecraft.getInstance();
        int added = 0;
        for (RecipeFocus.Need need : RecipeFocus.needs()) {
            Item best = need.options().get(0);
            int bestOwned = -1;
            for (Item option : need.options()) {
                int o = owned(mc, option);
                if (o > bestOwned) {
                    bestOwned = o;
                    best = option;
                }
            }
            NEED.merge(best, need.count(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            added++;
        }
        if (added > 0) {
            save();
            visible = true;
        }
        return added;
    }

    public static void clear() {
        load();
        NEED.clear();
        save();
    }

    public static boolean toggleVisible() {
        visible = !visible;
        return visible;
    }

    private static int ratioColor(int have, int need) {
        double r = need <= 0 ? 1.0 : (double) have / need;
        if (r >= 1.0) {
            return 0xFF55FF55;
        }
        if (r >= 0.6) {
            return 0xFFFFE04A;
        }
        if (r >= 0.3) {
            return 0xFFFFA23A;
        }
        return 0xFFFF5555;
    }

    public static void renderHud(GuiGraphics g, Minecraft mc) {
        load();
        if (!visible || NEED.isEmpty() || mc.player == null || mc.options.hideGui) {
            return;
        }
        int x = 6;
        int y = 34;
        int shownRows = Math.min(14, NEED.size());
        int width = 150;
        g.fill(x - 3, y - 3, x + width, y + 12 + shownRows * 18 + (NEED.size() > shownRows ? 10 : 0), 0x66000000);
        g.drawString(mc.font, Component.translatable("insight_addons.list.title"), x, y, 0xFFFFD84A, true);
        y += 12;
        int i = 0;
        for (Map.Entry<Item, Integer> e : NEED.entrySet()) {
            if (i++ >= shownRows) {
                g.drawString(mc.font, Component.translatable("insight_addons.list.more", NEED.size() - shownRows), x, y, 0xFFB0B0B0, true);
                break;
            }
            ItemStack stack = new ItemStack(e.getKey());
            int have = owned(mc, e.getKey());
            int need = e.getValue();
            g.renderItem(stack, x, y);
            Component name = stack.getHoverName();
            String text = (have >= need ? "✓ " : "") + have + "/" + need;
            g.drawString(mc.font, name, x + 19, y, 0xFFFFFFFF, true);
            g.drawString(mc.font, text, x + 19, y + 9, ratioColor(have, need), true);
            y += 18;
        }
    }
}
