package dev.insightaddons.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * Sophisticated 화면의 배경/패널을 InSight Chests와 같은 어두운 반투명 유리로 그리는 도우미.
 * 색과 투명도는 InSight Chests의 GlassChestScreen과 같은 값을 씁니다.
 *
 * 리소스팩 지원: 아래 경로에 이미지를 넣으면 유리 대신 그 이미지로 그립니다.
 *  - assets/insight_addons/textures/gui/glass_panel.png   (16x16, 모서리 4px 9분할)
 *  - assets/insight_addons/textures/gui/glass_control.png (16x16, 모서리 4px 9분할)
 *  - assets/insight_addons/textures/gui/glass_slot.png    (18x18)
 */
public final class GlassSkin {
    private static final String STORAGE_SCREEN = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase";
    private static final String SETTINGS_SCREEN = "net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen";
    private static final Map<Class<?>, Boolean> SCREEN_CACHE = new HashMap<>();
    private static Boolean insightPresent;
    /** 지금 열린 Sophisticated 화면이 "설치된 보관함 블록"에서 열린 것인지. B키로 연 손에 든/멘 배낭은 false. */
    private static boolean placed;
    private static final Map<Class<?>, Boolean> AE_CACHE = new HashMap<>();

    private static final ResourceLocation TEX_PANEL = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/gui/glass_panel.png");
    private static final ResourceLocation TEX_CONTROL = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/gui/glass_control.png");
    private static final ResourceLocation TEX_SLOT = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/gui/glass_slot.png");

    private static final long RECHECK_MS = 3000;
    private static final Map<ResourceLocation, long[]> PACK_CACHE = new HashMap<>();

    private GlassSkin() {}

    /** 설정이 켜져 있고 InSight Chests가 설치되어 있으면 true */
    public static boolean enabled() {
        if (insightPresent == null) {
            insightPresent = ModList.get().isLoaded("insightchests");
        }
        return insightPresent && placed && InsightConfig.GLASS_SKIN.get();
    }

    public static void setPlaced(boolean value) {
        placed = value;
    }

    public static boolean placed() {
        return placed;
    }

