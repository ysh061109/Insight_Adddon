package dev.insightaddons.core.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** InSight 전용 모드의 클라이언트 설정 (config/insight_addons-client.toml). */
public final class InsightConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue INSIGHT_MODE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INSIGHT_MODE_BLOCKS;
    public static final ModConfigSpec.BooleanValue GLASS_SKIN;
    public static final ModConfigSpec.BooleanValue WINDOW_DRAG;
    public static final ModConfigSpec.IntValue GLASS_ALPHA;
    public static final ModConfigSpec.IntValue CHAOS_SPEED;
    public static final ModConfigSpec.IntValue CHAOS_RANDOM;
    public static final ModConfigSpec.BooleanValue HUD_HIDDEN;
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

    /** 보관함을 열 때 나오는 조작 버튼들의 배치(기본 배치 기준 이동량, 표시 여부) */
    public static final String[] LAYOUT_IDS = {"ratio", "alpha", "chaos", "speed", "random", "hud"};
    public static final ModConfigSpec.IntValue[] LAYOUT_DX = new ModConfigSpec.IntValue[LAYOUT_IDS.length];
    public static final ModConfigSpec.IntValue[] LAYOUT_DY = new ModConfigSpec.IntValue[LAYOUT_IDS.length];
    public static final ModConfigSpec.BooleanValue[] LAYOUT_SHOW = new ModConfigSpec.BooleanValue[LAYOUT_IDS.length];
    public static final ModConfigSpec.IntValue CHAOS_ICON_MAX;

    private static int layoutIndex(String id) {
        for (int i = 0; i < LAYOUT_IDS.length; i++) {
            if (LAYOUT_IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean layoutShow(String id) {
        int i = layoutIndex(id);
        return i < 0 || LAYOUT_SHOW[i].get();
    }

    public static int layoutDx(String id) {
        int i = layoutIndex(id);
        return i < 0 ? 0 : LAYOUT_DX[i].get();
    }

    public static int layoutDy(String id) {
        int i = layoutIndex(id);
        return i < 0 ? 0 : LAYOUT_DY[i].get();
    }
    public static final ModConfigSpec.BooleanValue AE2_FRONT_VIEW;
    public static final ModConfigSpec.BooleanValue AE2_PHYSICS;
    public static final ModConfigSpec.IntValue AE2_PHYSICS_MAX;
    public static final ModConfigSpec.IntValue AE2_PHYSICS_RATIO;
    public static final ModConfigSpec.IntValue AE2_GUI_ALPHA;
    public static final ModConfigSpec.BooleanValue AE2_GLASS;
    public static final ModConfigSpec.IntValue AE2_GLASS_ALPHA;
    public static final ModConfigSpec.IntValue AE2_GLASS_SLOT_ALPHA;
    public static final ModConfigSpec.IntValue AE2_OVERLAY_ALPHA;
    public static final ModConfigSpec.BooleanValue AE2_HIDE_EMI;
    public static final ModConfigSpec.IntValue AE2_MAX_SHOWN;

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
        CHAOS_SPEED = b.comment("난장판 속도(%). 화면의 속도 슬라이더로도 바꿀 수 있고, 아이템마다 이 값 근처에서 제각각 랜덤 속도가 됩니다.")
                .translation("insight_addons.configuration.chaosSpeed").defineInRange("chaosSpeed", 100, 20, 300);
        CHAOS_RANDOM = b.comment("난장판에서 아이템마다 속도를 다르게 하는 정도(%). 0이면 모두 설정한 속도로 같이 움직이고, 높을수록 제각각 빠르고 느려집니다.")
                .translation("insight_addons.configuration.chaosRandom").defineInRange("chaosRandom", 0, 0, 100);
        CHAOS_ICON_MAX = b.comment("인벤토리/배낭 난장판에서 동시에 날아다니는 아이콘 조각의 최대 개수. 올리면 더 많이 날고 느려질 수 있습니다.")
                .translation("insight_addons.configuration.chaosIconMax").defineInRange("chaosIconMax", 240, 20, 3000);
        HUD_HIDDEN = b.comment("보관함을 열었을 때 체력/갑옷/배고픔/경험치/핫바를 숨깁니다. 보관함 화면의 HUD 버튼으로 바꾸면 저장됩니다.")
                .translation("insight_addons.configuration.hudHidden").define("hudHidden", false);
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
        WINDOW_DRAG = b.comment("true면 보관함/상자 창의 빈 곳(슬롯과 버튼이 없는 곳, 제목 줄 포함)을 끌어서 창을 옮길 수 있습니다. 위치는 게임을 켜 둔 동안 기억하고, 빈 곳을 더블클릭하면 원래 자리로 돌아옵니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.windowDrag").define("windowDrag", true);
        b.pop();

        b.translation("insight_addons.configuration.ae2").push("ae2");
        AE2_PHYSICS = b.comment("AE2 단말기를 열었을 때 아이템을 격자 진열대 대신, SF 서버룸(흰 패널 벽, 서버 랙, ME 제어기와 드라이브) 안에 물리로 쌓아 보여주고, 카메라가 그 방 한가운데에 선 것처럼 보입니다.",
                        "꺼내면 해당 아이템이 플레이어에게 날아가고, 넣으면 위에서 떨어집니다. 끄면 예전 격자 진열대로 돌아갑니다.")
                .translation("insight_addons.configuration.ae2.pileBox").define("pileBox", true);
        AE2_MAX_SHOWN = b.comment("AE2 단말기를 열었을 때 3D로 보여줄 아이템 종류의 최대 개수 (많이 든 순)")
                .translation("insight_addons.configuration.ae2.maxShown").defineInRange("maxShown", 40, 8, 80);
        AE2_PHYSICS_RATIO = b.comment("서버룸에 쌓이는 아이템 조각 수의 비율(%). 단말기 창 아래 슬라이더로도 바꿀 수 있습니다. 100이 기본입니다.")
                .translation("insight_addons.configuration.ae2.pileRatio").defineInRange("pileRatio", 100, 10, 300);
        AE2_GUI_ALPHA = b.comment("서버룸 연출이 켜져 있을 때 AE2 단말기 창 배경의 불투명도(%). 낮출수록 뒤의 서버룸이 비쳐 보입니다. 100이면 원래대로입니다.")
                .translation("insight_addons.configuration.ae2.guiAlpha").defineInRange("guiAlpha", 60, 20, 100);
        AE2_GLASS = b.comment("AE2 단말기 창을 Insight Chests Plus의 배낭/통처럼 투명한 유리 창으로 그립니다(서버룸 연출이 켜져 있을 때).")
                .translation("insight_addons.configuration.ae2.glass").define("glass", true);
        AE2_GLASS_ALPHA = b.comment("유리 창 패널의 진하기(0~255). Plus의 기본값과 같은 35입니다.")
                .translation("insight_addons.configuration.ae2.glassAlpha").defineInRange("glassAlpha", 35, 0, 255);
        AE2_GLASS_SLOT_ALPHA = b.comment("유리 창 슬롯 칸 속 흰색 농도(0~255). Plus의 기본값과 같은 16입니다.")
                .translation("insight_addons.configuration.ae2.glassSlotAlpha").defineInRange("glassSlotAlpha", 16, 0, 255);
        AE2_OVERLAY_ALPHA = b.comment("서버룸 연출 중 JEI/EMI 화면 요소의 불투명도(%). 100이면 원래대로입니다.")
                .translation("insight_addons.configuration.ae2.overlayAlpha").defineInRange("overlayAlpha", 70, 20, 100);
        AE2_HIDE_EMI = b.comment("AE2 단말기를 여는 동안 EMI 화면 요소(검색창, 아이템 목록 등)를 끕니다. 기본은 꺼짐(반투명으로 표시되어 레시피 옮기기(+) 버튼도 그대로 씁니다). 반투명이 안 먹는 환경에서만 켜세요.")
                .translation("insight_addons.configuration.ae2.hideEmi").define("hideEmiOverlay", false);
        AE2_FRONT_VIEW = b.comment("AE2 단말기를 열면 시선을 서버룸 정면으로 맞추고, 닫으면 원래 각도로 되돌립니다.")
                .translation("insight_addons.configuration.ae2.frontView").define("frontView", true);
        AE2_PHYSICS_MAX = b.comment("물리 연출에서 동시에 그리는 아이템 조각의 최대 개수. 낮추면 가벼워집니다.")
                .translation("insight_addons.configuration.ae2.physicsMax").defineInRange("pieceBudget", 600, 50, 3000);
        b.pop();

        b.translation("insight_addons.configuration.buttonLayout").push("buttonLayout");
        for (int i = 0; i < LAYOUT_IDS.length; i++) {
            String id = LAYOUT_IDS[i];
            LAYOUT_SHOW[i] = b.comment("보관함을 열 때 이 버튼을 표시합니다.")
                    .translation("insight_addons.configuration.layout." + id + ".show").define(id + "Show", true);
            LAYOUT_DX[i] = b.comment("기본 위치에서 가로로 옮길 양(GUI 픽셀, 오른쪽이 +).")
                    .translation("insight_addons.configuration.layout." + id + ".x").defineInRange(id + "X", 0, -1500, 1500);
            LAYOUT_DY[i] = b.comment("기본 위치에서 세로로 옮길 양(GUI 픽셀, 아래쪽이 +).")
                    .translation("insight_addons.configuration.layout." + id + ".y").defineInRange(id + "Y", 0, -1500, 1500);
        }
        b.pop();

        SPEC = b.build();
    }

    private InsightConfig() {}
}
