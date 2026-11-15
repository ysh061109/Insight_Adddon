package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** AE2 ME 단말기가 클라이언트로 받아 둔 네트워크 목록을 읽습니다(서버룸 연출용). 모두 리플렉션이며 구조가 다르면 null. */
public final class AeTerminal {
    private static final Map<Class<?>, Boolean> SCREEN_CACHE = new HashMap<>();
    private static BlockPos pos;
    private static boolean warned;

    private AeTerminal() {}

    public static boolean isTerminalScreen(Object screen) {
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
                || !net.neoforged.fml.ModList.get().isLoaded("ae2")) {
            return false;
        }
        return SCREEN_CACHE.computeIfAbsent(screen.getClass(), k -> {
            for (Class<?> x = k; x != null; x = x.getSuperclass()) {
                if (x.getName().equals("appeng.client.gui.me.common.MEStorageScreen")) {
                    return true;
                }
            }
            return false;
        });
    }

    /** 화면이 열리기 직전에 호출: 단말기 위치(바라보던 블록)를 기억한다. */
    public static void onOpening(Screen next, Minecraft mc) {
        if (!isTerminalScreen(next) || mc.player == null) {
            return;
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos().immutable();
        } else {
            pos = mc.player.blockPosition();
        }
    }

    public static BlockPos pos() {
        return pos;
    }

    /** 열려 있는 단말기의 네트워크 내용(아이템별 개수). 단말기가 아니거나 읽을 수 없으면 null */
    public static Map<Item, Integer> contents(Minecraft mc) {
        Screen screen = mc.screen;
        if (pos == null || !AeTerminal.isTerminalScreen(screen)) {
            return null;
        }
        try {
            Object repo = null;
            if (repoClass != screen.getClass()) {
                repoField = null;
                repoClass = screen.getClass();
                for (Class<?> c = screen.getClass(); c != null && repoField == null; c = c.getSuperclass()) {
                    try {
                        Field f = c.getDeclaredField("repo");
                        f.setAccessible(true);
                        repoField = f;
                    } catch (NoSuchFieldException ignored) {
                        // 상위 클래스에서 계속 찾는다
                    }
                }
            }
            if (repoField != null) {
                repo = repoField.get(screen);
            }
            if (repo == null) {
                return warn("repo 필드를 찾지 못했습니다");
            }
            if (mAll == null || mAll.getDeclaringClass() != repo.getClass() && !mAll.getDeclaringClass().isInstance(repo)) {
                mAll = repo.getClass().getMethod("getAllEntries");
                mWhat = null;
            }
            Collection<?> entries = (Collection<?>) mAll.invoke(repo);
            Map<Item, Integer> out = new HashMap<>();
            for (Object e : entries) {
                if (mWhat == null) {
                    mWhat = e.getClass().getMethod("getWhat");
                    mAmount = e.getClass().getMethod("getStoredAmount");
                }
                Object what = mWhat.invoke(e);
                if (what == null) {
                    continue;
                }
                if (mItem == null || !mItem.getDeclaringClass().isInstance(what)) {
                    if (!what.getClass().getName().equals("appeng.api.stacks.AEItemKey")) {
                        continue;
                    }
                    mItem = what.getClass().getMethod("getItem");
                }
                long amount = ((Number) mAmount.invoke(e)).longValue();
                if (amount <= 0) {
                    continue;
                }
                Item item = (Item) mItem.invoke(what);
                out.merge(item, (int) Math.min(Integer.MAX_VALUE, amount), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
            return out;
        } catch (Throwable t) {
            return warn(t.toString());
        }
    }

    private static Class<?> repoClass;
    private static Field repoField;
    private static Method mAll;
    private static Method mWhat;
    private static Method mAmount;
    private static Method mItem;

    private static Map<Item, Integer> warn(String why) {
        if (!warned) {
            warned = true;
            InsightAddons.LOGGER.warn("AE2 단말기 목록을 읽지 못했습니다: {}", why);
        }
        return null;
    }
}
