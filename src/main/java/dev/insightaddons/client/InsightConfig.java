package dev.insightaddons.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** 클라이언트 설정 (config/insight_addons-client.toml). 서버 코드에서는 읽지 않습니다. */
public final class InsightConfig {
    public static final ModConfigSpec SPEC;

    // ── 대상 보관함 (옆 3D 표시와 렌치 스캔 공통) ──
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONTAINER_BLOCKS;

    // ── 열었던 보관함 기억 (서버에 모드가 없어도 동작) ──
    public static final ModConfigSpec.BooleanValue MEMORY_ENABLED;

    // ── 보관함 옆 3D 표시 ──
    public static final ModConfigSpec.BooleanValue NEAR_ENABLED;
    public static final ModConfigSpec.IntValue NEAR_MAX_ICONS;
    public static final ModConfigSpec.DoubleValue NEAR_ICON_SCALE;
    public static final ModConfigSpec.BooleanValue NEAR_FRAME;
    public static final ModConfigSpec.BooleanValue NEAR_UPGRADES;
    public static final ModConfigSpec.DoubleValue NEAR_MAIN_SCALE;
    public static final ModConfigSpec.BooleanValue STICKY_DISPLAY;
    public static final ModConfigSpec.IntValue STICKY_DISTANCE;
    public static final ModConfigSpec.BooleanValue CLICK_TAKE;

    // ── InSight Chests 연동 ──
    public static final ModConfigSpec.BooleanValue INSIGHT_MODE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INSIGHT_MODE_BLOCKS;
    public static final ModConfigSpec.BooleanValue GLASS_SKIN;
    public static final ModConfigSpec.IntValue GLASS_ALPHA;
    public static final ModConfigSpec.BooleanValue FOLLOW_INSIGHT_POSITION;
    public static final ModConfigSpec.IntValue DROP_RATIO;
    public static final ModConfigSpec.IntValue DROP_MAX_TOTAL;
    public static final ModConfigSpec.ConfigValue<String> ITEM_SIZE_SCALING;
    public static final ModConfigSpec.ConfigValue<String> BACKPACK_CAVITY_TEXTURE;
    public static final ModConfigSpec.ConfigValue<String> ITEM_GLOW;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FANCY_MOD_IDS;
    public static final ModConfigSpec.BooleanValue KEEP_ORIGINAL_SCREEN;

    // ── JEI 조합 재료 표시 ──
    public static final ModConfigSpec.IntValue RECIPE_SECONDS;
    public static final ModConfigSpec.DoubleValue RECIPE_ICON_SCALE;
    public static final ModConfigSpec.IntValue RECIPE_MAX_SOURCES;
    public static final ModConfigSpec.BooleanValue RECIPE_SCROLL;

    // ── 조합 연출 ──
    public static final ModConfigSpec.BooleanValue AE2_ENABLED;
    public static final ModConfigSpec.BooleanValue AE2_PHYSICS;
    public static final ModConfigSpec.IntValue AE2_PHYSICS_MAX;
    public static final ModConfigSpec.BooleanValue MEKANISM_ENABLED;
    public static final ModConfigSpec.BooleanValue CRAFT_FX;
    public static final ModConfigSpec.IntValue CRAFT_FX_RADIUS;

    // ── 렌치 스캔 ──
    public static final ModConfigSpec.BooleanValue WRENCH_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WRENCH_TOOLS;
    public static final ModConfigSpec.IntValue WRENCH_RADIUS;
    public static final ModConfigSpec.IntValue WRENCH_MAX_ITEMS;
    public static final ModConfigSpec.DoubleValue WRENCH_ORBIT_RADIUS;
    public static final ModConfigSpec.DoubleValue WRENCH_ICON_SCALE;
    public static final ModConfigSpec.DoubleValue WRENCH_SELECT_ANGLE;
    public static final ModConfigSpec.BooleanValue WRENCH_LABELS;
    public static final ModConfigSpec.IntValue WRENCH_MAX_LABELED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.translation("insight_addons.configuration.containers").push("containers");
        CONTAINER_BLOCKS = b.comment("3D 표시와 렌치 스캔의 대상이 되는 블록 ID 목록입니다. *는 아무 글자와 일치합니다.",
                        "예: \"mymod:*\" 는 그 모드의 모든 블록, \"minecraft:*shulker_box\" 는 모든 셜커 상자")
                .translation("insight_addons.configuration.containers.blocks").defineListAllowEmpty("blocks", List.of(
                        "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
                        "minecraft:ender_chest", "minecraft:*shulker_box",
                        "create:item_vault", "create:chute", "create:smart_chute", "create:*_toolbox",
                        "sophisticatedbackpacks:*",
                        "sophisticatedstorage:*chest*", "sophisticatedstorage:*barrel*",
                        "sophisticatedstorage:*shulker_box*"
                ), () -> "", o -> o instanceof String);
        b.pop();

