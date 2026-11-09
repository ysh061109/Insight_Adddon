package dev.insightaddons.extras.net;

import dev.insightaddons.extras.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 서버 측: 요청받은 좌표의 IItemHandler를 읽어 요약해서 돌려보냅니다. */
public final class ServerScan {

    private static final int MAX_POSITIONS = 160;
    private static final int MAX_STACKS_PER_ENTRY = 128;
    private static final int MAX_WANTED = 64;
    private static final double NEAR_RANGE = 24.0;
    private static final double WRENCH_RANGE = 64.0;

    // 서버 부하를 줄이기 위한 제한: 플레이어별 최소 간격, 한 요청당 처리 시간 예산, 보관함 요약 캐시
    private static final int[] MIN_INTERVAL = {4, 12, 4};
    private static final long BUDGET_NANOS = 3_000_000L;
    private static final int MAX_SLOTS = 2048;
    private static final int CACHE_TICKS = 10;
    private static final int CACHE_MAX = 4096;

    private record Cached(long time, Map<Item, Long> totals) {}

    private static final Map<String, Cached> CACHE = new java.util.HashMap<>();
    private static final Map<java.util.UUID, int[]> LAST = new java.util.HashMap<>();

    private ServerScan() {}

    /** 로그아웃한 플레이어의 요청 기록 정리 */
    public static void forget(java.util.UUID id) {
        LAST.remove(id);
    }

    public static void handle(ScanRequest req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> scan(player, req));
    }

    static void scan(ServerPlayer player, ScanRequest req) {
        int m = Math.max(0, Math.min(2, req.mode()));
        int[] last = LAST.computeIfAbsent(player.getUUID(), k -> new int[]{-1000, -1000, -1000});
        if (player.tickCount - last[m] < MIN_INTERVAL[m]) {
            return;  // 너무 자주 온 요청은 무시 (클라이언트는 이전 결과를 계속 쓴다)
        }
        last[m] = player.tickCount;
        boolean wrenchMode = req.mode() == 1;
        boolean recipeMode = req.mode() == 2;
        // 렌치 모드는 실제로 렌치를 들고 있을 때만 응답 (임의 좌표 스캔 남용 방지)
        if (wrenchMode && !holdsWrench(player, req.namespace())) {
            PacketDistributor.sendToPlayer(player, new ScanResult(1, List.of()));
            return;
        }

        // 조합 재료 모드는 찾을 아이템 목록이 있어야 하고, 그 아이템만 응답한다
        Set<Item> wanted = null;
        if (recipeMode) {
            wanted = new HashSet<>();
            for (ResourceLocation id : req.items()) {
                if (wanted.size() >= MAX_WANTED) {
                    break;
                }
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(wanted::add);
            }
            if (wanted.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new ScanResult(2, List.of()));
                return;
            }
        }

        ServerLevel level = player.serverLevel();
        double range = (wrenchMode || recipeMode) ? WRENCH_RANGE : NEAR_RANGE;
        double range2 = range * range;
        String ns = wrenchMode ? req.namespace() : "";

        // 같은 IItemHandler를 공유하는 블록들(금고 멀티블록, 더블 상자)은 하나로 묶는다.
        Map<IItemHandler, List<BlockPos>> groups = new IdentityHashMap<>();
        List<IItemHandler> order = new ArrayList<>();

        int count = 0;
        for (BlockPos pos : req.positions()) {
            if (++count > MAX_POSITIONS) {
                break;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range2) {
                continue;
            }
            IItemHandler handler = StorageAccess.find(level, pos);
            if (handler == null) {
                continue;
            }
            List<BlockPos> members = groups.get(handler);
            if (members == null) {
                members = new ArrayList<>();
                groups.put(handler, members);
                order.add(handler);
            }
            members.add(pos);
        }

        List<ScanResult.Entry> entries = new ArrayList<>();
        long startNanos = System.nanoTime();
        if (CACHE.size() > CACHE_MAX) {
            CACHE.clear();
        }
        for (IItemHandler handler : order) {
            if (System.nanoTime() - startNanos > BUDGET_NANOS) {
                break;  // 시간 예산 초과: 가까운 보관함부터 처리했으므로 나머지는 다음 요청에서
            }
            ScanResult.Entry entry = summarize(handler, groups.get(handler), player, ns, wanted);
            if (entry != null) {
                entries.add(entry);
            }
        }
        PacketDistributor.sendToPlayer(player, new ScanResult(req.mode(), entries));
    }

    private static ScanResult.Entry summarize(IItemHandler handler, List<BlockPos> members, ServerPlayer player,
                                              String ns, Set<Item> wanted) {
        BlockPos first = members.get(0);
        String key = player.serverLevel().dimension().location() + "@" + first.asLong();
        long now = player.serverLevel().getGameTime();
        Cached cached = CACHE.get(key);
        Map<Item, Long> base;
        if (cached != null && now - cached.time() >= 0 && now - cached.time() < CACHE_TICKS) {
            base = cached.totals();
        } else {
            base = new LinkedHashMap<>();
            int slots = Math.min(handler.getSlots(), MAX_SLOTS);
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    base.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
            CACHE.put(key, new Cached(now, base));
        }
        Map<Item, Long> totals = new LinkedHashMap<>();
        long sum = 0;
        for (Map.Entry<Item, Long> e : base.entrySet()) {
            Item item = e.getKey();
            if (wanted != null && !wanted.contains(item)) {
                continue;
            }
            if (!ns.isEmpty() && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ns)) {
                continue;
            }
            totals.put(item, e.getValue());
            sum += e.getValue();
        }
        if (totals.isEmpty()) {
            return null;
        }

        BlockPos anchor = members.stream()
                .min(Comparator.comparingDouble(p -> player.distanceToSqr(Vec3.atCenterOf(p))))
                .orElse(members.get(0));

        List<ScanResult.Stack> stacks = totals.entrySet().stream()
                .sorted(Map.Entry.<Item, Long>comparingByValue().reversed())
                .limit(wanted != null ? MAX_WANTED : MAX_STACKS_PER_ENTRY)
                .map(e -> new ScanResult.Stack(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue())))
                .toList();

        return new ScanResult.Entry(anchor.immutable(), (int) Math.min(Integer.MAX_VALUE, sum), totals.size(), stacks);
    }

    /**
     * 렌치 도구를 들고 있을 때만 응답한다(임의 좌표 스캔 남용 방지).
     * 서버는 클라이언트 설정을 읽지 않으므로, 알려진 도구이거나 이름에 wrench/configurator가 들어간 도구,
     * 또는 요청한 모드 ID와 같은 모드의 아이템이면 렌치로 인정한다.
     */
    private static boolean holdsWrench(ServerPlayer player, String ns) {
        return isWrench(player.getMainHandItem(), ns) || isWrench(player.getOffhandItem(), ns);
    }

    private static boolean isWrench(ItemStack stack, String ns) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return KNOWN_TOOLS.contains(id.toString()) || path.contains("wrench") || path.contains("configurator")
                || (!ns.isEmpty() && id.getNamespace().equals(ns));
    }

    private static final Set<String> KNOWN_TOOLS = Set.of(
            "create:wrench", "mekanism:configurator", "ae2:certus_quartz_wrench", "ae2:nether_quartz_wrench");
}
