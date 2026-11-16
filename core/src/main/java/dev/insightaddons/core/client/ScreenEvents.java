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

    private static boolean isGlassChest(Object screen) {
        return screen != null && screen.getClass().getName().equals("com.mrsun.insightchests.client.gui.GlassChestScreen");
    }

    /** 화면이 열리기 직전: 어떤 방식으로 열렸는지 판단해서 유리 스킨 적용 여부와 EMI 숨김을 정한다. */
    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        // 배낭 화면 <-> 배낭 설정 화면처럼 보관함 화면끼리 바로 넘어가는 경우, 이전 화면의 닫힘 처리(카메라 복귀)를 막는다
        GlassSkin.setSwitching(GlassSkin.isStorageScreenClass(event.getCurrentScreen())
                && GlassSkin.isStorageScreenClass(event.getNewScreen()));
        Screen next = event.getNewScreen();
        if (insightPresent() && isGlassChest(next)) {
            // InSight 유리 보관함 화면(바닐라 상자, Iron Chests 등)에서도 EMI 검색창 등을 숨긴다
            GlassSkin.setPlaced(false);
            EmiBridge.hide();
            return;
        }
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
        if (GlassSkin.isStorageScreenClass(event.getScreen()) || isGlassChest(event.getScreen())) {
            // 다음 화면이 곧바로 이어질 수 있어서 상태는 Opening/틱에서 정리한다
            pendingReset = true;
        }
    }

    private static boolean pendingReset;

    /** 화면이 모두 닫혔는데 EMI가 꺼진 채로 남지 않도록 정리 */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GlassSkin.setSwitching(false);
        if (mc.screen == null || !(GlassSkin.isStorageScreenClass(mc.screen) || isGlassChest(mc.screen))) {
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
        // 왼쪽 옆에는 배낭 업그레이드 탭이 붙으므로 절대 두지 않는다
        if (below + h <= screen.height - 2) {
            y = below;
        } else if (acs.getGuiTop() - h - 3 >= 2) {
            y = acs.getGuiTop() - h - 3;
        } else if (acs.getGuiLeft() + acs.getXSize() + 4 + w <= screen.width - 2) {
            x = acs.getGuiLeft() + acs.getXSize() + 4;
            y = Math.min(screen.height - h - 2, acs.getGuiTop() + acs.getYSize() - h - 2);
        } else {
            x = acs.getGuiLeft() + acs.getXSize() - w - 4;
            y = Math.max(2, screen.height - h - 2);
        }
        event.addListener(WidgetGuard.mine(new DropRatioSlider(x, y, w, h)));
        // 창 투명도 슬라이더: 비율 슬라이더 옆, 옆이 막혀 있으면 그 위/아래
        int ax = x + w + 4;
        int ay = y;
        if (ax + 100 > screen.width - 2) {
            ax = x;
            ay = y - h - 2 >= 2 ? y - h - 2 : y + h + 2;
        }
        event.addListener(WidgetGuard.mine(new ConfigSlider(ax, ay, 100, h, 0, 255, GlassSkin.effectiveAlpha(),
                v -> net.minecraft.network.chat.Component.translatable("insight_addons.window.alpha", Math.round(v * 100f / 255f)),
                ConfigSlider.save(InsightConfig.GLASS_ALPHA))));
    }
}
