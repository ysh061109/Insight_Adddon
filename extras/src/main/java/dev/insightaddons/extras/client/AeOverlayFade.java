package dev.insightaddons.extras.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.insightaddons.extras.InsightAddons;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

/**
 * AE2 단말기의 서버룸 연출 중 JEI/EMI 화면 요소(검색창, 아이템 목록 등)를 반투명하게 그려서
 * 뒤의 방이 비쳐 보이게 합니다. 화면 렌더링이 끝난 뒤(Post)에 JEI/EMI가 그리는 부분만 감쌉니다.
 * 설정으로 EMI 화면 요소를 아예 끌 수도 있습니다(단말기를 여는 동안만).
 */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class AeOverlayFade {
    private static boolean faded;
    private static Field emiEnabled;
    private static Boolean emiSaved;

    private AeOverlayFade() {}

    private static boolean active(Screen s) {
        return InsightConfig.AE2_PHYSICS.get() && AeTerminal.isTerminalScreen(s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(ScreenEvent.Render.Post e) {
        if (!active(e.getScreen())) {
            return;
        }
        int pct = InsightConfig.AE2_OVERLAY_ALPHA.get();
        if (pct < 100) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, pct / 100f);
            faded = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(ScreenEvent.Render.Post e) {
        if (faded) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            faded = false;
        }
    }

    @SubscribeEvent
    public static void onOpen(ScreenEvent.Init.Pre e) {
        if (InsightConfig.AE2_HIDE_EMI.get() && active(e.getScreen())) {
            try {
                if (emiEnabled == null) {
                    emiEnabled = Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled");
                }
                if (emiSaved == null) {
                    emiSaved = emiEnabled.getBoolean(null);
                    emiEnabled.setBoolean(null, false);
                }
            } catch (Throwable ignored) {
                emiSaved = null;
            }
        }
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing e) {
        restore();
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening e) {
        if (emiSaved != null && !active(e.getNewScreen())) {
            restore();
        }
    }

    private static void restore() {
        if (emiSaved != null && emiEnabled != null) {
            try {
                emiEnabled.setBoolean(null, emiSaved);
            } catch (Throwable ignored) {
                // 무시
            }
            emiSaved = null;
        }
    }
}
