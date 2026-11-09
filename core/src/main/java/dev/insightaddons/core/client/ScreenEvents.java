package dev.insightaddons.core.client;

import dev.insightaddons.core.InsightAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 화면 관련 이벤트: 유리 스킨 적용 여부 판단, EMI 숨김, 표시 비율 슬라이더. */
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
        return (ns.equals("sophisticatedbackpacks") || ns.equals("sophisticatedstorage")
                || (ns.equals("mekanism") && id.getPath().startsWith("personal_")))
                && mc.level.getBlockEntity(hit.getBlockPos()) != null;
    }

    /** 화면이 열리기 직전: 어떤 방식으로 열렸는지 판단해서 유리 스킨 적용 여부와 EMI 숨김을 정한다. */
    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        Screen next = event.getNewScreen();
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
        } else if (acs.getGuiLeft() - w - 4 >= 2) {
            // 아래 공간이 없으면 창 왼쪽 옆 아래쪽에 둔다 (제목/위쪽 탭과 겹치지 않게)
            x = acs.getGuiLeft() - w - 4;
            y = Math.min(screen.height - h - 2, acs.getGuiTop() + acs.getYSize() - h - 2);
        } else if (acs.getGuiTop() - h - 3 >= 2) {
            y = acs.getGuiTop() - h - 3;
        } else {
            x = 2;
            y = screen.height - h - 2;
        }
        event.addListener(new DropRatioSlider(x, y, w, h));
    }
}
