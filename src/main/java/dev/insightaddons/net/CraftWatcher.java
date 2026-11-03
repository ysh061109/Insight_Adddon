package dev.insightaddons.net;

import dev.insightaddons.InsightAddons;
import dev.insightaddons.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 서버 측: 누군가 작업대(조합대, 대장장이 작업대, 모루, 석재절단기, 베틀, 지도 제작대, 숫돌)에서 결과를 꺼내면
 * 근처 플레이어에게 "재료가 날아가는 연출" 정보를 보냅니다. (이 모드가 설치된 클라이언트만 받습니다)
 *
 * 재료가 플레이어 인벤토리에 있으면 플레이어에게서, 없고 작업대 근처 보관함에 있으면 그 보관함에서 날아오는 것으로 표시합니다.
 * 이 모드는 서버에 따로 아무것도 바꾸지 않고, 위치 정보만 보냅니다.
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID)
public final class CraftWatcher {
    private static final double BROADCAST_RANGE = 48.0;
    private static final int SEARCH_RADIUS = 8;
    private static final int COOLDOWN_TICKS = 8;

    private record Snap(int menuId, List<ItemStack> inputs, ItemStack result, int resultHeld) {}

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private CraftWatcher() {}

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.containerMenu instanceof InventoryMenu) {
            return;
        }
        Container grid = event.getInventory();
        Set<Item> items = new LinkedHashSet<>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
        broadcast(player, items);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!supported(menu)) {
            SNAPS.remove(player.getUUID());
            watchInventory(player, menu);
            return;
        }
        INV.remove(player.getUUID());
        List<Slot> own = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                own.add(slot);
            }
        }
        if (own.size() < 2) {
            return;
        }
        Slot resultSlot = own.get(own.size() - 1);
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < own.size() - 1; i++) {
            inputs.add(own.get(i).getItem().copy());
        }
        ItemStack result = resultSlot.getItem().copy();

        Snap prev = SNAPS.get(player.getUUID());
        if (prev != null && prev.menuId() == menu.containerId && !prev.result().isEmpty()
                && prev.inputs().size() == inputs.size()) {
            int heldNow = held(player, menu, prev.result().getItem());
            boolean gained = heldNow > prev.resultHeld();
            if (gained) {
                Set<Item> consumed = new LinkedHashSet<>();
                for (int i = 0; i < inputs.size(); i++) {
                    ItemStack before = prev.inputs().get(i);
                    ItemStack after = inputs.get(i);
                    if (!before.isEmpty() && (after.isEmpty() || after.getCount() < before.getCount()
                            || !ItemStack.isSameItem(before, after))) {
                        consumed.add(before.getItem());
                    }
                }
                if (!consumed.isEmpty()) {
                    broadcast(player, consumed);
                }
            }
        }
        SNAPS.put(player.getUUID(), new Snap(menu.containerId, inputs, result,
                result.isEmpty() ? 0 : held(player, menu, result.getItem())));
    }

    private static final Map<UUID, Map<Item, Integer>> INV = new HashMap<>();

    /** 총기 제작대(TaCZ 등)처럼 이벤트 없이 인벤토리에서 재료를 빼 결과물을 주는 제작 메뉴: 인벤토리 변화로 감지 */
    private static boolean customCrafter(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu || menu instanceof net.minecraft.world.inventory.CraftingMenu) {
            return false;
        }
        String n = menu.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("net.minecraft.") || n.startsWith("appeng.")) {
            return false;
        }
        return n.contains("sophisticated") || n.contains("tacz") || n.contains("smith") || n.contains("workbench") || n.contains("craft")
                || n.contains("assembl") || n.contains("fabricat");
    }

    private static void watchInventory(ServerPlayer player, AbstractContainerMenu menu) {
        if (!customCrafter(menu)) {
            INV.remove(player.getUUID());
            return;
        }
        Map<Item, Integer> now = new HashMap<>();
        // 메뉴의 모든 칸(배낭 내용물·제작 격자 포함, 결과 미리보기 칸 제외) + 커서
        for (Slot slot : menu.slots) {
            if (slot instanceof net.minecraft.world.inventory.ResultSlot) {
                continue;
            }
            ItemStack st = slot.getItem();
            if (!st.isEmpty()) {
                now.merge(st.getItem(), st.getCount(), Integer::sum);
            }
        }
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            now.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }
        Map<Item, Integer> prev = INV.put(player.getUUID(), now);
        if (prev == null) {
            return;
        }
        Set<Item> lost = new LinkedHashSet<>();
        boolean gained = false;
        for (Map.Entry<Item, Integer> e : prev.entrySet()) {
            if (now.getOrDefault(e.getKey(), 0) < e.getValue()) {
                lost.add(e.getKey());
            }
        }
        for (Map.Entry<Item, Integer> e : now.entrySet()) {
            if (e.getValue() > prev.getOrDefault(e.getKey(), 0)) {
                gained = true;
                lost.remove(e.getKey());
            }
        }
        if (gained && !lost.isEmpty()) {
            broadcast(player, lost);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SNAPS.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
        INV.remove(event.getEntity().getUUID());
        ServerScan.forget(event.getEntity().getUUID());
    }

    private static boolean supported(AbstractContainerMenu menu) {
        return menu instanceof ItemCombinerMenu || menu instanceof StonecutterMenu || menu instanceof LoomMenu
                || menu instanceof CartographyTableMenu || menu instanceof GrindstoneMenu;
    }

    /** 커서에 든 것 + 인벤토리에 있는 해당 아이템 개수 */
    private static int held(ServerPlayer player, AbstractContainerMenu menu, Item item) {
        int total = 0;
        ItemStack carried = menu.getCarried();
        if (carried.is(item)) {
            total += carried.getCount();
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
    }

    private static void broadcast(ServerPlayer player, Set<Item> items) {
        if (items.isEmpty()) {
            return;
        }
        // 한 번에 여러 개를 연속으로 만드는 경우(쉬프트 클릭)에는 연출을 너무 자주 보내지 않는다
        Integer last = LAST.get(player.getUUID());
        if (last != null && player.tickCount - last < COOLDOWN_TICKS) {
            return;
        }
        LAST.put(player.getUUID(), player.tickCount);

        ServerLevel level = player.serverLevel();
        BlockPos station;
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            station = block.getBlockPos();
        } else {
            station = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(1.2)));
        }

        Vec3 hand = player.position().add(0, player.getBbHeight() * 0.55, 0);
        // 플레이어에게 없는 재료만 근처 보관함에서 찾는다
        Set<Item> needSearch = new LinkedHashSet<>();
        Inventory inv = player.getInventory();
        for (Item item : items) {
            boolean has = false;
            for (int i = 0; i < inv.getContainerSize() && !has; i++) {
                has = inv.getItem(i).is(item);
            }
            if (!has) {
                needSearch.add(item);
            }
        }
        // AE2 등 네트워크 단말: 재료가 네트워크에서 오므로, 단말 블록을 바라보고 있으면 그 단말에서 날아오게 한다
        boolean network = player.containerMenu.getClass().getName().startsWith("appeng.");
        boolean looking = hit instanceof BlockHitResult b2 && b2.getType() == HitResult.Type.BLOCK;
        Map<Item, BlockPos> boxes = needSearch.isEmpty() || network ? Map.of() : findContainers(level, station, needSearch);
        if (network && looking) {
            Map<Item, BlockPos> m = new HashMap<>();
            for (Item item : items) {
                m.put(item, station);
            }
            boxes = m;
        }

        List<CraftFxPacket.Fly> flies = new ArrayList<>();
        for (Item item : items) {
            BlockPos box = boxes.get(item);
            if (box != null) {
                Vec3 c = Vec3.atCenterOf(box);
                flies.add(new CraftFxPacket.Fly(item, c.x, c.y, c.z, true));
            } else {
                flies.add(new CraftFxPacket.Fly(item, hand.x, hand.y, hand.z, false));
            }
        }

        CraftFxPacket packet = new CraftFxPacket(station.immutable(), flies);
        double r2 = BROADCAST_RANGE * BROADCAST_RANGE;
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(player) <= r2 && other.connection.hasChannel(CraftFxPacket.TYPE)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(other, packet);
            }
        }
    }

    /** 작업대 근처 보관함 중 각 재료를 가진 가장 가까운 보관함 */
    private static Map<Item, BlockPos> findContainers(ServerLevel level, BlockPos center, Set<Item> wanted) {
        Map<Item, BlockPos> best = new HashMap<>();
        Map<Item, Double> bestDist = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS / 2, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, SEARCH_RADIUS / 2, SEARCH_RADIUS))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            IItemHandler handler = StorageAccess.find(level, pos);
            if (handler == null) {
                continue;
            }
            double d = pos.distSqr(center);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack st = handler.getStackInSlot(slot);
                if (!st.isEmpty() && wanted.contains(st.getItem())) {
                    Double prev = bestDist.get(st.getItem());
                    if (prev == null || d < prev) {
                        bestDist.put(st.getItem(), d);
                        best.put(st.getItem(), pos.immutable());
                    }
                }
            }
        }
        return best;
    }
}
