package dev.insightaddons.extras;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applied Energistics 2 연동. AE2에 컴파일 의존성을 두지 않고 리플렉션으로만 접근하므로,
 * AE2가 없거나 API가 달라도 조용히 꺼집니다(StorageAccess는 기존 방식으로 동작).
 *
 * ME 상자/ME 드라이브/ME 인터페이스 블록을 바라보면 그 ME 저장소(상자는 셀, 나머지는 연결된 네트워크 전체)의
 * 내용물을 IItemHandler처럼 보여줍니다. 꺼내기는 네트워크에서 직접 꺼내며 넣기는 지원합니다.
 */
public final class AeBridge {
    /** 지원하는 AE2 블록 (경로 기준) */
    public static final Set<String> BLOCKS = Set.of("chest", "drive", "interface", "cable_bus");

    private static Boolean ready;
    private static BlockCapability<Object, Direction> capStorage;
    private static BlockCapability<Object, Direction> capHost;
    private static Method hostGetNode;       // IInWorldGridNodeHost.getGridNode(Direction)
    private static Method nodeGetGrid;       // IGridNode.getGrid()
    private static Method gridGetStorage;    // IGrid.getStorageService()
    private static Method serviceGetInv;     // IStorageService.getInventory()
    private static Method meAvailable;       // MEStorage.getAvailableStacks(KeyCounter)
    private static Method meExtract;         // MEStorage.extract(AEKey,long,Actionable,IActionSource)
    private static Method meInsert;          // MEStorage.insert(AEKey,long,Actionable,IActionSource)
    private static Class<?> keyCounterClass;
    private static Class<?> itemKeyClass;
    private static Method itemKeyOf;         // AEItemKey.of(ItemStack)
    private static Method itemKeyToStack;    // AEItemKey.toStack(int)
    private static Object simulate;
    private static Object modulate;
    private static Object emptySource;

    private static final java.util.Map<Object, MeHandler> CACHE = new java.util.IdentityHashMap<>();
    private static long cacheTick = -1;
    private static Level cacheLevel;

    private AeBridge() {}

    public static boolean isAeBlock(ResourceLocation id) {
        return id.getNamespace().equals("ae2") && BLOCKS.contains(id.getPath());
    }

    @SuppressWarnings("unchecked")
    private static boolean init() {
        if (ready != null) {
            return ready;
        }
        ready = false;
        if (!ModList.get().isLoaded("ae2")) {
            return false;
        }
        try {
            Class<?> caps = Class.forName("appeng.api.AECapabilities");
            capStorage = (BlockCapability<Object, Direction>) caps.getField("ME_STORAGE").get(null);
            try {
                capHost = (BlockCapability<Object, Direction>) caps.getField("IN_WORLD_GRID_NODE_HOST").get(null);
            } catch (Throwable ignored) {
                capHost = null;
            }
            Class<?> host = Class.forName("appeng.api.networking.IInWorldGridNodeHost");
            Class<?> node = Class.forName("appeng.api.networking.IGridNode");
            Class<?> grid = Class.forName("appeng.api.networking.IGrid");
            Class<?> service = Class.forName("appeng.api.networking.storage.IStorageService");
            Class<?> me = Class.forName("appeng.api.storage.MEStorage");
            Class<?> key = Class.forName("appeng.api.stacks.AEKey");
            Class<?> actionable = Class.forName("appeng.api.config.Actionable");
            Class<?> source = Class.forName("appeng.api.networking.security.IActionSource");
            keyCounterClass = Class.forName("appeng.api.stacks.KeyCounter");
            itemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");

            hostGetNode = host.getMethod("getGridNode", Direction.class);
            nodeGetGrid = node.getMethod("getGrid");
            gridGetStorage = grid.getMethod("getStorageService");
            serviceGetInv = service.getMethod("getInventory");
            meAvailable = me.getMethod("getAvailableStacks", keyCounterClass);
            meExtract = me.getMethod("extract", key, long.class, actionable, source);
            meInsert = me.getMethod("insert", key, long.class, actionable, source);
            itemKeyOf = itemKeyClass.getMethod("of", ItemStack.class);
            itemKeyToStack = itemKeyClass.getMethod("toStack", int.class);
            simulate = actionable.getField("SIMULATE").get(null);
            modulate = actionable.getField("MODULATE").get(null);
            emptySource = source.getMethod("empty").invoke(null);
            ready = true;
        } catch (Throwable t) {
            InsightAddons.LOGGER.warn("AE2 연동을 초기화하지 못해 끕니다: {}", t.toString());
            ready = false;
        }
        return ready;
    }

