        // AE2 단말기의 슬롯은 화면에 보이는 칸의 "표시용 1개짜리 복사본"이라 개수가 모두 1로 잡히고 첫 페이지(18종)만 보인다.
        // 그래서 단말기 메뉴는 슬롯으로 기억하지 않고, 단말기가 받아 둔 실제 네트워크 목록(AeTerminal)으로만 기억한다.
        // 화면이 닫히는 순간의 마지막 기록은 화면이 이미 없어서 실제 목록을 읽을 수 없으므로, 열려 있을 때 만든 기록을 그대로 둔다.
        if (hasSuperclassNamed(menu.getClass(), "MEStorageMenu")) {
            net.minecraft.client.Minecraft mcNow = net.minecraft.client.Minecraft.getInstance();
            Map<Item, Integer> ae = GlassSkin.isAeTerminal(mcNow.screen) ? AeTerminal.contents(mcNow) : null;
            if (ae == null || ae.isEmpty()) {
                return;
            }
            totals.clear();
            sum = 0;
            for (Map.Entry<Item, Integer> e : ae.entrySet()) {
                totals.put(e.getKey(), (long) e.getValue());
                sum += e.getValue();
            }
        }

package dev.insightaddons.client;

import dev.insightaddons.net.ScanResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 보관함을 열 때 서버가 클라이언트에 보내주는 메뉴 슬롯을 읽어 위치별로 기억합니다.
 * 서버에 이 모드가 없어도 동작하는 이유입니다. (열린 동안은 실시간, 닫은 뒤에는 마지막 상태)
 */
public final class ContainerMemory {
    /** 보관함 하나의 기억. 더블 상자는 두 칸이 같은 Memo를 공유합니다. */
    public static final class Memo {
        public final ResourceLocation block;
        public final List<BlockPos> positions;
        public final long time;
        public final int total;
        public final int kinds;
        public final List<ScanResult.Stack> stacks;
        public final List<Item> upgrades;
        /** 개수 제한 없는 전체 내용물. 조합 재료 찾기에서 씁니다. */
        public final Map<Item, Integer> all;

        Memo(ResourceLocation block, List<BlockPos> positions, long time, int total, int kinds,
             List<ScanResult.Stack> stacks, List<Item> upgrades, Map<Item, Integer> all) {
            this.all = all;
            this.block = block;
            this.positions = positions;
            this.time = time;
            this.total = total;
            this.kinds = kinds;
            this.stacks = stacks;
            this.upgrades = upgrades;
        }
    }

    private static final int MAX_STACKS = 128;
    private static final Map<BlockPos, Memo> BY_POS = new HashMap<>();

    private static BlockPos clickPos;
    private static long clickTime;
    private static AbstractContainerMenu lastMenu;
    private static BlockPos openPos;
    private static long lastRecord;
    private static long lastCheck;

    private ContainerMemory() {}

    public static void onRightClick(BlockPos pos, long gameTime) {
        clickPos = pos;
        clickTime = gameTime;
    }

    /** 마지막으로 우클릭한 블록 (작업대 등 조합 연출의 목적지로 씀) */
    public static BlockPos lastClickPos() {
        return clickPos;
    }

    public static long lastClickTime() {
        return clickTime;
    }

    public static Memo get(BlockPos pos) {
        return BY_POS.get(pos);
    }

    /** 중복 없는 기억 목록 */
    public static Collection<Memo> memos() {
        Set<Memo> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(BY_POS.values());
        return unique;
    }

    public static void clear() {
        BY_POS.clear();
        clickPos = null;
        lastMenu = null;
        openPos = null;
    }

    public static void tick(Minecraft mc, ClientLevel level, LocalPlayer player) {
        if (!InsightConfig.MEMORY_ENABLED.get()) {
            return;
        }
        long now = level.getGameTime();

        if (mc.screen instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
            AbstractContainerMenu menu = screen.getMenu();
            if (menu instanceof InventoryMenu) {
                return;
            }
            if (menu != lastMenu) {
                lastMenu = menu;
                openPos = null;
                if (clickPos != null && now - clickTime <= 100) {
                    BlockState state = level.getBlockState(clickPos);
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (ContainerFilter.matches(id) && level.getBlockEntity(clickPos) != null) {
                        openPos = clickPos;
                    }
                }
                lastRecord = 0;
            }
            if (openPos != null && now - lastRecord >= 5) {
                lastRecord = now;
                record(level, player, menu, openPos);
            }
        } else if (lastMenu != null) {
            // 화면을 닫는 순간 마지막 상태를 한 번 더 기록
            if (openPos != null) {
                record(level, player, lastMenu, openPos);
            }
            lastMenu = null;
            openPos = null;
        }

        // 블록이 부서지거나 바뀌었으면 기억을 지운다 (2초마다)
        if (now - lastCheck >= 40) {
            lastCheck = now;
            List<BlockPos> remove = new ArrayList<>();
            for (Map.Entry<BlockPos, Memo> e : BY_POS.entrySet()) {
                BlockPos pos = e.getKey();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                if (!id.equals(e.getValue().block)) {
                    remove.add(pos);
                }
            }
            for (BlockPos pos : remove) {
                BY_POS.remove(pos);
            }
        }
    }

