package dev.insightaddons.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 머리 위 작업대 연출.
 * 작업대(조합 칸 + 결과 칸이 있는 화면)를 열면 플레이어 머리 앞쪽 위에 조합 칸이 떠서,
 * 플레이어가 재료를 올리고 빼는 대로 아이템이 날아와 놓이고 사라집니다. 결과 칸 옆에는 지금 만들어질 결과가 미리 보입니다.
 * 결과를 꺼내 조합하면 완성품이 크게 떠올라 반짝이며(파티클, 소리) 자연스럽게 사라집니다.
 * 클라이언트에서만 계산하므로 다른 플레이어에게는 보이지 않습니다.
 */
public final class CraftTable {
    private static final ItemStack[] CELLS = new ItemStack[9];
    private static final long[] BORN = new long[9];
    private static ItemStack preview = ItemStack.EMPTY;
    private static long previewBorn;
    private static int gridW = 3;
    private static int gridH = 3;
    private static float anim;
    private static float prevAnim;
    private static boolean open;

    private static ItemStack burst = ItemStack.EMPTY;
    private static long burstStart = -1;
    /** 실제로 만들어진 개수를 나중에 인벤토리 변화로 확인하기 위한 값 */
    private static ItemStack pendingItem = ItemStack.EMPTY;
    private static int pendingBefore;
    private static long pendingStart = -1;
    private static final int BURST_IN = 8;
    private static final int BURST_HOLD = 30;
    private static final int BURST_OUT = 18;
    private static final double PITCH = 0.27;

    static {
        for (int i = 0; i < CELLS.length; i++) {
            CELLS[i] = ItemStack.EMPTY;
        }
    }

    private CraftTable() {}

    private static void sound(SoundEvent event, float pitch, float volume) {
        Fx.sound(event, pitch, volume);
    }

    public static void clear() {
        for (int i = 0; i < CELLS.length; i++) {
            CELLS[i] = ItemStack.EMPTY;
        }
        preview = ItemStack.EMPTY;
        burst = ItemStack.EMPTY;
        burstStart = -1;
        pendingStart = -1;
        pendingItem = ItemStack.EMPTY;
        anim = 0;
        prevAnim = 0;
        open = false;
    }

