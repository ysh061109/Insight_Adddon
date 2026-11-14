package dev.insightaddons.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 조합 연출: 작업대 등에서 결과 칸을 눌러 조합하면, 재료 아이템이 (기억해 둔) 보관함 또는 플레이어에게서
 * 작업대로 날아가 합쳐지는 모습을 3D로 보여줍니다.
 * 서버 정보 없이 클라이언트에서 추정합니다: 재료가 플레이어 인벤토리에 더 남아 있으면 플레이어에게서,
 * 없고 근처에 그 재료를 가진 기억된 보관함이 있으면 그 보관함에서 날아옵니다.
 */
public final class CraftFx {
    private record Fly(ItemStack stack, Vec3 from, Vec3 to, BlockPos sourceBox, long start, int duration) {}

    private static final List<Fly> FLIES = new ArrayList<>();
    private static final int FLY_TICKS = 22;

    private CraftFx() {}

    public static boolean active() {
        return !FLIES.isEmpty();
    }

    public static void clear() {
        FLIES.clear();
    }

    /** 결과 칸을 눌렀을 때 호출합니다. 조합 칸의 재료를 읽어 연출을 만든다. */
    public static void onResultClicked(Minecraft mc, AbstractContainerMenu menu, Slot clicked) {
        if (!InsightConfig.CRAFT_FX.get() || mc.player == null || mc.level == null) {
            return;
        }
        // 서버에도 이 모드가 있으면 서버가 연출 정보를 보내 주므로 여기서는 만들지 않는다 (중복 방지)
        if (ClientEvents.serverHasMod()) {
            return;
        }
        if (!(clicked instanceof ResultSlot) || !clicked.hasItem()) {
            return;
        }
        // 조합 칸의 재료 (같은 아이템은 합친다)
        Map<Item, ItemStack> grid = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof CraftingContainer && slot.hasItem()) {
                ItemStack st = slot.getItem();
                grid.merge(st.getItem(), st.copy(), (a, b) -> {
                    a.grow(b.getCount());
                    return a;
                });
            }
        }
        if (grid.isEmpty()) {
            return;
        }

        ClientLevel level = mc.level;
        Player player = mc.player;
        long now = level.getGameTime();

        // 목적지: 방금 우클릭한 작업대, 없으면 플레이어 앞
        Vec3 to;
        BlockPos station = ContainerMemory.lastClickPos();
        if (station != null && now - ContainerMemory.lastClickTime() < 20L * 60 * 10
                && station.distToCenterSqr(player.position()) < 64) {
            to = Vec3.atCenterOf(station).add(0, 0.6, 0);
        } else {
            Vec3 look = player.getLookAngle();
            to = player.getEyePosition().add(look.x * 1.2, -0.5, look.z * 1.2);
        }

        Vec3 hand = player.position().add(0, player.getBbHeight() * 0.55, 0);
        double radius = InsightConfig.CRAFT_FX_RADIUS.get();
        int i = 0;
        for (ItemStack ingredient : grid.values()) {
            Vec3 from = hand;
            BlockPos box = null;
            int held = player.getInventory().countItem(ingredient.getItem());
            if (held <= 0) {
                // 플레이어가 더 들고 있지 않으면 근처에 기억된 보관함에서 온 것으로 본다
                double best = radius * radius;
                for (ContainerMemory.Memo memo : ContainerMemory.memos()) {
                    Integer have = memo.all.get(ingredient.getItem());
                    if (have == null || have <= 0) {
                        continue;
                    }
                    for (BlockPos p : memo.positions) {
                        double d = Vec3.atCenterOf(p).distanceToSqr(player.position());
                        if (d < best) {
                            best = d;
                            box = p;
                        }
                    }
                }
                if (box != null) {
                    from = Vec3.atCenterOf(box);
                }
            }
            ItemStack shown = ingredient.copy();
            shown.setCount(1);
            // 재료마다 조금씩 시차를 두고 출발
            FLIES.add(new Fly(shown, from, to, box, now + i * 3L, FLY_TICKS));
            i++;
        }
    }

    /** 서버가 알려 준 다른 플레이어(또는 내) 조합 연출 */
    public static void addRemote(dev.insightaddons.extras.net.CraftFxPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!InsightConfig.CRAFT_FX.get() || mc.level == null) {
            return;
        }
        if (ScreenFx.wanted(mc)) {
            // 휴대용 배낭 메뉴: 3D 연출은 GUI에 가려지므로 화면 위에 그린다
            List<Item> items = new ArrayList<>();
            for (dev.insightaddons.extras.net.CraftFxPacket.Fly f : packet.flies()) {
                items.add(f.item());
            }
            ScreenFx.spawn(mc, items);
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 to = Vec3.atCenterOf(packet.station()).add(0, 0.6, 0);
        int i = 0;
        for (dev.insightaddons.extras.net.CraftFxPacket.Fly f : packet.flies()) {
            Vec3 from = new Vec3(f.x(), f.y(), f.z());
            BlockPos box = f.fromContainer() ? BlockPos.containing(from) : null;
            FLIES.add(new Fly(new ItemStack(f.item()), from, to, box, now + i * 3L, FLY_TICKS));
            i++;
        }
    }

    private static Vec3 pendingFrom;
    private static BlockPos pendingPos;
    private static Item pendingItem;

    /** 꺼내기를 요청한 순간 바라보던 아이콘의 위치를 기억해 둔다 (결과가 오면 거기서 출발) */
    public static void noteTake(BlockPos pos, Item item, Vec3 iconPos) {
        pendingPos = pos;
        pendingItem = item;
        pendingFrom = iconPos;
    }

    /** 서버가 알려 준 실제 꺼낸 개수만큼, 아이콘 자리에서 플레이어 쪽으로 날아온다 */
    public static void addTake(dev.insightaddons.extras.net.TakeResult r) {
        Minecraft mc = Minecraft.getInstance();
        if (!InsightConfig.CRAFT_FX.get() || mc.level == null || mc.player == null || r.moved() <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 from = pendingFrom != null && r.pos().equals(pendingPos) && r.item() == pendingItem
                ? pendingFrom : Vec3.atCenterOf(r.pos());
        Vec3 look = mc.player.getLookAngle();
        Vec3 to = mc.player.getEyePosition().add(look.x * 0.7, -0.45, look.z * 0.7);
        // 꺼낸 개수만큼 날린다 (많으면 최대 24개까지만 그리고, 그 이상은 한 번에 여러 개를 대표하게 한다)
        int n = Math.min(r.moved(), 24);
        for (int i = 0; i < n; i++) {
            FLIES.add(new Fly(new ItemStack(r.item()), from, to, i == 0 ? r.pos() : null, now + i, 14));
        }
    }

    public static void tick(ClientLevel level) {
        if (FLIES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        FLIES.removeIf(f -> now > f.start() + f.duration() + 4);
    }

    public static void render(PoseStack ps, Vec3 camPos, Minecraft mc, float partialTick,
                              MultiBufferSource.BufferSource buffers) {
        if (FLIES.isEmpty() || mc.level == null) {
            return;
        }
        float now = mc.level.getGameTime() + partialTick;

        // 출발한 보관함은 잠깐 금색으로 깜빡인다 (벽 너머에서도 보임)
        VertexConsumer lines = null;
        for (Fly f : FLIES) {
            float t = (now - f.start()) / f.duration();
            if (f.sourceBox() != null && t > -0.2f && t < 1.0f) {
                if (lines == null) {
                    lines = buffers.getBuffer(ModRenderTypes.LINES_NO_DEPTH);
                }
                AABB box = new AABB(f.sourceBox()).inflate(0.02).move(-camPos.x, -camPos.y, -camPos.z);
                LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 0.85f, 0.2f, 1.0f - Math.max(0, t));
            }
        }
        if (lines != null) {
            buffers.endBatch(ModRenderTypes.LINES_NO_DEPTH);
        }

        for (Fly f : FLIES) {
            float t = (now - f.start()) / f.duration();
            if (t < 0f || t > 1f) {
                continue;
            }
            // 부드럽게 가속하며 호를 그리고, 끝에서 작아지며 합쳐진다
            float e = t * t * (3f - 2f * t);
            Vec3 p = f.from().lerp(f.to(), e).add(0, Math.sin(Math.PI * e) * 0.7, 0);
            float scale = 0.34f * (1.0f - 0.55f * e);
            ps.pushPose();
            ps.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            ps.mulPose(Axis.YP.rotationDegrees(now * 14f));
            ps.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(f.stack(), ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
            ps.popPose();
        }
        buffers.endBatch();
    }
}