    private static void record(ClientLevel level, LocalPlayer player, AbstractContainerMenu menu, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        Set<Slot> upgradeSlots = Collections.newSetFromMap(new IdentityHashMap<>());
        upgradeSlots.addAll(findUpgradeSlots(menu));
        boolean sophisticated = isSophisticatedMenu(menu);

        Map<Item, Long> totals = new LinkedHashMap<>();
        List<Item> upgrades = new ArrayList<>();
        long sum = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (upgradeSlots.contains(slot)) {
                upgrades.add(stack.getItem());
                continue;
            }
            // Sophisticated 메뉴에는 업그레이드 탭의 필터/제작 슬롯도 섞여 있어서 보관 슬롯만 센다
            if (sophisticated && !hasSuperclassNamed(slot.getClass(), "StorageInventorySlot")) {
                continue;
            }
            totals.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            sum += stack.getCount();
        }

        // AE2 단말기의 슬롯은 화면에 보이는 칸의 "표시용 1개짜리 복사본"이라 개수가 모두 1로 잡히고 첫 페이지만 보인다.
        // 단말기가 받아 둔 실제 네트워크 목록(AeTerminal)이 있으면 그것으로 바꿔 쓴다.
        net.minecraft.client.Minecraft mcNow = net.minecraft.client.Minecraft.getInstance();
        if (GlassSkin.isAeTerminal(mcNow.screen)) {
            Map<Item, Integer> ae = AeTerminal.contents(mcNow);
            if (ae == null || ae.isEmpty()) {
                // 실제 목록을 아직 못 읽었다. 화면에 보이는 칸(검색 결과로 걸러진 일부)으로 기억하면 틀리므로 기록하지 않는다.
                return;
            }
            {
                totals.clear();
                sum = 0;
                for (Map.Entry<Item, Integer> e : ae.entrySet()) {
                    totals.put(e.getKey(), (long) e.getValue());
                    sum += e.getValue();
                }
            }
        }

        List<ScanResult.Stack> stacks = totals.entrySet().stream()
                .sorted(Map.Entry.<Item, Long>comparingByValue().reversed())
                .limit(MAX_STACKS)
                .map(e -> new ScanResult.Stack(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue())))
                .toList();

        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos.immutable());
        BlockPos partner = partnerOf(state, pos);
        if (partner != null) {
            positions.add(partner);
        }

        Map<Item, Integer> all = new HashMap<>();
        for (Map.Entry<Item, Long> e : totals.entrySet()) {
            all.put(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()));
        }

        Memo memo = new Memo(blockId, List.copyOf(positions), level.getGameTime(),
                (int) Math.min(Integer.MAX_VALUE, sum), totals.size(), stacks, List.copyOf(upgrades), Map.copyOf(all));
        for (BlockPos p : positions) {
            BY_POS.put(p, memo);
        }
    }

    /** 바닐라 더블 상자의 반대쪽 칸. 아니면 null */
    public static BlockPos partnerOf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock
                && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
        }
        return null;
    }

    private static boolean isSophisticatedMenu(AbstractContainerMenu menu) {
        return menu.getClass().getName().startsWith("net.p3pp3rf1y.sophisticated");
    }

    private static boolean hasSuperclassNamed(Class<?> type, String simpleName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    /** Sophisticated 메뉴의 public 필드 upgradeSlots를 리플렉션으로 읽는다. 없으면 빈 목록 */
    private static List<Slot> findUpgradeSlots(AbstractContainerMenu menu) {
        for (Class<?> c = menu.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("upgradeSlots");
                field.setAccessible(true);
                Object value = field.get(menu);
                if (value instanceof List<?> list) {
                    List<Slot> out = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof Slot s) {
                            out.add(s);
                        }
                    }
                    return out;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 다음 상위 클래스에서 다시 찾는다
            }
        }
        return List.of();
    }

    /** 가까운 순 정렬용 */
    public static Comparator<BlockPos> byDistance(BlockPos origin) {
        return Comparator.comparingDouble(p -> p.distSqr(origin));
    }
}
