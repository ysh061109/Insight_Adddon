package dev.insightaddons.client;

import dev.insightaddons.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.insightaddons.net.TakeRequest;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 화면 관련 이벤트: 표시 비율 슬라이더, 조합 연출 시작, 재료 표시 중 마우스 휠. */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ScreenEvents {
    private static final GlobList INSIGHT_BLOCKS = new GlobList();
    private static Boolean insightPresent;

    private ScreenEvents() {}

    private static boolean insightPresent() {
        if (insightPresent == null) {
            insightPresent = ModList.get().isLoaded("insightchests");
        }
        return insightPresent;
    }

    /** InSight Chests 3D 연출이 붙는 보관함(블록)을 바라보고 있는지 */
    private static boolean lookingAtInsightBlock(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        var id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock());
        return INSIGHT_BLOCKS.matches(InsightConfig.INSIGHT_MODE_BLOCKS.get(), id)
                || id.getNamespace().equals("minecraft") && (id.getPath().contains("chest"));
    }

    /** 설치된 Sophisticated 배낭/보관함 블록을 바라보고 있는지 (B키로 연 배낭과 구분) */
    private static boolean lookingAtPlacedSophisticated(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        var id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock());
        String ns = id.getNamespace();
        return (ns.equals("sophisticatedbackpacks") || ns.equals("sophisticatedstorage"))
                && mc.level.getBlockEntity(hit.getBlockPos()) != null;
    }

    /** 화면이 열리기 직전: 어떤 방식으로 열렸는지 판단해서 유리 스킨 적용 여부와 EMI 숨김을 정한다. */
    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        Screen next = event.getNewScreen();
        AeTerminal.onOpening(next, mc);
        if (!insightPresent() || next == null || !GlassSkin.isStorageScreenClass(next)) {
            GlassSkin.setPlaced(false);
            EmiBridge.restore();
            return;
        }
        Screen current = event.getCurrentScreen();
        // 저장소 화면 -> 설정 화면처럼 이어지는 경우는 이전 판단을 그대로 쓴다
        if (current == null || !GlassSkin.isStorageScreenClass(current)) {
            GlassSkin.setPlaced(lookingAtPlacedSophisticated(mc));
        }
        if (GlassSkin.placed()) {
            EmiBridge.hide();
        } else {
            EmiBridge.restore();
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (GlassSkin.isStorageScreenClass(event.getScreen())) {
            // 다음 화면이 곧바로 이어질 수 있어서 상태는 Opening/틱에서 정리한다
            pendingReset = true;
        }
    }

    private static boolean pendingReset;

    /** 화면이 모두 닫혔는데 EMI가 꺼진 채로 남지 않도록 정리 */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !GlassSkin.isStorageScreenClass(mc.screen)) {
            if (GlassSkin.placed() || EmiBridge.hidden() || pendingReset) {
                GlassSkin.setPlaced(false);
                EmiBridge.restore();
                pendingReset = false;
            }
        }
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!insightPresent() || !(screen instanceof AbstractContainerScreen<?> acs)) {
            return;
        }
        // 표시 비율 슬라이더: InSight 유리 보관함 화면과, 설치된 Sophisticated 배낭/보관함 화면에 둔다 (B키 배낭 제외)
        boolean glass = screen.getClass().getName().equals("com.mrsun.insightchests.client.gui.GlassChestScreen");
        boolean sophisticated = GlassSkin.isStorageScreenClass(screen) && GlassSkin.placed();
        if (!glass && !sophisticated) {
            return;
        }
        int w = 126;
        int h = 12;
        int x = acs.getGuiLeft();
        int below = acs.getGuiTop() + acs.getYSize() + 3;
        int y;
        if (below + h <= screen.height - 2) {
            y = below;
        } else if (acs.getGuiTop() - h - 3 >= 2) {
            y = acs.getGuiTop() - h - 3;
        } else {
            y = 2;
        }
        event.addListener(new DropRatioSlider(x, y, w, h));
    }

    /** 결과 칸을 누르는 순간의 조합 칸 재료를 읽어 연출을 시작한다. */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!insightPresent() || event.getButton() != 0) {
            return;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> acs) {
            Slot slot = acs.getSlotUnderMouse();
            if (slot != null) {
                if (slot.hasItem() && CraftTable.isResultSlot(acs.getMenu(), slot)) {
                    CraftTable.onResult(slot.getItem().copy());
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
        return insightPresent() && InsightConfig.CLICK_TAKE.get() && ClientEvents.serverHasMod()
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
