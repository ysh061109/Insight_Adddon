package dev.insightaddons.core.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** AE2 서버룸 연출의 틱/월드 렌더/HUD 글씨 연결. */
@EventBusSubscriber(modid = dev.insightaddons.core.InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class AeEvents {
    private AeEvents() {}

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening e) {
        AeTerminal.onOpening(e.getNewScreen(), Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            AeScene.clear();
            return;
        }
        AeScene.tick(mc);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        AeScene.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AeDraw.setProjection(e.getProjectionMatrix());
        if (mc.player == null || mc.level == null || !AeScene.visible()) {
            return;
        }
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        AeScene.render(e.getPoseStack(), e.getCamera(), e.getCamera().getPosition(), mc, partial,
                mc.renderBuffers().bufferSource());
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post e) {
        AeDraw.renderHud(e.getGuiGraphics(), Minecraft.getInstance());
    }
}
