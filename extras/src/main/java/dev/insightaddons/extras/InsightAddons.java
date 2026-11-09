package dev.insightaddons.extras;

import dev.insightaddons.extras.client.InsightConfig;
import dev.insightaddons.extras.net.Net;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(InsightAddons.MOD_ID)
public class InsightAddons {
    public static final String MOD_ID = "insight_addons_extras";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public InsightAddons(IEventBus modBus, ModContainer container) {
        modBus.addListener(Net::register);
        // 클라이언트 전용 설정. 전용 서버에서는 로드되지 않으며 서버 코드는 이 값을 읽지 않습니다.
        container.registerConfig(ModConfig.Type.CLIENT, InsightConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.insightaddons.extras.client.ClientInit.register(container);
        }
    }
}
