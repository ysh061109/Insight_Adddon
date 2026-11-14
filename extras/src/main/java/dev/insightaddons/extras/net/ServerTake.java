package dev.insightaddons.extras.net;

import dev.insightaddons.extras.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 서버 측: 플레이어가 3D 표시를 클릭하면 그 보관함에서 아이템을 꺼내 인벤토리에 넣습니다.
 *  - 팔이 닿는 거리(상호작용 거리 + 2블록) 안의 보관함만 허용합니다.
 *  - 보호 모드(청크 보호 등)가 막는 우클릭이면 거절합니다. (RightClickBlock 이벤트를 발생시켜 취소 여부를 확인)
 *  - 인벤토리에 들어갈 만큼만 꺼냅니다. 바닥에 떨어뜨리지 않습니다.
 */
public final class ServerTake {
    private ServerTake() {}

    public static void handle(TakeRequest req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> take(player, req));
    }

    private static void take(ServerPlayer player, TakeRequest req) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = req.pos();
        if (!level.isLoaded(pos)) {
            return;
        }
        double reach = player.blockInteractionRange() + 2.0;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return;
        }
        // 청크 보호 같은 모드가 이 보관함 사용을 막고 있으면 거절한다
        PlayerInteractEvent.RightClickBlock probe = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        NeoForge.EVENT_BUS.post(probe);
        if (probe.isCanceled()) {
            return;
        }

        // 플레이어가 열 수 없는 금고는 서버에서도 거절한다
        if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals("create:item_vault")) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(req.item()).orElse(null);
        IItemHandler handler = StorageAccess.find(level, pos);
        if (item == null || handler == null) {
            return;
        }

        IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
        int remaining = req.mode() >= 2 ? Integer.MAX_VALUE : req.mode() == 1 ? item.getDefaultMaxStackSize() : 1;
        int moved = 0;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            // 이 칸에서 꺼낼 수 있는 양을 먼저 확인하고, 인벤토리에 들어갈 만큼만 실제로 꺼낸다
            ItemStack simulated = handler.extractItem(slot, Math.min(remaining, stack.getMaxStackSize()), true);
            if (simulated.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(inventory, simulated, true);
            int fits = simulated.getCount() - leftover.getCount();
            if (fits <= 0) {
                break; // 인벤토리가 가득 참
            }
            ItemStack taken = handler.extractItem(slot, fits, false);
            if (taken.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, taken, false);
            int placed = taken.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                // 혹시 모를 불일치: 꺼낸 것을 되돌려 넣는다
                ItemHandlerHelper.insertItemStacked(handler, rest, false);
            }
            moved += placed;
            remaining -= placed;
            slot--; // 같은 칸에 더 남았을 수 있으니 다시 확인
            if (placed <= 0) {
                break;
            }
        }

        if (moved > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.25f, 1.3f);
            // 바뀐 내용을 바로 다시 보내서 화면의 개수가 곧바로 갱신되게 한다
            ServerScan.scan(player, new ScanRequest(0, "", List.of(pos), List.of()));
        }
    }
}
