package dev.insightaddons.extras.client;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.insightaddons.extras.net.TakeRequest;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 화면 관련 이벤트: AE2 단말기 기억, 조합 연출 시작, 재료 표시 중 마우스 휠, 3D 아이템 클릭. */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ScreenEvents {
    private ScreenEvents() {}

    /** 화면이 열리기 직전: AE2 단말기 화면이면 내용물을 기억할 준비를 한다. */
    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        AeTerminal.onOpening(event.getNewScreen(), Minecraft.getInstance());
    }

    /** 결과 칸을 누르는 순간의 조합 칸 재료를 읽어 연출을 시작한다. */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> acs) {
            Slot slot = acs.getSlotUnderMouse();
            if (slot != null) {
                if (slot.hasItem() && CraftTable.isResultSlot(acs.getMenu(), slot)) {
                    CraftTable.onResult(slot.getItem().copy(), acs.getMenu());
                }
                CraftFx.onResultClicked(Minecraft.getInstance(), acs.getMenu(), slot);
            }
        }
    }

    /** 재료 표시 중에는 마우스 휠로 재료를 넘긴다. (핫바 스크롤 대신) */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!RecipeFocus.isActive() || !InsightConfig.RECIPE_SCROLL.get() || Minecraft.getInstance().screen != null
                || RecipeFocus.shownRows().isEmpty()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta != 0) {
            RecipeFocus.scroll(delta > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    private static boolean canTake() {
        return InsightConfig.CLICK_TAKE.get() && ClientEvents.serverHasMod()
                && Minecraft.getInstance().screen == null
                && StorageRenderer.hoveredItem() != null && StorageRenderer.hoveredPos() != null;
    }

    /** 3D 아이템을 바라본 채 왼쪽 클릭하면 그 보관함에서 해당 아이템을 인벤토리로 가져온다. (Shift: 전부) */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !canTake()) {
            return;
        }
        Item item = StorageRenderer.hoveredItem();
        BlockPos pos = StorageRenderer.hoveredPos();
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new TakeRequest(pos, BuiltInRegistries.ITEM.getKey(item), Screen.hasShiftDown()));
    }

    /** 아이템을 바라보는 동안 클릭이 보관함 부수기로 이어지지 않게 한다. (크리에이티브 포함) */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() && canTake()) {
            event.setCanceled(true);
        }
    }
}