        b.translation("insight_addons.configuration.memory").push("memory");
        MEMORY_ENABLED = b.comment("보관함을 열면 그 내용물을 기억해 두었다가 옆에 3D로 표시합니다.",
                        "서버에 이 모드가 없어도 동작합니다. 열었던 시점의 내용이라, 다른 사람이나 자동화가 바꾼 건 다시 열어야 반영됩니다.")
                .translation("insight_addons.configuration.memory.enabled").define("enabled", true);
        b.pop();

        b.translation("insight_addons.configuration.nearDisplay").push("nearDisplay");
        NEAR_ENABLED = b.comment("바라보는 보관함 옆에 내용물을 3D 아이템으로 띄웁니다. 시선을 떼면 사라집니다.")
                .translation("insight_addons.configuration.nearDisplay.enabled").define("enabled", true);
        NEAR_MAX_ICONS = b.comment("보관함 하나에 띄울 최대 아이템 종류 수. 많이 넣으면 작은 아이콘이 격자로 늘어납니다. (Jade의 Shift 표시처럼)")
                .translation("insight_addons.configuration.nearDisplay.maxStacks").defineInRange("maxStacks", 56, 1, 120);
        NEAR_MAIN_SCALE = b.comment("가장 많은 아이템(앞에 크게 나오는 것)의 크기").translation("insight_addons.configuration.nearDisplay.mainIconScale").defineInRange("mainIconScale", 0.6, 0.2, 1.5);
        NEAR_ICON_SCALE = b.comment("나머지 아이템의 작은 아이콘 크기").translation("insight_addons.configuration.nearDisplay.gridIconScale").defineInRange("gridIconScale", 0.28, 0.1, 1.0);
        STICKY_DISPLAY = b.comment("시선이 보관함에서 벗어나도 마지막으로 본 보관함의 표시를 계속 유지합니다. 다른 보관함을 바라보면 그 보관함으로 바뀝니다.")
                .translation("insight_addons.configuration.nearDisplay.stickyDisplay").define("stickyDisplay", true);
        STICKY_DISTANCE = b.comment("유지 표시가 이 거리(블록)보다 멀어지면 꺼집니다.")
                .translation("insight_addons.configuration.nearDisplay.stickyDistance").defineInRange("stickyDistance", 24, 4, 96);
        CLICK_TAKE = b.comment("표시된 3D 아이템을 바라보고 왼쪽 클릭하면 그 아이템을 보관함에서 인벤토리로 가져옵니다. (Shift+클릭: 전부)",
                        "서버에도 이 모드가 있어야 합니다.")
                .translation("insight_addons.configuration.nearDisplay.clickToTake").define("clickToTake", true);
        NEAR_FRAME = b.comment("보관함 종류에 맞는 색의 테두리를 표시합니다.").translation("insight_addons.configuration.nearDisplay.frame").define("frame", true);
        NEAR_UPGRADES = b.comment("배낭의 업그레이드를 작은 아이콘으로 표시합니다. (배낭을 열어 본 뒤부터)").translation("insight_addons.configuration.nearDisplay.showUpgrades").define("showUpgrades", true);
        b.pop();

