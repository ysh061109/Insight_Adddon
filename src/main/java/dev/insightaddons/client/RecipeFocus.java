package dev.insightaddons.client;

import dev.insightaddons.net.ScanResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JEI에서 "조합 재료 표시" 버튼을 눌렀을 때의 상태.
 * 재료마다 주변 보관함에 얼마나 있는지 집계해서, 플레이어 앞에 3D로 띄우고 있는 보관함을 표시하는 데 씁니다.
 * JEI 클래스에 의존하지 않으므로 JEI가 없어도 안전하게 로드됩니다.
 */
public final class RecipeFocus {
    /** 필요한 재료 하나. options는 태그 등으로 대체 가능한 아이템들 */
    public record Need(List<Item> options, int count) {}

    /** 재료를 가진 보관함 하나 */
    public record Source(BlockPos pos, ResourceLocation block, int count, long ageTicks) {}

    /** 재료 하나의 집계 결과. item은 options 중 가장 많이 가진 것 */
    public record Row(Need need, Item item, int have, int inv, List<Source> sources, int wireless, Item wirelessTerminal) {
        /** 인벤토리만으로 충분한가 */
        public boolean inventoryEnough() {
            return inv >= need.count();
        }
    }

    private record Holder(BlockPos pos, ResourceLocation block, Map<Item, Integer> counts, long age) {}

    private static boolean active;
    private static Component title = Component.empty();
    private static List<Need> needs = List.of();
    private static List<Row> rows = List.of();
    /** 3D로 띄울 재료: 보관함에 있고 인벤토리만으로는 모자란 것 */
    private static List<Row> shown = List.of();
    /** 인벤토리에 이미 충분히 있는 재료 (3D 대신 글씨로 알려 준다) */
    private static List<Row> inInventory = List.of();
    /** 인벤토리에도 보관함에도 없는 재료 */
    private static List<Row> missing = List.of();
    private static List<ScanResult.Entry> serverEntries = List.of();
    private static boolean serverAnswered;
    private static long expireTick = -1;
    /** 3D로 띄울 재료가 한 번이라도 있었는지. 있다가 전부 사라지면(꺼내 갔으면) 표시 모드를 끈다. */
    private static boolean hadShown;
    private static boolean reported;
    private static boolean reportedWireless;
    /** 재료를 다 챙겼을 때 잠깐 보여 주는 완료 연출 */
    private static final int FINISH_TICKS = 40;
    private static boolean finishing;
    private static long finishStart;
    private static boolean finishFx;
    private static List<Item> finishItems = List.of();
    /** 없는 재료의 경고 연출(소리, 파티클)을 이번 표시에서 이미 했는지 */
    private static boolean missingFx;

    private RecipeFocus() {}

    public static boolean isActive() {
        return active;
    }

    public static boolean finishing() {
        return finishing;
    }

    public static List<Item> finishItems() {
        return finishItems;
    }

    /** 완료 연출 진행도 0~1 */
    public static float finishProgress(ClientLevel level, float partial) {
        return Math.max(0f, Math.min(1f, (level.getGameTime() - finishStart + partial) / FINISH_TICKS));
    }

    /** 완료 연출의 파티클을 이번에 처음 내보내는지(한 번만 true) */
    public static boolean takeFinishFx() {
        if (finishFx) {
            return false;
        }
        finishFx = true;
        return true;
    }

    /** 없는 재료 경고 연출을 이번에 처음 하는지(한 번만 true) */
    public static boolean takeMissingFx() {
        if (missingFx) {
            return false;
        }
        missingFx = true;
        return true;
    }

    public static List<Row> rows() {
        return rows;
    }

    public static List<Row> shownRows() {
        return shown;
    }

    public static List<Row> inventoryRows() {
        return inInventory;
    }

    public static List<Row> missingRows() {
        return missing;
    }

    /** 지금 켜져 있는 재료 찾기가 필요로 하는 재료 목록 (재료 목록에 담을 때 쓴다) */
    public static List<Need> needs() {
        return active ? needs : List.of();
    }

    public static Component title() {
        return title;
    }