    /** 결과 칸을 눌렀을 때: 완성품 연출 시작 */
    public static void onResult(ItemStack result, AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (!InsightConfig.CRAFT_FX.get() || mc.level == null || mc.player == null || result.isEmpty() || !open) {
            return;
        }
        burst = result.copy();
        // Shift+클릭은 재료가 떨어질 때까지 반복해서 만들므로, 그만큼 곱한 개수를 보여준다
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            int total = shiftTotal(mc.player, menu, result);
            if (total > burst.getCount()) {
                burst.setCount(total);
            }
        }
        burstStart = mc.level.getGameTime();
        pendingItem = result.copy();
        pendingBefore = countOwned(mc.player, result);
        pendingStart = burstStart;
        sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.9f, 1.0f);
        sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 0.5f);
        Vec3 o = origin(mc, 1f).add(0, 0.55, 0);
        for (int i = 0; Fx.particles() && i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2;
            mc.level.addParticle(ParticleTypes.END_ROD, o.x, o.y, o.z,
                    Math.cos(a) * 0.08, 0.04 + mc.level.random.nextDouble() * 0.04, Math.sin(a) * 0.08);
        }
        for (int i = 0; Fx.particles() && i < 10; i++) {
            mc.level.addParticle(ParticleTypes.FIREWORK, o.x, o.y, o.z,
                    (mc.level.random.nextDouble() - 0.5) * 0.18, 0.05 + mc.level.random.nextDouble() * 0.08,
                    (mc.level.random.nextDouble() - 0.5) * 0.18);
        }
    }

    /**
     * Shift+클릭으로 한꺼번에 만들 때의 총 개수 추정. 작업대/스톤커터처럼 한 번에 재료를 1개씩 쓰는 메뉴만 곱한다.
     * 인벤토리에 들어갈 수 있는 개수까지만 센다.
     */
    private static int shiftTotal(Player player, AbstractContainerMenu menu, ItemStack result) {
        boolean repeating = menu instanceof net.minecraft.world.inventory.CraftingMenu
                || menu instanceof net.minecraft.world.inventory.InventoryMenu
                || menu instanceof net.minecraft.world.inventory.StonecutterMenu;
        Layout l = layout(menu);
        if (!repeating || l == null) {
            return result.getCount();
        }
        int crafts = Integer.MAX_VALUE;
        for (Slot s : l.inputs()) {
            ItemStack st = s.getItem();
            if (!st.isEmpty()) {
                crafts = Math.min(crafts, st.getCount());
            }
        }
        if (crafts == Integer.MAX_VALUE || crafts < 1) {
            return result.getCount();
        }
        long total = (long) result.getCount() * crafts;
        // 인벤토리 빈 공간 + 같은 아이템 남은 자리
        long room = 0;
        int max = result.getMaxStackSize();
        for (int i = 0; i < 36; i++) {
            ItemStack inv = player.getInventory().getItem(i);
            if (inv.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameComponents(inv, result)) {
                room += Math.max(0, inv.getMaxStackSize() - inv.getCount());
            }
        }
        return (int) Math.max(result.getCount(), Math.min(total, room));
    }

    /** 인벤토리(보조 손 포함)와 마우스에 든 것까지 합쳐 같은 아이템 개수 */
    private static int countOwned(Player player, ItemStack like) {
        int n = 0;
        for (ItemStack st : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(st, like)) {
                n += st.getCount();
            }
        }
        for (ItemStack st : player.getInventory().offhand) {
            if (ItemStack.isSameItemSameComponents(st, like)) {
                n += st.getCount();
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (ItemStack.isSameItemSameComponents(carried, like)) {
            n += carried.getCount();
        }
        return n;
    }

    /** 클릭 뒤 몇 틱 지나 서버 응답이 반영되면, 늘어난 개수를 실제로 만들어진 개수로 보여준다 (Shift 반복 제작 포함) */
    private static void settleCount(Minecraft mc, long now) {
        if (pendingStart < 0 || pendingItem.isEmpty()) {
            return;
        }
        long age = now - pendingStart;
        if (age < 2) {
            return;
        }
        int delta = countOwned(mc.player, pendingItem) - pendingBefore;
        if (delta > 0 && burstStart >= 0 && !burst.isEmpty()) {
            burst.setCount(delta);
        }
        // 응답이 오고도 변화가 안정될 시간을 준 뒤 확정
        if (age >= 8 || (delta > 0 && age >= 5)) {
            pendingStart = -1;
            pendingItem = ItemStack.EMPTY;
        }
    }

    private static Vec3 origin(Minecraft mc, float partial) {
        Player p = mc.player;
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        return eye.add(flat.scale(1.5)).add(0, 0.85, 0);
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        settleCount(mc, now);
        AbstractContainerMenu menu = mc.screen instanceof AbstractContainerScreen<?> acs ? acs.getMenu() : null;
        Layout layout = menu == null ? null : layout(menu);
        Slot result = layout == null ? null : layout.result();
        List<Slot> grid = layout == null ? List.of() : layout.inputs();
        open = InsightConfig.CRAFT_FX.get() && layout != null && !grid.isEmpty() && grid.size() <= 9;

        prevAnim = anim;
        anim += ((open ? 1f : 0f) - anim) * 0.25f;
        if (!open && anim < 0.02f) {
            anim = 0;
        }

        if (open) {
            gridW = layout.w();
            gridH = layout.h();
            for (int i = 0; i < 9; i++) {
                ItemStack now0 = i < grid.size() ? grid.get(i).getItem() : ItemStack.EMPTY;
                ItemStack old = CELLS[i];
                if (!now0.isEmpty() && !ItemStack.isSameItem(old, now0)) {
                    BORN[i] = now;
                    sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.1f + 0.05f * i, 0.55f);
                } else if (now0.isEmpty() && !old.isEmpty()) {
                    sound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.4f);
                }
                CELLS[i] = now0.copy();
            }
            ItemStack res = result == null ? ItemStack.EMPTY : result.getItem();
            if (!res.isEmpty() && !ItemStack.isSameItem(preview, res)) {
                previewBorn = now;
                sound(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.3f, 0.35f);
            }
            preview = res.copy();
        } else if (anim == 0) {
            for (int i = 0; i < 9; i++) {
                CELLS[i] = ItemStack.EMPTY;
            }
            preview = ItemStack.EMPTY;
        }

        // 완성품이 떠 있는 동안 반짝임
        if (burstStart >= 0) {
            long age = now - burstStart;
            if (age > BURST_IN + BURST_HOLD + BURST_OUT) {
                burstStart = -1;
                burst = ItemStack.EMPTY;
            } else if (age < BURST_IN + BURST_HOLD && age % 2 == 0 && Fx.particles()) {
                Vec3 o = origin(mc, 1f).add(0, 0.55, 0);
                mc.level.addParticle(ParticleTypes.ENCHANT, o.x + (mc.level.random.nextDouble() - 0.5) * 0.6,
                        o.y + (mc.level.random.nextDouble() - 0.3) * 0.5, o.z + (mc.level.random.nextDouble() - 0.5) * 0.6,
                        0, 0.05, 0);
            }
        }
    }

    /** 재료 칸들과 결과 칸 (결과 칸이 없는 양조기 같은 것은 result가 null) */
    private record Layout(List<Slot> inputs, Slot result, int w, int h) {}

    private static Layout byIndex(AbstractContainerMenu menu, int resultIndex, int... inputIdx) {
        List<Slot> in = new ArrayList<>();
        for (int i : inputIdx) {
            if (i < menu.slots.size()) {
                in.add(menu.slots.get(i));
            }
        }
        Slot res = resultIndex >= 0 && resultIndex < menu.slots.size() ? menu.slots.get(resultIndex) : null;
        return in.isEmpty() ? null : new Layout(in, res, in.size(), 1);
    }

    private static Layout moddedLayout(AbstractContainerMenu menu) {
        Slot result = null;
        List<Slot> grid = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            String n = slot.getClass().getSimpleName();
            if (slot.getClass().getName().startsWith("appeng.")) {
                if (n.equals("CraftingTermSlot")) {
                    result = slot;
                } else if (n.equals("CraftingMatrixSlot")) {
                    grid.add(slot);
                }
            } else if (slot instanceof ResultSlot && menu.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("sophisticated")) {
                result = slot;
                grid.clear();
                for (int k = Math.max(0, i - 9); k < i; k++) {
                    grid.add(menu.slots.get(k));
                }
            }
        }
        if (result == null || grid.isEmpty() || grid.size() > 9) {
            return null;
        }
        return new Layout(grid, result, 3, Math.max(1, (grid.size() + 2) / 3));
    }

    private static Layout layout(AbstractContainerMenu menu) {
        // 조합 칸 + 결과 칸이 있는 화면 (작업대, 인벤토리 2x2 조합 등)
        Slot result = null;
        List<Slot> grid = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot) {
                result = slot;
            } else if (slot.container instanceof CraftingContainer) {
                grid.add(slot);
            }
        }
        if (result != null && !grid.isEmpty()) {
            CraftingContainer cc = (CraftingContainer) grid.get(0).container;
            return new Layout(grid, result, Math.max(1, cc.getWidth()), Math.max(1, cc.getHeight()));
        }
        // 다른 모드의 조합 칸: 서바이벌 배낭(결과 칸 앞의 9칸), AE2 조합 터미널(CraftingTermSlot / CraftingMatrixSlot)
        Layout modded = moddedLayout(menu);
        if (modded != null) {
            return modded;
        }
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) {
            return byIndex(menu, 3, 0, 1, 2);
        }
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu || menu instanceof net.minecraft.world.inventory.GrindstoneMenu
                || menu instanceof net.minecraft.world.inventory.CartographyTableMenu
                || menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu) {
            return byIndex(menu, 2, 0, 1);
        }
        if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) {
            return byIndex(menu, 1, 0);
        }
        if (menu instanceof net.minecraft.world.inventory.LoomMenu) {
            return byIndex(menu, 3, 0, 1, 2);
        }
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) {
            return byIndex(menu, -1, 3, 0, 1, 2, 4);
        }
        return null;
    }

    /** 이 화면에서 "결과를 꺼내는" 칸인지 (조합 연출 시작용) */
    public static boolean isResultSlot(AbstractContainerMenu menu, Slot slot) {
        Layout l = layout(menu);
        if (l == null || l.result() != slot) {
            return false;
        }
        // 모루는 경험치가 모자라면 꺼낼 수 없다
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu anvil) {
            Player p = Minecraft.getInstance().player;
            return p != null && (p.getAbilities().instabuild || p.experienceLevel >= anvil.getCost());
        }
        return true;
    }

    public static boolean visible() {
        return anim > 0.01f || burstStart >= 0;
    }

    private static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }

    private static double easeOutCubic(double t) {
        double x = 1 - t;
        return 1 - x * x * x;
    }

    public static void render(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc, float partial,
                              MultiBufferSource.BufferSource buffers) {
        if (!visible() || mc.level == null || mc.player == null) {
            return;
        }
        float a = prevAnim + (anim - prevAnim) * partial;
        float time = mc.level.getGameTime() + partial;
        long now = mc.level.getGameTime();
        Player p = mc.player;
        Vec3 look = p.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 origin = origin(mc, partial).add(0, Math.sin(time * 0.06) * 0.012, 0);
        Vec3 from = p.getEyePosition(partial).add(0, -0.45, 0).add(flat.scale(0.35));

        if (a > 0.01f) {
            // 조합 칸 테두리
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            PoseStack.Pose pose = ps.last();
            int alpha = (int) (200 * a);
            for (int r = 0; r < gridH; r++) {
                for (int c = 0; c < gridW; c++) {
                    Vec3 cell = cellPos(origin, right, up, c, r);
                    drawSquare(lines, pose, cell.subtract(camPos), right, up, PITCH * 0.46 * a, alpha, 255, 255, 255, flat);
                }
            }
            // 결과 칸 + 화살표
            Vec3 resPos = origin.add(right.scale((gridW / 2.0) * PITCH + 0.42));
            drawSquare(lines, pose, resPos.subtract(camPos), right, up, PITCH * 0.5 * a, alpha, 255, 216, 90, flat);
            Vec3 ar0 = origin.add(right.scale((gridW / 2.0) * PITCH + 0.1)).subtract(camPos);
            Vec3 ar1 = origin.add(right.scale((gridW / 2.0) * PITCH + 0.24)).subtract(camPos);
            line(lines, pose, ar0, ar1, alpha, 255, 216, 90, flat);
            line(lines, pose, ar1, ar1.add(right.scale(-0.04)).add(up.scale(0.035)), alpha, 255, 216, 90, flat);
            line(lines, pose, ar1, ar1.add(right.scale(-0.04)).add(up.scale(-0.035)), alpha, 255, 216, 90, flat);
            buffers.endBatch(RenderType.lines());

            // 놓인 재료: 새로 놓이면 플레이어 쪽에서 날아와 자리를 잡는다
            for (int i = 0; i < 9; i++) {
                if (CELLS[i].isEmpty()) {
                    continue;
                }
                int c = i % gridW;
                int r = i / gridW;
                Vec3 target = cellPos(origin, right, up, c, r);
                double age = (now - BORN[i]) + partial;
                double t = Math.min(1.0, age / 9.0);
                Vec3 pos = from.add(target.subtract(from).scale(easeOutCubic(t)))
                        .add(0, Math.sin(t * Math.PI) * 0.18, 0);
                float s = (float) (0.15 * a * (0.6 + 0.4 * easeOutBack(t)));
                StorageRenderer.drawIcon(ps, camPos, pos, CELLS[i], s, time * 2f + i * 25f, buffers, mc);
            }
            // 지금 만들어질 결과 미리보기
            if (!preview.isEmpty()) {
                double age = (now - previewBorn) + partial;
                float s = (float) (0.2 * a * easeOutBack(Math.min(1.0, age / 8.0)));
                StorageRenderer.drawIcon(ps, camPos, resPos, preview, s, time * 3f, buffers, mc);
            }
        }

        // 완성품: 크게 떠올라 빛나다가 위로 올라가며 사라진다
        if (burstStart >= 0 && !burst.isEmpty()) {
            double age = (now - burstStart) + partial;
            double scale;
            double rise = 0;
            if (age < BURST_IN) {
                scale = 1.7 * easeOutBack(age / BURST_IN);
            } else if (age < BURST_IN + BURST_HOLD) {
                scale = 1.7 + Math.sin(age * 0.5) * 0.05;
                rise = (age - BURST_IN) / BURST_HOLD * 0.08;
            } else {
                double t = Math.min(1.0, (age - BURST_IN - BURST_HOLD) / BURST_OUT);
                scale = 1.7 * (1 - t * t * t);
                rise = 0.08 + t * 0.4;
            }
            Vec3 pos = origin.add(0, 0.55 + rise, 0);
            if (scale > 0.02) {
                StorageRenderer.drawIcon(ps, camPos, pos, burst, (float) scale * 0.3f, (float) (age * 14), buffers, mc);
                if (age < BURST_IN + BURST_HOLD) {
                    StorageRenderer.drawLabel(ps, camera, camPos, pos.add(0, -0.34, 0),
                            burst.getHoverName().copy().append(Component.literal(" ×" + burst.getCount())),
                            0.016f, 0xFFFFD84A, 0.0, 4);
                }
            }
        }
        buffers.endBatch();
    }

    private static Vec3 cellPos(Vec3 origin, Vec3 right, Vec3 up, int col, int row) {
        double x = (col - (gridW - 1) / 2.0) * PITCH;
        double y = ((gridH - 1) / 2.0 - row) * PITCH;
        return origin.add(right.scale(x)).add(up.scale(y));
    }

    private static void drawSquare(VertexConsumer lines, PoseStack.Pose pose, Vec3 c, Vec3 right, Vec3 up,
                                   double half, int alpha, int r, int g, int b, Vec3 n) {
        Vec3 p00 = c.add(right.scale(-half)).add(up.scale(-half));
        Vec3 p10 = c.add(right.scale(half)).add(up.scale(-half));
        Vec3 p11 = c.add(right.scale(half)).add(up.scale(half));
        Vec3 p01 = c.add(right.scale(-half)).add(up.scale(half));
        line(lines, pose, p00, p10, alpha, r, g, b, n);
        line(lines, pose, p10, p11, alpha, r, g, b, n);
        line(lines, pose, p11, p01, alpha, r, g, b, n);
        line(lines, pose, p01, p00, alpha, r, g, b, n);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 a, Vec3 b, int alpha, int r, int g, int bl, Vec3 unused) {
        Vec3 d = b.subtract(a);
        double len = Math.max(1.0e-4, d.length());
        float nx = (float) (d.x / len);
        float ny = (float) (d.y / len);
        float nz = (float) (d.z / len);
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha).setNormal(pose, nx, ny, nz);
    }
}
