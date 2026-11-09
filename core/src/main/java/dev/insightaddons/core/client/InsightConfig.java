package dev.insightaddons.core.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** InSight 전용 모드의 클라이언트 설정 (config/insight_addons-client.toml). */
public final class InsightConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue INSIGHT_MODE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INSIGHT_MODE_BLOCKS;
    public static final ModConfigSpec.BooleanValue GLASS_SKIN;
    public static final ModConfigSpec.IntValue GLASS_ALPHA;
    public static final ModConfigSpec.BooleanValue FOLLOW_INSIGHT_POSITION;
    public static final ModConfigSpec.IntValue DROP_RATIO;
    public static final ModConfigSpec.IntValue DROP_MAX_TOTAL;
    public static final ModConfigSpec.ConfigValue<String> ITEM_SIZE_SCALING;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE_BACKPACK;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE_SHULKER;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE_BARREL;
    public static final ModConfigSpec.IntValue GLASS_SLOT_ALPHA;
    public static final ModConfigSpec.ConfigValue<String> ITEM_GLOW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FANCY_MOD_IDS;
    public static final ModConfigSpec.BooleanValue KEEP_ORIGINAL_SCREEN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.translation("insight_addons.configuration.insightChestsIntegration").push("insightChestsIntegration");
        INSIGHT_MODE_ENABLED = b.comment("InSight Chests가 설치되어 있으면, 아래 모드의 블록을 열 때도 InSight Chests의 상자 연출을 적용합니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.enabled").define("enabled", true);
        INSIGHT_MODE_BLOCKS = b.comment("상자 연출(카메라 전환, 유리 화면, 3D 아이템)을 적용할 블록 ID 패턴 (*는 와일드카드)")
                .translation("insight_addons.configuration.insightChestsIntegration.blocks").defineListAllowEmpty("blocks", List.of("sophisticatedbackpacks:*", "sophisticatedstorage:*",
                                "minecraft:barrel", "minecraft:*shulker_box", "mekanism:personal_*"),
                        () -> "", o -> o instanceof String);
        GLASS_SKIN = b.comment("true면 Sophisticated 화면의 배경과 패널(슬롯, 업그레이드 탭, 설정 패널)을 InSight Chests 스타일의 유리로 바꿉니다.",
                        "기능(업그레이드 탭, 정렬, 설정 등)은 원래 그대로입니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.glassSkin").define("glassSkin", true);
        GLASS_ALPHA = b.comment("유리 패널의 투명도 값(0~255). -1이면 InSight Chests의 uiBackgroundOpacity 설정을 그대로 따릅니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.glassAlpha").defineInRange("glassAlpha", -1, -1, 255);
        GLASS_SLOT_ALPHA = b.comment("유리 화면의 슬롯 칸 속 흰색 농도(0~255). 올리면 칸이 밝아지고, 0이면 칸 속이 완전히 투명해집니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.glassSlotAlpha").defineInRange("glassSlotAlpha", 16, 0, 255);
        FOLLOW_INSIGHT_POSITION = b.comment("true면 Sophisticated 화면도 InSight Chests의 guiPosition 설정(왼쪽/가운데/오른쪽)대로 옮깁니다.",
                        "오른쪽이 기본이라 가운데에 3D 아이템이 보입니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.followInsightPosition").define("followInsightPosition", true);
        DROP_RATIO = b.comment("보관함 안에 떨어지는 3D 아이템 개수 비율. 0 = 실제 개수 그대로(1:1), 1 = 절반, 2 = InSight Chests 원래 방식(스택당 최대 5개)",
                        "보관함 화면 아래의 슬라이더로도 바꿀 수 있습니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.dropRatio").defineInRange("dropRatio", 0, 0, 2);
        DROP_MAX_TOTAL = b.comment("보관함 하나에 한꺼번에 떨어뜨릴 3D 아이템 최대 개수. 넘으면 비율을 유지한 채 줄여서 표시합니다. (FPS 보호)",
                        "dropRatio가 2(원래 방식)이면 InSight Chests의 자체 제한을 따릅니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.dropMaxTotal").defineInRange("dropMaxTotal", 640, 54, 4000);
        ITEM_SIZE_SCALING = b.comment("아이템 실제 크기에 맞춰 떨어지는 아이템 크기를 조절합니다. auto = Fancy Items 모드가 있을 때만, on = 항상, off = 끔")
                .translation("insight_addons.configuration.insightChestsIntegration.itemSizeScaling").define("itemSizeScaling", "auto");
        ITEM_SCALE = b.comment("보관함 안에 쌓이는 3D 아이템의 전체 크기 배율 (1.0 = 기본). 줄이면 더 많이 들어가 보입니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.itemScale").defineInRange("itemScale", 1.0, 0.3, 2.0);
        ITEM_SCALE_BACKPACK = b.comment("배낭에 쌓이는 3D 아이템의 추가 크기 배율 (전체 배율과 곱해집니다)")
                .translation("insight_addons.configuration.insightChestsIntegration.itemScaleBackpack").defineInRange("itemScaleBackpack", 1.0, 0.3, 2.0);
        ITEM_SCALE_SHULKER = b.comment("셜커 상자에 쌓이는 3D 아이템의 추가 크기 배율")
                .translation("insight_addons.configuration.insightChestsIntegration.itemScaleShulker").defineInRange("itemScaleShulker", 1.0, 0.3, 2.0);
        ITEM_SCALE_BARREL = b.comment("통에 쌓이는 3D 아이템의 추가 크기 배율")
                .translation("insight_addons.configuration.insightChestsIntegration.itemScaleBarrel").defineInRange("itemScaleBarrel", 1.0, 0.3, 2.0);
        ITEM_GLOW = b.comment("보관함 안에서 떨어지는 3D 아이템에 등급 색 빛(후광) 효과를 줍니다.",
                        "auto = 아래 fancyModIds 중 하나가 설치되어 있을 때만, on = 항상, off = 끔")
                .translation("insight_addons.configuration.insightChestsIntegration.itemGlow").define("itemGlow", "auto");
        FANCY_MOD_IDS = b.comment("itemGlow가 auto일 때 빛 효과를 켜는 기준이 되는 모드 ID 목록 (Fancy Items 모드의 ID를 모르면 여기에 추가하세요)")
                .translation("insight_addons.configuration.insightChestsIntegration.fancyModIds").defineListAllowEmpty("fancyModIds", List.of("fancyitems", "fancy_items", "fancyitem", "fancy_item", "fancyitemglow"),
                        () -> "", o -> o instanceof String);
        KEEP_ORIGINAL_SCREEN = b.comment("true면 Sophisticated 계열은 원래 화면(업그레이드 탭 등)을 그대로 두고 3D 연출만 적용합니다.",
                        "false면 InSight Chests의 유리 화면으로 바꿉니다. 이 경우 업그레이드 탭이 보이지 않을 수 있습니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.keepOriginalScreen").define("keepOriginalScreen", true);
        b.pop();

        SPEC = b.build();
    }

    private InsightConfig() {}
}