    /**
     * (클라이언트) 케이블 블록에 단말기 계열 부품이 붙어 있는지. 케이블마다 네트워크 전체를 띄우면 너무 어수선하므로
     * 단말기가 붙은 케이블만 대상으로 삼는다. 알아낼 수 없으면 false.
     */
    public static boolean hasTerminal(net.minecraft.world.level.block.entity.BlockEntity be) {
        try {
            Method getPart = be.getClass().getMethod("getPart", Direction.class);
            for (Direction d : Direction.values()) {
                Object part = getPart.invoke(be, d);
                if (part == null) {
                    continue;
                }
                Object item = part.getClass().getMethod("getPartItem").invoke(part);
                if (item instanceof net.minecraft.world.level.ItemLike like) {
                    String path = BuiltInRegistries.ITEM.getKey(like.asItem()).getPath();
                    if (path.contains("terminal") || path.contains("pattern_access")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // API가 다르면 지원하지 않음
        }
        return false;
    }

    /** 위치의 AE2 블록에서 ME 저장소를 찾아 IItemHandler로 감싸 돌려줍니다. 못 찾으면 null */
    @Nullable
    public static IItemHandler handler(Level level, BlockPos pos) {
        if (!init()) {
            return null;
        }
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            boolean chest = id.getPath().equals("chest");
            Object storage = chest ? cellStorage(level, pos) : null;
            if (storage == null) {
                storage = networkStorage(level, pos);
            }
            if (storage == null && !chest) {
                storage = cellStorage(level, pos);
            }
            if (storage == null && failLogs++ < 3) {
                InsightAddons.LOGGER.warn("AE2 블록 {}({})에서 ME 저장소/네트워크를 찾지 못했습니다", pos, id);
            }
            if (storage == null) {
                return null;
            }
            // 같은 네트워크를 여러 블록이 가리켜도 한 번만 읽도록 같은 틱 안에서는 재사용한다
            long now = level.getGameTime();
            if (now != cacheTick || level != cacheLevel) {
                CACHE.clear();
                cacheTick = now;
                cacheLevel = level;
            }
            MeHandler h = CACHE.get(storage);
            if (h == null) {
                h = new MeHandler(storage);
                CACHE.put(storage, h);
                if (okLogs++ < 3) {
                    InsightAddons.LOGGER.info("AE2 저장소 읽기 성공 ({}, {}): {}종", pos, id, h.getSlots());
                }
            }
            return h;
        } catch (Throwable t) {
            if (failLogs++ < 3) {
                InsightAddons.LOGGER.warn("AE2 저장소를 읽지 못했습니다 ({}): {}", pos, t.toString());
            }
            return null;
        }
    }

    private static int failLogs;
    private static int okLogs;

    @Nullable
    private static Object cellStorage(Level level, BlockPos pos) {
        Object s = level.getCapability(capStorage, pos, null);
        if (s != null) {
            return s;
        }
        for (Direction d : Direction.values()) {
            s = level.getCapability(capStorage, pos, d);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    private static Object networkStorage(Level level, BlockPos pos) throws Exception {
        if (capHost == null) {
            return null;
        }
        List<Direction> sides = new ArrayList<>();
        sides.add(null);
        sides.addAll(List.of(Direction.values()));
        for (Direction d : sides) {
            Object host = level.getCapability(capHost, pos, d);
            if (host == null) {
                continue;
            }
            for (Direction s : Direction.values()) {
                Object node = hostGetNode.invoke(host, s);
                if (node == null) {
                    continue;
                }
                Object grid = nodeGetGrid.invoke(node);
                if (grid == null) {
                    continue;
                }
                return serviceGetInv.invoke(gridGetStorage.invoke(grid));
            }
        }
        return null;
    }

    /** MEStorage 스냅샷을 슬롯 목록처럼 보여주는 IItemHandler. 만든 시점의 내용을 기준으로 합니다. */
    private static final class MeHandler implements IItemHandler {
        private final Object storage;
        private final List<Object> keys = new ArrayList<>();
        private final NonNullList<ItemStack> stacks = NonNullList.create();

        MeHandler(Object storage) throws Exception {
            this.storage = storage;
            Object counter = keyCounterClass.getConstructor().newInstance();
            meAvailable.invoke(storage, counter);
            for (Object o : (Iterable<?>) counter) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object key = e.getKey();
                if (!itemKeyClass.isInstance(key)) {
                    continue;
                }
                long amount = ((Number) e.getValue()).longValue();
                if (amount <= 0) {
                    continue;
                }
                ItemStack stack = (ItemStack) itemKeyToStack.invoke(key, (int) Math.min(Integer.MAX_VALUE, amount));
                if (!stack.isEmpty()) {
                    keys.add(key);
                    stacks.add(stack);
                }
            }
        }

        @Override
        public int getSlots() {
            return stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean sim) {
            if (stack.isEmpty()) {
                return stack;
            }
            try {
                Object key = itemKeyOf.invoke(null, stack);
                long in = ((Number) meInsert.invoke(storage, key, (long) stack.getCount(), sim ? simulate : modulate, emptySource)).longValue();
                if (in <= 0) {
                    return stack;
                }
                ItemStack rest = stack.copy();
                rest.shrink((int) Math.min(in, stack.getCount()));
                return rest;
            } catch (Throwable t) {
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean sim) {
            if (slot < 0 || slot >= keys.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            try {
                long got = ((Number) meExtract.invoke(storage, keys.get(slot), (long) amount, sim ? simulate : modulate, emptySource)).longValue();
                if (got <= 0) {
                    return ItemStack.EMPTY;
                }
                return (ItemStack) itemKeyToStack.invoke(keys.get(slot), (int) Math.min(got, amount));
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