        b.translation("insight_addons.configuration.insightChestsIntegration").push("insightChestsIntegration");
        INSIGHT_MODE_ENABLED = b.comment("InSight Chests가 설치되어 있으면, 아래 모드의 블록을 열 때도 InSight Chests의 상자 연출을 적용합니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.enabled").define("enabled", true);
        INSIGHT_MODE_BLOCKS = b.comment("상자 연출(카메라 전환, 유리 화면, 3D 아이템)을 적용할 블록 ID 패턴 (*는 와일드카드)")
                .translation("insight_addons.configuration.insightChestsIntegration.blocks").defineListAllowEmpty("blocks", List.of("sophisticatedbackpacks:*", "sophisticatedstorage:*",
                                "minecraft:barrel", "minecraft:*shulker_box"),
                        () -> "", o -> o instanceof String);
        GLASS_SKIN = b.comment("true면 Sophisticated 화면의 배경과 패널(슬롯, 업그레이드 탭, 설정 패널)을 InSight Chests 스타일의 유리로 바꿉니다.",
                        "기능(업그레이드 탭, 정렬, 설정 등)은 원래 그대로입니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.glassSkin").define("glassSkin", true);
        GLASS_ALPHA = b.comment("유리 패널의 투명도 값(0~255). -1이면 InSight Chests의 uiBackgroundOpacity 설정을 그대로 따릅니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.glassAlpha").defineInRange("glassAlpha", -1, -1, 255);
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
        BACKPACK_CAVITY_TEXTURE = b.comment("배낭 내부(바닥/벽) 질감. 리소스팩이 바꾼 블록 질감도 그대로 반영됩니다.")
                .translation("insight_addons.configuration.insightChestsIntegration.backpackCavityTexture").define("backpackCavityTexture", "minecraft:textures/block/brown_terracotta.png");
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

        b.translation("insight_addons.configuration.recipeFocus").push("recipeFocus");
        RECIPE_SECONDS = b.comment("JEI에서 조합 재료 표시 버튼을 누르면 이 시간(초) 동안 재료를 3D로 띄웁니다. 버튼을 다시 누르면 바로 꺼집니다.")
                .translation("insight_addons.configuration.recipeFocus.seconds").defineInRange("seconds", 90, 10, 600);
        RECIPE_ICON_SCALE = b.translation("insight_addons.configuration.recipeFocus.iconScale").defineInRange("iconScale", 0.5, 0.2, 1.5);
        RECIPE_MAX_SOURCES = b.comment("재료 하나당 표시할 최대 보관함 수 (가까운 순)")
                .translation("insight_addons.configuration.recipeFocus.maxSources").defineInRange("maxSources", 8, 1, 24);
        RECIPE_SCROLL = b.comment("재료 표시 중 마우스 휠을 굴리면 다음 재료로 넘어가 그 재료가 든 보관함을 강조합니다. (이 동안은 핫바 스크롤이 막힙니다)")
                .translation("insight_addons.configuration.recipeFocus.scrollToSelect").define("scrollToSelect", true);
        b.pop();

        b.translation("insight_addons.configuration.ae2").push("ae2");
        AE2_ENABLED = b.comment("Applied Energistics 2의 ME 상자/드라이브/인터페이스를 바라보면 그 ME 저장소 내용을 3D로 표시하고 클릭으로 꺼냅니다.",
                        "ME 상자는 안에 든 셀, 드라이브와 인터페이스는 연결된 네트워크 전체를 보여줍니다. 서버에도 이 모드가 있어야 합니다.")
                .translation("insight_addons.configuration.ae2.enabled").define("enabled", true);
        AE2_PHYSICS = b.comment("AE2 단말기를 열었을 때 아이템을 격자 진열대 대신, 유리 상자 안에 물리로 쏟아져 쌓이는 연출로 보여줍니다.",
                        "꺼내면 해당 아이템이 플레이어에게 날아가고, 넣으면 위에서 떨어집니다. 기본은 꺼져 있습니다.")
                .translation("insight_addons.configuration.ae2.physics").define("physics", false);
        AE2_PHYSICS_MAX = b.comment("물리 연출에서 동시에 그리는 아이템 조각의 최대 개수. 낮추면 가벼워집니다.")
                .translation("insight_addons.configuration.ae2.physicsMax").defineInRange("physicsMax", 80, 10, 200);
        b.pop();

