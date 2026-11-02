package dev.insightaddons.client;

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

/**
 * AE2 ME 단말기(조합 단말기, 패턴 인코딩 단말기 등)가 열려 있으면, 단말기가 클라이언트로 받아 둔 네트워크 목록을 읽습니다.
 * 서버 쪽 네트워크 조회가 안 되는 경우에도 JEI/EMI "재료 찾기"가 ME 저장소의 아이템을 알 수 있게 하는 보조 경로입니다.
 * 모두 리플렉션이며, 구조가 다르면 null을 돌려줍니다.
 */
public final class AeTerminal {
    private static BlockPos pos;
    private static boolean warned;

    /** 단말기를 닫은 뒤에도 재료 찾기가 쓸 수 있게, 단말기별로 마지막으로 본 네트워크 목록을 기억한다. */
    public record Snapshot(BlockPos pos, Map<Item, Integer> counts, long time) {}

    private static final Map<BlockPos, Snapshot> SNAPSHOTS = new HashMap<>();

    public static java.util.Collection<Snapshot> snapshots() {
        return SNAPSHOTS.values();
    }

    public static void forget(BlockPos p) {
        SNAPSHOTS.remove(p);
    }

    public static void clearSnapshots() {
        SNAPSHOTS.clear();
        pos = null;
    }

    /** 로그아웃: 무선 터미널 기억까지 모두 지운다 (차원 이동 때는 무선 기억을 남겨 둔다) */
    public static void clearAll() {
        clearSnapshots();
        WIRELESS.clear();
    }

    /** 무선 터미널로 마지막으로 본 네트워크 목록. link는 연결된 접속점 위치(없으면 빈 글자) */
    public record WirelessSnap(String link, Item terminal, Map<Item, Integer> counts, long time) {}

    private static final Map<String, WirelessSnap> WIRELESS = new HashMap<>();
    private static Method mGetHost;
    private static Method mItemStack;
    private static boolean wirelessWarned;

    /** 지금 열린 단말기 화면이 무선 터미널이면 그 아이템 스택, 아니면 null */
    private static net.minecraft.world.item.ItemStack openWirelessStack(Screen screen) {
        try {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs)) {
                return null;
            }
            Object menu = acs.getMenu();
            if (mGetHost == null || !mGetHost.getDeclaringClass().isInstance(menu)) {
                mGetHost = menu.getClass().getMethod("getHost");
            }
            Object host = mGetHost.invoke(menu);
            if (host == null) {
                return null;
            }
            boolean itemHost = false;
            for (Class<?> c = host.getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().equals("appeng.api.implementations.menuobjects.ItemMenuHost")) {
                    itemHost = true;
                    break;
                }
            }
            if (!itemHost) {
                return null;
            }
            if (mItemStack == null || !mItemStack.getDeclaringClass().isInstance(host)) {
                mItemStack = host.getClass().getMethod("getItemStack");
            }
            Object stack = mItemStack.invoke(host);
            return stack instanceof net.minecraft.world.item.ItemStack st && !st.isEmpty() ? st : null;
        } catch (Throwable t) {
            if (!wirelessWarned) {
                wirelessWarned = true;
                dev.insightaddons.InsightAddons.LOGGER.warn("무선 터미널 여부를 확인하지 못했습니다: {}", t.toString());
            }
            return null;
        }
    }

    /** 터미널 아이템이 연결된 접속점 위치 글자. 연결이 없으면 null */
    private static String linkOf(net.minecraft.world.item.ItemStack stack) {
        try {
            Method m = stack.getItem().getClass().getMethod("getLinkedPosition", net.minecraft.world.item.ItemStack.class);
            Object gp = m.invoke(stack.getItem(), stack);
            return gp == null ? null : String.valueOf(gp);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean charged(net.minecraft.world.item.ItemStack stack) {
        try {
            Method m = stack.getItem().getClass().getMethod("getAECurrentPower", net.minecraft.world.item.ItemStack.class);
            return ((Number) m.invoke(stack.getItem(), stack)).doubleValue() > 0;
        } catch (Throwable t) {
            return true; // 알 수 없으면 충전된 것으로 본다
        }
    }

    /** 인벤토리에 충전되고 연결된 무선 터미널이 아직 있는, 기억해 둔 네트워크들 (같은 네트워크는 한 번만) */
    public static java.util.List<WirelessSnap> activeWireless(net.minecraft.world.entity.player.Player player) {
        java.util.List<WirelessSnap> out = new java.util.ArrayList<>();
        if (player == null || WIRELESS.isEmpty()) {
            return out;
        }
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (WirelessSnap snap : WIRELESS.values()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = inv.getItem(i);
                if (st.isEmpty() || st.getItem() != snap.terminal()) {
                    continue;
                }
                String link = linkOf(st);
                if (link == null || !(snap.link().isEmpty() || snap.link().equals(link)) || !charged(st)) {
                    continue;
                }
                out.add(snap);
                break;
            }
        }
        return out;
    }

    private AeTerminal() {}

    /** 화면이 열리기 직전에 호출: 단말기 위치(바라보던 블록)를 기억한다. */
    public static void onOpening(Screen next, Minecraft mc) {
        if (!GlassSkin.isAeTerminal(next) || mc.player == null) {
            return;
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos().immutable();
            // 열 때 바라보던 부품 이름을 기억해 둔다 (시선을 뗀 뒤에도 "케이블 버스"가 아니라 부품 이름이 나오게)
            StorageRenderer.blockName(mc, net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(mc.level.getBlockState(pos).getBlock()), pos);
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
        if (pos == null || !GlassSkin.isAeTerminal(screen)) {
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
            if (!out.isEmpty() && mc.level != null) {
                net.minecraft.world.item.ItemStack wireless = openWirelessStack(screen);
                if (wireless != null) {
                    // 무선 터미널: 위치가 아니라 연결된 네트워크 기준으로 기억한다 (다른 차원에서도 쓸 수 있다)
                    String link = linkOf(wireless);
                    if (link != null) {
                        WIRELESS.put(link.isEmpty() ? "?" + wireless.getItem() : link,
                                new WirelessSnap(link, wireless.getItem(), Map.copyOf(out), mc.level.getGameTime()));
                    }
                } else {
                    SNAPSHOTS.put(pos, new Snapshot(pos, Map.copyOf(out), mc.level.getGameTime()));
                }
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
            dev.insightaddons.InsightAddons.LOGGER.warn("AE2 단말기 목록을 읽지 못했습니다: {}", why);
        }
        return null;
    }
}
