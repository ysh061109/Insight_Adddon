package dev.insightaddons.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.client.GlassSkin;
import dev.insightaddons.client.InsightBridge;
import dev.insightaddons.client.InsightConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sophisticated Core의 StorageScreenBase(배낭, 통, 상자 화면)의 "배경"만 InSight Chests 유리로 바꾸고,
 * 화면을 InSight Chests의 guiPosition 설정 위치(기본 오른쪽)로 옮깁니다.
 * 슬롯, 버튼, 업그레이드 탭 같은 기능 요소는 건드리지 않습니다.
 * 주입 지점을 못 찾으면 require=0 때문에 조용히 건너뛰고 원래 화면이 그대로 나옵니다.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class StorageScreenSkinMixin {

    /** super.init()이 leftPos를 정한 직후, Sophisticated가 위젯 위치를 계산하기 전에 화면을 옮긴다. */
    @Inject(method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;init()V",
                    shift = At.Shift.AFTER),
            remap = false, require = 0)
    private void insightaddons$reposition(CallbackInfo ci) {
        if (!GlassSkin.enabled() || !InsightConfig.FOLLOW_INSIGHT_POSITION.get()) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) (Object) this;
        int w = container.getXSize();
        int h = container.getYSize();
        // 왼쪽에는 업그레이드 띠(약 21px)가 붙는다
        switch (InsightBridge.guiPosition()) {
            case "LEFT" -> access.insightaddons$setLeftPos(6 + 25);
            case "RIGHT" -> access.insightaddons$setLeftPos(Math.max(25, screen.width - w - 6));
            default -> {
                return;
            }
        }
        access.insightaddons$setTopPos(Math.max(4, (screen.height - h) / 2));
    }

    /** 메인 패널 + 플레이어 인벤토리 칸 */
    @Inject(method = "drawInventoryBg(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void insightaddons$glassPanel(GuiGraphics g, int x, int y, ResourceLocation texture, CallbackInfo ci) {
        if (!GlassSkin.enabled()) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        RenderSystem.enableBlend();
        // Sophisticated는 배경 위치를 "화면 가운데"로 따로 계산하므로, 화면을 옮겼을 때 실제 위치(guiLeft/guiTop)를 쓴다
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        GlassSkin.panel(g, left, top, screen.getXSize(), screen.getYSize());
        GlassSkin.extraCells(g, left, top, screen.getMenu(), false);
        ci.cancel();
    }

    // 원래 배경/슬롯 칸 그리기도 가운데 기준 좌표를 받으므로, 옮긴 실제 위치로 바꿔 준다.
    // (이게 어긋나 있으면 슬롯 칸 그림과 실제 슬롯 위치가 따로 놀게 된다)
    @ModifyVariable(method = "drawInventoryBg(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"), index = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$bgX(int x) {
        return GlassSkin.enabled() ? ((AbstractContainerScreen<?>) (Object) this).getGuiLeft() : x;
    }

    @ModifyVariable(method = "drawInventoryBg(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"), index = 3, argsOnly = true, remap = false, require = 0)
    private int insightaddons$bgY(int y) {
        return GlassSkin.enabled() ? ((AbstractContainerScreen<?>) (Object) this).getGuiTop() : y;
    }

    @ModifyVariable(method = "drawSlotBg(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At("HEAD"), index = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$slotX(int x) {
        return GlassSkin.enabled() ? ((AbstractContainerScreen<?>) (Object) this).getGuiLeft() : x;
    }

    @ModifyVariable(method = "drawSlotBg(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At("HEAD"), index = 3, argsOnly = true, remap = false, require = 0)
    private int insightaddons$slotY(int y) {
        return GlassSkin.enabled() ? ((AbstractContainerScreen<?>) (Object) this).getGuiTop() : y;
    }

    // 왼쪽 업그레이드 띠: 원래는 위 모서리, 가운데, 아래 모서리 3조각을 이미지로 그린다.
    // 가운데(ordinal 1) 호출에서 한 번에 유리 띠와 업그레이드 칸을 그리고, 나머지 두 조각은 건너뛴다.
    // 업그레이드 켜기/끄기 표시(그 뒤의 blit)는 그대로 둔다.
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V";

    @Redirect(method = "drawUpgradeBackground", at = @At(value = "INVOKE", target = BLIT, ordinal = 0),
            remap = false, require = 0)
    private void insightaddons$stripTop(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                                        int w, int h, int tw, int th) {
        if (!GlassSkin.enabled()) {
            g.blit(tex, x, y, u, v, w, h, tw, th);
        }
    }

    @Redirect(method = "drawUpgradeBackground", at = @At(value = "INVOKE", target = BLIT, ordinal = 1),
            remap = false, require = 0)
    private void insightaddons$stripMiddle(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                                           int w, int h, int tw, int th) {
        if (!GlassSkin.enabled()) {
            g.blit(tex, x, y, u, v, w, h, tw, th);
            return;
        }
        // 위쪽 4칸 + 가운데 h + 아래쪽 6칸 = 전체 높이
        GlassSkin.control(g, x, y - 4, 22, h + 4 + 6);
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GlassSkin.extraCells(g, screen.getGuiLeft(), screen.getGuiTop(), screen.getMenu(), true);
    }

    @Redirect(method = "drawUpgradeBackground", at = @At(value = "INVOKE", target = BLIT, ordinal = 2),
            remap = false, require = 0)
    private void insightaddons$stripBottom(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                                           int w, int h, int tw, int th) {
        if (!GlassSkin.enabled()) {
            g.blit(tex, x, y, u, v, w, h, tw, th);
        }
    }
}