        b.translation("insight_addons.configuration.mekanism").push("mekanism");
        MEKANISM_ENABLED = b.comment("Mekanism의 개인 상자와 저장통(Bin)도 바라보면 내용물을 3D로 표시하고 꺼낼 수 있게 합니다.")
                .translation("insight_addons.configuration.mekanism.enabled").define("enabled", true);
        b.pop();

        b.translation("insight_addons.configuration.craftEffect").push("craftEffect");
        CRAFT_FX = b.comment("작업대 등에서 조합할 때, 재료 아이템이 보관함이나 플레이어에게서 작업대로 날아가는 연출을 보여줍니다.")
                .translation("insight_addons.configuration.craftEffect.enabled").define("enabled", true);
        CRAFT_FX_RADIUS = b.comment("재료가 든 보관함을 찾을 반경(블록). 기억해 둔 보관함 기준입니다.")
                .translation("insight_addons.configuration.craftEffect.radius").defineInRange("radius", 12, 2, 48);
        b.pop();

        b.translation("insight_addons.configuration.wrenchScan").push("wrenchScan");
        WRENCH_ENABLED = b.comment("렌치 도구(Create 렌치, Mekanism 구성기, AE2 석영 렌치 등)를 들면 주변 보관함의 해당 모드 아이템을 플레이어 주변에 띄웁니다.")
                .translation("insight_addons.configuration.wrenchScan.enabled").define("enabled", true);
        WRENCH_TOOLS = b.comment("렌치 역할을 하는 도구와, 그 도구를 들었을 때 보여줄 아이템의 모드 ID 목록입니다. 형식: \"도구아이템ID=모드ID\"",
                        "모드 ID를 비우면(\"도구ID=\") 모든 아이템을 보여줍니다. 다른 모드의 도구도 여기에 한 줄 추가하면 됩니다.")
                .translation("insight_addons.configuration.wrenchScan.tools").defineListAllowEmpty("tools", List.of(
                        "create:wrench=create",
                        "mekanism:configurator=mekanism",
                        "ae2:certus_quartz_wrench=ae2",
                        "ae2:nether_quartz_wrench=ae2"
                ), () -> "", o -> o instanceof String str && str.contains("="));
        WRENCH_RADIUS = b.comment("스캔 반경(블록). 서버는 최대 64까지만 응답합니다.")
                .translation("insight_addons.configuration.wrenchScan.radius").defineInRange("radius", 24, 4, 64);
        WRENCH_MAX_ITEMS = b.comment("플레이어 주변에 띄울 최대 아이템 종류 수")
                .translation("insight_addons.configuration.wrenchScan.maxItems").defineInRange("maxItems", 14, 1, 32);
        WRENCH_ORBIT_RADIUS = b.comment("플레이어를 도는 아이템 궤도 반지름")
                .translation("insight_addons.configuration.wrenchScan.orbitRadius").defineInRange("orbitRadius", 1.7, 0.8, 4.0);
        WRENCH_ICON_SCALE = b.translation("insight_addons.configuration.wrenchScan.iconScale").defineInRange("iconScale", 0.5, 0.2, 1.5);
        WRENCH_SELECT_ANGLE = b.comment("시선과 아이템 방향의 코사인 임계값. 1에 가까울수록 정확히 조준해야 선택됩니다.")
                .translation("insight_addons.configuration.wrenchScan.selectCosine").defineInRange("selectCosine", 0.985, 0.9, 0.9995);
        WRENCH_LABELS = b.comment("선택한 아이템이 든 보관함과 바라보는 보관함 위에 이름과 개수를 표시합니다. (벽 너머에서도 보입니다)")
                .translation("insight_addons.configuration.wrenchScan.sourceLabels").define("sourceLabels", true);
        WRENCH_MAX_LABELED = b.comment("라벨을 표시할 최대 보관함 수 (가까운 순)")
                .translation("insight_addons.configuration.wrenchScan.maxLabeledContainers").defineInRange("maxLabeledContainers", 16, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private InsightConfig() {}
}