    /** 같은 대체 목록을 가진 재료는 합쳐서 시작한다. */
    public static void start(Component recipeTitle, List<Need> raw) {
        Map<List<Item>, Integer> merged = new LinkedHashMap<>();
        for (Need need : raw) {
            if (need.options().isEmpty()) {
                continue;
            }
            merged.merge(need.options(), Math.max(1, need.count()), Integer::sum);
        }
        if (merged.isEmpty()) {
            return;
        }
        List<Need> list = new ArrayList<>();
        for (Map.Entry<List<Item>, Integer> e : merged.entrySet()) {
            list.add(new Need(e.getKey(), e.getValue()));
        }
        needs = List.copyOf(list);
        title = recipeTitle;
        rows = List.of();
        shown = List.of();
        inInventory = List.of();
        missing = List.of();
        serverEntries = List.of();
        serverAnswered = false;
        expireTick = -1;
        hadShown = false;
        reported = false;
        reportedWireless = false;
        finishing = false;
        finishFx = false;
        missingFx = false;
        finishItems = List.of();
        selected = 0;
        active = true;
    }

    /**
     * JEI/EMI 버튼이 공통으로 쓰는 진입점: 이미 켜져 있으면 끄고, 아니면 이 재료로 시작한 뒤 화면을 닫는다.
     */
    public static void toggle(Component recipeTitle, List<Need> raw) {
        if (active) {
            clear();
            return;
        }
        start(recipeTitle, raw);
        if (active) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(null));
        }
    }

    /** "이 아이템 찾기" 버튼: 조합법과 상관없이 그 아이템 자체를 보관함에서 찾는다. */
    public static void toggleItem(Item item) {
        if (item == null) {
            return;
        }
        toggle(new net.minecraft.world.item.ItemStack(item).getHoverName(),
                List.of(new Need(List.of(item), 1)));
    }

    // ── 마우스 휠로 고르는 재료 ──
    private static int selected;

    public static int selectedIndex() {
        return shown.isEmpty() ? -1 : Math.floorMod(selected, shown.size());
    }

    /** 휠 한 칸당 +1/-1. 가진 재료 종류가 바뀌면 처음으로 돌아가지 않도록 그대로 둔다. */
    public static void scroll(int delta) {
        if (!shown.isEmpty()) {
            selected = Math.floorMod(selected + delta, shown.size());
            Fx.sound(net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.4f, 0.45f);
        }
    }

    public static void clear() {
        selected = 0;
        hadShown = false;
        reported = false;
        reportedWireless = false;
        finishing = false;
        finishFx = false;
        missingFx = false;
        finishItems = List.of();
        active = false;
        needs = List.of();
        rows = List.of();
        shown = List.of();
        inInventory = List.of();
        missing = List.of();
        serverEntries = List.of();
        serverAnswered = false;
        expireTick = -1;
    }

    /** 서버에 물어볼 아이템 ID들 (대체 가능한 것 포함) */
    public static List<ResourceLocation> wantedIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Need need : needs) {
            for (Item item : need.options()) {
                ids.add(BuiltInRegistries.ITEM.getKey(item));
            }
        }
        List<ResourceLocation> list = new ArrayList<>(ids);
        return list.size() > 64 ? new ArrayList<>(list.subList(0, 64)) : list;
    }

    public static void acceptServer(List<ScanResult.Entry> entries) {
        if (active) {
            serverEntries = entries;
            serverAnswered = true;
        }
    }

    /** 시간이 지나면 저절로 끈다. */
    public static void tick(ClientLevel level) {
        if (!active) {
            return;
        }
        long now = level.getGameTime();
        if (finishing) {
            if (now - finishStart >= FINISH_TICKS) {
                net.minecraft.world.entity.player.Player me = net.minecraft.client.Minecraft.getInstance().player;
                if (me != null) {
                    me.displayClientMessage(Component.translatable("insight_addons.recipe.done"), true);
                }
                clear();
            }
            return;
        }
        if (expireTick < 0) {
            expireTick = now + InsightConfig.RECIPE_SECONDS.get() * 20L;
        } else if (now > expireTick) {
            clear();
        }
    }

    /**
     * 서버가 답했으면 서버 데이터만 쓴다. (서버가 모든 보관함을 직접 읽으므로 가장 정확하고, 기억은 오래된 내용일 수 있다.)
     * 서버에 이 모드가 없으면 열어 본 보관함의 기억을 쓴다.
     */
    public static void rebuild(ClientLevel level, Vec3 eye) {
        if (!active || finishing) {
            return;
        }
        double radius = InsightConfig.RECIPE_RADIUS.get();
        double r2 = radius * radius;
        long now = level.getGameTime();

        List<Holder> holders = new ArrayList<>();
        if (serverAnswered) {
            Set<BlockPos> covered = new HashSet<>();
            for (ScanResult.Entry e : serverEntries) {
                if (!covered.add(e.pos())) {
                    continue;
                }
                BlockPos partner = ContainerMemory.partnerOf(level.getBlockState(e.pos()), e.pos());
                if (partner != null) {
                    covered.add(partner);
                }
                Map<Item, Integer> counts = new HashMap<>();
                for (ScanResult.Stack s : e.stacks()) {
                    counts.merge(s.item(), s.count(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                }
                holders.add(new Holder(e.pos(), blockId(level, e.pos()), counts, -1));
            }
        } else {
            for (ContainerMemory.Memo memo : ContainerMemory.memos()) {
                holders.add(new Holder(nearest(memo.positions, eye), memo.block, memo.all, now - memo.time));
            }
        }
        // AE2 단말기가 받아 둔 네트워크 목록(열려 있으면 최신, 닫았으면 마지막으로 본 내용)도 하나의 보관함처럼 취급한다.
        // 재료 찾기를 누르면 화면이 닫히므로, 열려 있을 때만 읽으면 ME 저장소가 항상 비어 보인다.
        net.minecraft.client.Minecraft mcc = net.minecraft.client.Minecraft.getInstance();
        AeTerminal.contents(mcc); // 열려 있으면 기억을 최신으로 갱신
        List<BlockPos> stale = new ArrayList<>();
        for (AeTerminal.Snapshot snap : AeTerminal.snapshots()) {
            if (level.isLoaded(snap.pos()) && !blockId(level, snap.pos()).getNamespace().equals("ae2")) {
                stale.add(snap.pos()); // 단말기를 치웠다
                continue;
            }
            if (snap.counts().isEmpty()) {
                continue;
            }
            // 같은 위치에 다른 경로(서버 조회, 보관함 기억)의 AE 항목이 있어도, 단말기가 받아 둔 실제 목록이 더 정확하므로 그것으로 바꾼다
            // (검색으로 걸러진 화면 칸만 본 기억이 "없음"으로 이기지 않게)
            holders.removeIf(h -> h.pos().equals(snap.pos()) && h.block().getNamespace().equals("ae2"));
            boolean openNow = GlassSkin.isAeTerminal(mcc.screen) && snap.pos().equals(AeTerminal.pos());
            holders.add(new Holder(snap.pos(), blockId(level, snap.pos()), snap.counts(), openNow ? -1 : now - snap.time()));
        }
        stale.forEach(AeTerminal::forget);
        holders.removeIf(h -> Vec3.atCenterOf(h.pos()).distanceToSqr(eye) > r2);
        holders.sort(Comparator.comparingDouble(h -> Vec3.atCenterOf(h.pos()).distanceToSqr(eye)));

        net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
        List<AeTerminal.WirelessSnap> wireless = InsightConfig.AE2_WIRELESS.get() ? AeTerminal.activeWireless(player) : List.of();
        int maxSources = InsightConfig.RECIPE_MAX_SOURCES.get();
        List<Row> out = new ArrayList<>();
        for (Need need : needs) {
            Item best = need.options().get(0);
            long bestScore = -1;
            int bestInv = 0;
            int bestTotal = 0;
            int bestWireless = 0;
            Item bestWirelessItem = null;
            List<Source> bestSources = List.of();
            for (Item option : need.options()) {
                long total = 0;
                List<Source> found = new ArrayList<>();
                for (Holder h : holders) {
                    Integer c = h.counts().get(option);
                    if (c != null && c > 0) {
                        total += c;
                        found.add(new Source(h.pos(), h.block(), c, h.age()));
                    }
                }
                int inv = player == null ? 0 : countInInventory(player, option);
                long wTotal = 0;
                Item wItem = null;
                for (AeTerminal.WirelessSnap snap : wireless) {
                    Integer wc = snap.counts().get(option);
                    if (wc != null && wc > 0) {
                        wTotal += wc;
                        wItem = snap.terminal();
                    }
                }
                long score = total + inv + wTotal;
                if (score > bestScore) {
                    bestScore = score;
                    best = option;
                    bestInv = inv;
                    bestTotal = (int) Math.min(Integer.MAX_VALUE, total);
                    bestSources = found;
                    bestWireless = (int) Math.min(Integer.MAX_VALUE, wTotal);
                    bestWirelessItem = wItem;
                }
            }
            if (bestSources.size() > maxSources) {
                bestSources = new ArrayList<>(bestSources.subList(0, maxSources));
            }
            out.add(new Row(need, best, bestTotal, bestInv, List.copyOf(bestSources), bestWireless, bestWirelessItem));
        }
        rows = List.copyOf(out);

        List<Row> a = new ArrayList<>();
        List<Row> b = new ArrayList<>();
        List<Row> c = new ArrayList<>();
        for (Row row : rows) {
            if (row.inventoryEnough()) {
                b.add(row);
            } else if (row.sources().isEmpty() && row.wireless() <= 0) {
                c.add(row);
            } else {
                a.add(row);
            }
        }
        shown = List.copyOf(a);
        inInventory = List.copyOf(b);
        missing = List.copyOf(c);

        // 서버 응답(또는 서버 모드가 없을 때의 기억)이 준비된 뒤에만 판단한다
        boolean ready = serverAnswered || !ClientEvents.serverHasMod();
        if (!ready || net.minecraft.client.Minecraft.getInstance().player == null) {
            return;
        }
        net.minecraft.world.entity.player.Player me = net.minecraft.client.Minecraft.getInstance().player;
        if (!reportedWireless) {
            for (Row row : shown) {
                if (row.wireless() > 0 && row.wirelessTerminal() != null) {
                    reportedWireless = true;
                    me.displayClientMessage(Component.translatable("insight_addons.recipe.wireless.notice",
                            new net.minecraft.world.item.ItemStack(row.wirelessTerminal()).getHoverName()), true);
                    break;
                }
            }
        }
        if (!shown.isEmpty()) {
            hadShown = true;
        } else if (hadShown && net.minecraft.client.Minecraft.getInstance().screen != null) {
            // 터미널·보관함 화면이 열려 있는 동안에는 3D 연출이 GUI에 가려지므로, 화면을 닫을 때까지 기다린다
            if (expireTick >= 0) {
                expireTick = Math.max(expireTick, now + 200);
            }
        } else if (hadShown) {
            // 보관함에서 다 꺼냈거나 인벤토리에 충분히 생겼다 -> 완료 연출을 잠깐 보여 준 뒤(tick에서) 표시 모드를 끈다
            List<Item> items = new ArrayList<>();
            for (Row row : rows) {
                items.add(row.item());
            }
            finishItems = items;
            finishing = true;
            finishStart = level.getGameTime();
            finishFx = false;
            Fx.sound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.3f, 0.7f);
            Fx.sound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.8f);
        } else if (!reported && !rows.isEmpty()) {
            reported = true;
            me.displayClientMessage(Component.translatable(inInventory.isEmpty()
                    ? "insight_addons.recipe.none.all" : "insight_addons.recipe.inv.all"), true);
            // 보여 줄 3D 재료가 없으면 안내만 잠깐 보여 주고 자동으로 끈다
            expireTick = Math.min(expireTick < 0 ? Long.MAX_VALUE : expireTick, level.getGameTime() + 160);
        }
    }

    private static int countInInventory(net.minecraft.world.entity.player.Player player, Item item) {
        int total = 0;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = inv.getItem(i);
            if (st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
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
