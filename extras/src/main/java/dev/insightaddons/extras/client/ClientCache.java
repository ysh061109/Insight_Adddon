package dev.insightaddons.extras.client;

import dev.insightaddons.extras.net.ScanResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 표시할 데이터를 모읍니다. 메인 스레드에서만 접근합니다.
 * 서버에 이 모드가 있으면 실시간 데이터(near, serverWrench), 없으면 열어 본 보관함의 기억(ContainerMemory)을 씁니다.
 */
public final class ClientCache {
    /** 렌치 스캔: 아이템 종류별 합산과 그 아이템이 들어 있는 보관함 좌표들 */
    public record Agg(Item item, int count, List<BlockPos> sources) {}

    /**
     * 화면에 그릴 보관함 하나.
     *
     * @param ageTicks 기억된 데이터면 기록한 뒤 지난 틱, 실시간 데이터면 -1
     */
    public record View(BlockPos pos, ResourceLocation block, int total, int kinds,
                       List<ScanResult.Stack> stacks, List<Item> upgrades, long ageTicks) {}

    private static List<ScanResult.Entry> near = List.of();
    private static BlockPos target;
    private static List<ScanResult.Entry> serverWrench = List.of();
    private static List<Agg> wrench = List.of();
    private static List<View> wrenchViews = List.of();

    private ClientCache() {}

    public static List<Agg> wrench() {
        return wrench;
    }

    public static List<View> wrenchViews() {
        return wrenchViews;
    }

    public static void clearAll() {
        near = List.of();
        target = null;
        clearWrench();
    }

    /** 지금 시선이 닿은 보관함 (없으면 null) */
    public static BlockPos target() {
        return target;
    }

    public static void setTarget(BlockPos pos) {
        target = pos;
    }

    public static void clearNear() {
        near = List.of();
    }

    public static void clearWrench() {
        serverWrench = List.of();
        wrench = List.of();
        wrenchViews = List.of();
    }

    public static void accept(ScanResult result) {
        if (result.mode() == 0) {
            near = result.entries();
        } else if (result.mode() == 2) {
            RecipeFocus.acceptServer(result.entries());
        } else {
            serverWrench = result.entries();
        }
    }

    // ────────────────────────────────────────────────────────────
    // 바라보는 보관함 하나의 3D 표시용
    // ────────────────────────────────────────────────────────────
    /** 서버 실시간 데이터가 있으면 그것을, 없으면 기억을 돌려준다. 둘 다 없으면 null */
    public static View targetView(ClientLevel level, BlockPos pos) {
        ContainerMemory.Memo memo = ContainerMemory.get(pos);
        BlockPos partner = ContainerMemory.partnerOf(level.getBlockState(pos), pos);
        for (ScanResult.Entry e : near) {
            if (e.pos().equals(pos) || e.pos().equals(partner)) {
                return new View(pos, blockId(level, pos), e.total(), e.kinds(), e.stacks(),
                        memo == null ? List.of() : memo.upgrades, -1);
            }
        }
        if (memo != null) {
            return new View(pos, memo.block, memo.total, memo.kinds, memo.stacks, memo.upgrades,
                    level.getGameTime() - memo.time);
        }
        return null;
    }

    // ────────────────────────────────────────────────────────────
    // 렌치 스캔용: 서버 실시간 + 기억을 합쳐 아이템별로 합산
    // ────────────────────────────────────────────────────────────
    public static void rebuildWrench(ClientLevel level, Vec3 eye) {
        String ns = ClientEvents.wrenchNamespace();
        double radius = InsightConfig.WRENCH_RADIUS.get();
        double r2 = radius * radius;
        long now = level.getGameTime();

        Set<BlockPos> covered = new HashSet<>();
        List<View> views = new ArrayList<>();

        for (ScanResult.Entry e : serverWrench) {
            if (covered.contains(e.pos())) {
                continue;
            }
            covered.add(e.pos());
            BlockPos partner = ContainerMemory.partnerOf(level.getBlockState(e.pos()), e.pos());
            if (partner != null) {
                covered.add(partner);
            }
            ContainerMemory.Memo memo = ContainerMemory.get(e.pos());
            if (memo != null) {
                covered.addAll(memo.positions);
            }
            List<ScanResult.Stack> stacks = filter(e.stacks(), ns);
            if (stacks.isEmpty()) {
                continue;
            }
            views.add(new View(e.pos(), blockId(level, e.pos()), sum(stacks), stacks.size(), stacks, List.of(), -1));
        }

        for (ContainerMemory.Memo memo : ContainerMemory.memos()) {
            if (anyCovered(memo.positions, covered)) {
                continue;
            }
            List<ScanResult.Stack> stacks = filter(memo.stacks, ns);
            if (stacks.isEmpty()) {
                continue;
            }
            BlockPos anchor = nearest(memo.positions, eye);
            views.add(new View(anchor, memo.block, sum(stacks), stacks.size(), stacks, List.of(), now - memo.time));
        }

        views.removeIf(v -> Vec3.atCenterOf(v.pos()).distanceToSqr(eye) > r2);
        views.sort(Comparator.comparingDouble(v -> Vec3.atCenterOf(v.pos()).distanceToSqr(eye)));

        Map<Item, Integer> counts = new LinkedHashMap<>();
        Map<Item, List<BlockPos>> sources = new LinkedHashMap<>();
        for (View v : views) {
            for (ScanResult.Stack s : v.stacks()) {
                counts.merge(s.item(), s.count(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                sources.computeIfAbsent(s.item(), k -> new ArrayList<>()).add(v.pos());
            }
        }

        List<Agg> aggs = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            aggs.add(new Agg(e.getKey(), e.getValue(), List.copyOf(sources.get(e.getKey()))));
        }
        aggs.sort(Comparator.comparingInt(Agg::count).reversed());
        int maxItems = InsightConfig.WRENCH_MAX_ITEMS.get();
        wrench = aggs.size() > maxItems ? List.copyOf(aggs.subList(0, maxItems)) : List.copyOf(aggs);
        wrenchViews = views;
    }

    // ────────────────────────────────────────────────────────────
    private static List<ScanResult.Stack> filter(List<ScanResult.Stack> stacks, String namespace) {
        if (namespace.isEmpty()) {
            return stacks;
        }
        List<ScanResult.Stack> out = new ArrayList<>();
        for (ScanResult.Stack s : stacks) {
            if (BuiltInRegistries.ITEM.getKey(s.item()).getNamespace().equals(namespace)) {
                out.add(s);
            }
        }
        return out;
    }

    private static int sum(List<ScanResult.Stack> stacks) {
        long total = 0;
        for (ScanResult.Stack s : stacks) {
            total += s.count();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static boolean anyCovered(List<BlockPos> positions, Set<BlockPos> covered) {
        for (BlockPos p : positions) {
            if (covered.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos nearest(List<BlockPos> positions, Vec3 eye) {
        BlockPos best = positions.get(0);
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            double d = Vec3.atCenterOf(p).distanceToSqr(eye);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static ResourceLocation blockId(ClientLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }
}
