package dev.insightaddons.core;

import dev.insightaddons.core.client.InsightConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/** InSight 전용 모드: InSight Chests의 상자 연출 확장(내부 모양, 개수 비율, 빛, 유리 스킨 등). 클라이언트 전용입니다. */
@Mod(InsightAddons.MOD_ID)
public class InsightAddons {
    public static final String MOD_ID = "insight_addons";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public InsightAddons(ModContainer container) {
        // 클라이언트 전용 설정. 전용 서버에서는 로드되지 않습니다.
        container.registerConfig(ModConfig.Type.CLIENT, InsightConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.insightaddons.core.client.ClientInit.register(container);
        }
    }
}
