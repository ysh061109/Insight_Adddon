package dev.insightaddons.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * JEI/EMI 화면에서 마우스를 올려 둔 아이템을 계속 기억합니다. ("이 아이템 찾기" 버튼이 쓴다)
 * 버튼으로 마우스를 옮기는 동안 마지막으로 올렸던 아이템이 남아 있으므로, 아이템에 마우스를 올렸다가 버튼을 누르면 됩니다.
 * JEI/EMI 클래스는 직접 참조하지 않고(JEI는 플러그인이 넘겨 주는 함수, EMI는 리플렉션), 실패하면 조용히 무시합니다.
 */
public final class HoverTracker {
    private static Supplier<Item> jeiProbe;
    private static Item last;
    private static long lastTime;
    private static boolean emiFailed;
    private static Method emiHovered;

    private HoverTracker() {}

    public static void setJeiProbe(Supplier<Item> probe) {
        jeiProbe = probe;
    }

    public static Item last() {
        return last;
    }

    /** 최근(6초 안)에 마우스를 올렸던 아이템. 오래된 호버는 무시한다. */
    public static Item lastRecent() {
        return last != null && System.currentTimeMillis() - lastTime <= 6000 ? last : null;
    }

    public static void clear() {
        last = null;
    }

    /** 화면이 열려 있는 동안 매 틱 호출 */
    public static void tick(Minecraft mc) {
        if (mc.screen == null) {
            return;
        }
        Item found = null;
        try {
            if (jeiProbe != null) {
                found = jeiProbe.get();
            }
        } catch (Throwable ignored) {
            // JEI 화면이 아니면 값이 없을 수 있다
        }
        if (found == null) {
            found = emiHovered();
        }
        if (found != null) {
            last = found;
            lastTime = System.currentTimeMillis();
        }
    }

    private static Item emiHovered() {
        if (emiFailed) {
            return null;
        }
        try {
            if (emiHovered == null) {
                emiHovered = Class.forName("dev.emi.emi.api.EmiApi").getMethod("getHoveredStack", boolean.class);
            }
            Object interaction = emiHovered.invoke(null, true);
            if (interaction == null) {
                return null;
            }
            Object ingredient = interaction.getClass().getMethod("getStack").invoke(interaction);
            if (ingredient == null) {
                return null;
            }
            Object list = ingredient.getClass().getMethod("getEmiStacks").invoke(ingredient);
            if (list instanceof List<?> stacks && !stacks.isEmpty()) {
                Object first = stacks.get(0);
                Object item = first.getClass().getMethod("getItemStack").invoke(first);
                if (item instanceof ItemStack st && !st.isEmpty()) {
                    return st.getItem();
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            emiFailed = true;
        } catch (Throwable ignored) {
            // 호버 정보가 없는 프레임
        }
        return null;
    }
}
