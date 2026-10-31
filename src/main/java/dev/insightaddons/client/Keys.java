package dev.insightaddons.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.insightaddons.InsightAddons;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 이 모드의 단축키. 조작 설정의 "Insight Addons" 항목에서 바꿀 수 있습니다. */
@EventBusSubscriber(modid = InsightAddons.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Keys {
    private static final String CATEGORY = "key.categories.insight_addons";

    public static final KeyMapping SEARCH = new KeyMapping("key.insight_addons.search",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping NAME = new KeyMapping("key.insight_addons.name",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping LIST_ADD = new KeyMapping("key.insight_addons.list_add",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
    public static final KeyMapping LIST_TOGGLE = new KeyMapping("key.insight_addons.list_toggle",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, CATEGORY);

    private Keys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SEARCH);
        event.register(NAME);
        event.register(LIST_ADD);
        event.register(LIST_TOGGLE);
    }
}
