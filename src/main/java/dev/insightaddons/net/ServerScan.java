package dev.insightaddons.net;

import dev.insightaddons.StorageAccess;
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

    private ServerScan() {}

    public static void handle(ScanRequest req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> scan(player, req));
    }

    static void scan(ServerPlayer player, ScanRequest req) {
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
        for (IItemHandler handler : order) {
            ScanResult.Entry entry = summarize(handler, groups.get(handler), player, ns, wanted);
            if (entry != null) {
                entries.add(entry);
            }
        }
        PacketDistributor.sendToPlayer(player, new ScanResult(req.mode(), entries));
    }

    private static ScanResult.Entry summarize(IItemHandler handler, List<BlockPos> members, ServerPlayer player,
                                              String ns, Set<Item> wanted) {
        Map<Item, Long> totals = new LinkedHashMap<>();
        long sum = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (wanted != null && !wanted.contains(item)) {
                continue;
            }
            if (!ns.isEmpty() && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ns)) {
                continue;
            }
            totals.merge(item, (long) stack.getCount(), Long::sum);
            sum += stack.getCount();
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