    /** AE2 ME 단말기 계열 화면(조합 단말기, 패턴 인코딩 단말기 등)인지 */
    public static boolean isAeTerminal(Object screen) {
        if (!(screen instanceof AbstractContainerScreen<?>) || !ModList.get().isLoaded("ae2")
                || !InsightConfig.AE2_ENABLED.get()) {
            return false;
        }
        return AE_CACHE.computeIfAbsent(screen.getClass(), k -> {
            for (Class<?> x = k; x != null; x = x.getSuperclass()) {
                if (x.getName().equals("appeng.client.gui.me.common.MEStorageScreen")) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean isStorageClass(Class<?> c) {
        return SCREEN_CACHE.computeIfAbsent(c, k -> {
            for (Class<?> x = k; x != null; x = x.getSuperclass()) {
                if (x.getName().equals(STORAGE_SCREEN) || x.getName().equals(SETTINGS_SCREEN)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** 지금 열린 화면이 Sophisticated 저장소/배낭 화면인지 */
    public static boolean inStorageScreen() {
        if (!enabled()) {
            return false;
        }
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractContainerScreen<?> && isStorageClass(screen.getClass());
    }

    /** 설정과 상관없이, 화면 클래스가 Sophisticated 저장소 화면인지 */
    public static boolean isStorageScreenClass(Object screen) {
        return screen instanceof AbstractContainerScreen<?> && isStorageClass(screen.getClass());
    }

    /** 주어진 화면 객체가 Sophisticated 저장소 화면인지 (배경 그리기 억제용) */
    public static boolean isStorageScreen(Object screen) {
        return enabled() && screen instanceof AbstractContainerScreen<?> && isStorageClass(screen.getClass());
    }

    /** Sophisticated가 쓰는 어두운 회색 글씨를 유리 위에서 읽히는 밝은 색으로 */
    public static int recolor(int color) {
        if (!inStorageScreen()) {
            return color;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (r < 0x70 && g < 0x70 && b < 0x70) {
            return (color & 0xFF000000) | 0x00E6EAF5;
        }
        return color;
    }

    // ── 리소스팩 이미지 확인 (3초마다 다시 확인해서 리소스팩을 바꿔도 반영됨) ──
    private static boolean pack(ResourceLocation tex) {
        long now = System.currentTimeMillis();
        long[] entry = PACK_CACHE.get(tex);
        if (entry == null || now - entry[1] > RECHECK_MS) {
            boolean present = Minecraft.getInstance().getResourceManager().getResource(tex).isPresent();
            entry = new long[]{present ? 1 : 0, now};
            PACK_CACHE.put(tex, entry);
        }
        return entry[0] == 1;
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** InSight Chests와 같은 패널 알파: min(uiBackgroundOpacity*255, 35) */
    private static int panelAlpha() {
        int override = InsightConfig.GLASS_ALPHA.get();
        if (override >= 0) {
            return override;
        }
        return Math.min(Math.round((float) InsightBridge.uiOpacity() * 255f), 35);
    }

    /** 글씨가 들어가는 컨트롤은 패널보다 조금 더 진하게 */
    private static int controlAlpha() {
        return Math.min(255, panelAlpha() + 30);
    }

    private static void nineSlice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        final int c = 4;
        final int t = 16;
        if (w < c * 2 || h < c * 2) {
            g.blit(tex, x, y, w, h, 0f, 0f, t, t, t, t);
            return;
        }
        int mw = w - c * 2;
        int mh = h - c * 2;
        int mt = t - c * 2;
        g.blit(tex, x, y, c, c, 0f, 0f, c, c, t, t);
        g.blit(tex, x + w - c, y, c, c, t - c, 0f, c, c, t, t);
        g.blit(tex, x, y + h - c, c, c, 0f, t - c, c, c, t, t);
        g.blit(tex, x + w - c, y + h - c, c, c, t - c, t - c, c, c, t, t);
        g.blit(tex, x + c, y, mw, c, c, 0f, mt, c, t, t);
        g.blit(tex, x + c, y + h - c, mw, c, c, t - c, mt, c, t, t);
        g.blit(tex, x, y + c, c, mh, 0f, c, c, mt, t, t);
        g.blit(tex, x + w - c, y + c, c, mh, t - c, c, c, mt, t, t);
        g.blit(tex, x + c, y + c, mw, mh, c, c, mt, mt, t, t);
    }

    /** 큰 메인 패널 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (pack(TEX_PANEL)) {
            nineSlice(g, TEX_PANEL, x, y, w, h);
            return;
        }
        g.fill(x, y, x + w, y + h, argb(panelAlpha(), 0x141428));
        g.renderOutline(x, y, w, h, 0x45FFFFFF);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x18FFFFFF);
    }

    /** 업그레이드 탭, 설정 패널 같은 작은 패널 */
    public static void control(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 3 || h < 3) {
            return;
        }
        if (pack(TEX_CONTROL)) {
            nineSlice(g, TEX_CONTROL, x, y, w, h);
            return;
        }
        g.fill(x, y, x + w, y + h, argb(controlAlpha(), 0x141428));
        g.renderOutline(x, y, w, h, 0x45FFFFFF);
    }

    /** 슬롯 한 칸 (18x18, 아이템은 안쪽 16x16) */
    public static void cell(GuiGraphics g, int x, int y) {
        if (pack(TEX_SLOT)) {
            g.blit(TEX_SLOT, x, y, 0f, 0f, 18, 18, 18, 18);
            return;
        }
        g.fill(x + 1, y + 1, x + 17, y + 17, 0x10FFFFFF);
        g.renderOutline(x, y, 18, 18, 0x36FFFFFF);
    }

    /** 열 x 행 슬롯 격자 */
    public static void cells(GuiGraphics g, int x, int y, int columns, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                cell(g, x + c * 18, y + r * 18);
            }
        }
    }

    /**
     * 원래 배경 그림에 같이 들어 있던 칸들(플레이어 인벤토리, 업그레이드 칸)을 따로 그려 줍니다.
     * Sophisticated 슬롯(저장 칸)은 GuiHelper가 따로 그리므로 여기서는 제외합니다.
     */
    public static void extraCells(GuiGraphics g, int left, int top, AbstractContainerMenu menu, boolean upgradeSlotsOnly) {
        for (Slot slot : menu.slots) {
            boolean upgrade = slot.getClass().getName().contains("UpgradeSlot");
            if (upgradeSlotsOnly ? upgrade : slot.container instanceof Inventory) {
                cell(g, left + slot.x - 1, top + slot.y - 1);
            }
        }
    }
}
