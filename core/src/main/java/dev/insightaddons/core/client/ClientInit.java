package dev.insightaddons.core.client;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 클라이언트 전용 초기화: 모드 목록의 "설정" 버튼에서 열리는 설정 화면을 등록합니다.
 * YetAnotherConfigLib가 설치되어 있으면 YACL 화면을, 없거나 실패하면 NeoForge 기본 설정 화면을 씁니다.
 */
public final class ClientInit {
    private ClientInit() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> {
            if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
                Screen yacl = YaclScreen.create(parent);
                if (yacl != null) {
                    return yacl;
                }
            }
            return new ConfigurationScreen(modContainer, parent);
        });
    }
}
