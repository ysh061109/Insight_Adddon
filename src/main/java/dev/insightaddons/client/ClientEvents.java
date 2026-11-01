package dev.insightaddons.client;

import dev.insightaddons.InsightAddons;
import dev.insightaddons.net.ScanRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = InsightAddons.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static int ticks;
    private static ResourceKey<Level> lastDimension;
    private static Boolean insightPresent;
    private static int targetSeenTick;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCache.clearAll();
        ContainerMemory.clear();
        RecipeFocus.clear();
        CraftFx.clear();
        CraftTable.clear();
        AeScene.clear();
        HoverTracker.clear();
        lastDimension = null;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            ContainerMemory.onRightClick(event.getPos().immutable(), event.getLevel().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !insightChestsPresent()) {
            ClientCache.clearAll();
            RecipeFocus.clear();
            return;
        }
        ticks++;
        handleKeys(mc, player, level);

        // 차원이 바뀌면 좌표가 겹칠 수 있으니 기억을 비운다
        if (lastDimension != level.dimension()) {
            if (lastDimension != null) {
                ContainerMemory.clear();
                ClientCache.clearAll();
                RecipeFocus.clear();
            }
            lastDimension = level.dimension();
        }

        // 열린 보관함 기억 (서버 모드 없이도 동작)
        HoverTracker.tick(mc);
        ContainerMemory.tick(mc, level, player);
        CraftFx.tick(level);
        CraftTable.tick(mc);
        AeScene.tick(mc);

        // JEI "조합 재료 표시" 중에는 다른 표시를 모두 끄고 재료만 보여준다
        if (RecipeFocus.isActive()) {
            ClientCache.setTarget(null);
            ClientCache.clearNear();
            ClientCache.clearWrench();
            RecipeFocus.tick(level);
            if (RecipeFocus.isActive()) {
                boolean first = RecipeFocus.rows().isEmpty();  // 처음 한 번은 빨리 집계
                if ((ticks % 20 == 5 || (first && ticks % 5 == 0)) && serverHasMod()) {
                    List<BlockPos> positions = collect(level, player.blockPosition(),
                            InsightConfig.WRENCH_RADIUS.get(), 160);
                    PacketDistributor.sendToServer(
                            new ScanRequest(2, "", positions, RecipeFocus.wantedIds()));
                }
                if (ticks % 10 == 0 || first) {
                    RecipeFocus.rebuild(level, player.position());
                }
            }
            return;
        }

        // ① 바라보는 보관함 하나만 표시: 시선이 닿은 보관함을 찾고,
        //    서버에 이 모드가 있으면 그 하나만 실시간으로 요청한다 (0.5초마다)
        if (InsightConfig.NEAR_ENABLED.get()) {
            updateTarget(mc, level);
            BlockPos target = ClientCache.target();
            if (target != null && ticks % 10 == 0 && serverHasMod()) {
                PacketDistributor.sendToServer(new ScanRequest(0, "", List.of(target), List.of()));
            }
        } else {
            ClientCache.setTarget(null);
            ClientCache.clearNear();
        }

        // ② 렌치 스캔: 렌치를 든 동안 집계는 0.5초마다, 서버 요청은 1초마다
        String ns = wrenchNamespace(player.getMainHandItem());
        if (ns == null) {
            ns = wrenchNamespace(player.getOffhandItem());
        }
        activeWrenchNs = ns == null ? "" : ns;
        boolean holdingWrench = ns != null;
        if (InsightConfig.WRENCH_ENABLED.get() && holdingWrench) {
            if (ticks % 20 == 10 && serverHasMod()) {
                List<BlockPos> positions = collect(level, player.blockPosition(),
                        InsightConfig.WRENCH_RADIUS.get(), 160);
                PacketDistributor.sendToServer(
                        new ScanRequest(1, activeWrenchNs, positions, List.of()));
            }
            if (ticks % 10 == 0) {
                ClientCache.rebuildWrench(level, player.position());
            }
        } else {
            ClientCache.clearWrench();
        }
    }

    /** 모드 단축키 처리: 보관함 검색, 이름표, 재료 목록 */
    private static void handleKeys(Minecraft mc, LocalPlayer player, ClientLevel level) {
        while (Keys.SEARCH.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ItemSearchScreen());
            }
        }
        while (Keys.NAME.consumeClick()) {
            if (mc.screen != null) {
                continue;
            }
            BlockPos target = null;
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                if (ContainerFilter.matches(level, pos, id) && level.getBlockEntity(pos) != null) {
                    target = pos.immutable();
                }
            }
            if (target != null) {
                mc.setScreen(new NameScreen(target));
            } else {
                player.displayClientMessage(Component.translatable("insight_addons.name.look"), true);
            }
        }
        while (Keys.LIST_ADD.consumeClick()) {
            if (RecipeFocus.isActive() && ShoppingList.addCurrent() > 0) {
                player.displayClientMessage(Component.translatable("insight_addons.list.added"), true);
                Fx.sound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.3f, 0.5f);
            } else {
                player.displayClientMessage(Component.translatable("insight_addons.list.need_focus"), true);
            }
        }
        while (Keys.LIST_TOGGLE.consumeClick()) {
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                ShoppingList.clear();
                player.displayClientMessage(Component.translatable("insight_addons.list.cleared"), true);
            } else if (!ShoppingList.isEmpty()) {
                boolean now = ShoppingList.toggleVisible();
                player.displayClientMessage(Component.translatable(now ? "insight_addons.list.shown" : "insight_addons.list.hidden"), true);
            } else {
                player.displayClientMessage(Component.translatable("insight_addons.list.empty"), true);
            }
        }
    }

    /** 3D 표시의 개수/이름 글씨를 화면 위에 그린다. */
    @SubscribeEvent
    public static void onHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        StorageRenderer.renderHud(event.getGuiGraphics(), Minecraft.getInstance());
        ShoppingList.renderHud(event.getGuiGraphics(), Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !insightChestsPresent()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        StorageRenderer.render(event.getPoseStack(), event.getCamera(), mc, partialTick);
        if (AeScene.visible()) {
            AeScene.render(event.getPoseStack(), event.getCamera(), event.getCamera().getPosition(), mc, partialTick,
                    mc.renderBuffers().bufferSource());
        }
        if (CraftTable.visible()) {
            CraftTable.render(event.getPoseStack(), event.getCamera(), event.getCamera().getPosition(), mc, partialTick,
                    mc.renderBuffers().bufferSource());
        }
        if (CraftFx.active()) {
            CraftFx.render(event.getPoseStack(), event.getCamera().getPosition(), mc, partialTick,
                    mc.renderBuffers().bufferSource());
        }
    }

    /**
     * 시선이 닿은 보관함을 갱신한다. 시선을 떼면 잠깐(4틱) 뒤에 사라져서 깜빡임을 막는다.
     * 대상이 바뀌면 이전 보관함의 실시간 데이터는 즉시 버린다.
     */
    private static void updateTarget(Minecraft mc, ClientLevel level) {
        BlockPos found = null;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (ContainerFilter.matches(level, pos, id) && level.getBlockEntity(pos) != null) {
                found = pos.immutable();
            }
        }
        BlockPos current = ClientCache.target();
        if (found != null) {
            if (!found.equals(current)) {
                ClientCache.clearNear();
            }
            ClientCache.setTarget(found);
            targetSeenTick = ticks;
        } else if (current != null) {
            // 시선을 벗어나도 마지막으로 본 보관함을 계속 보여 준다. 다른 보관함을 보면 그쪽으로 바뀐다.
            // 너무 멀어지거나 블록이 사라지면 끈다.
            boolean keep = InsightConfig.STICKY_DISPLAY.get()
                    && mc.player != null
                    && Vec3.atCenterOf(current).distanceToSqr(mc.player.getEyePosition())
                    <= Math.pow(InsightConfig.STICKY_DISTANCE.get(), 2)
                    && level.getBlockEntity(current) != null;
            if (!keep && ticks - targetSeenTick > 4) {
                ClientCache.setTarget(null);
                ClientCache.clearNear();
            }
        }
    }

    /** InSight Chests가 설치된 클라이언트에서만 이 모드의 기능이 동작합니다. */
    private static boolean insightChestsPresent() {
        if (insightPresent == null) {
            insightPresent = ModList.get().isLoaded("insightchests");
        }
        return insightPresent;
    }

    /** 접속한 서버(또는 싱글플레이 내장 서버)에 이 모드의 통신 채널이 있는지 확인합니다. */
    public static boolean serverHasMod() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(ScanRequest.TYPE);
    }

    private static String activeWrenchNs = "";

    /** 지금 들고 있는 렌치 도구가 보여줄 아이템 모드 ID (빈 문자열이면 전부) */
    public static String wrenchNamespace() {
        return activeWrenchNs;
    }

    /** 설정의 "도구ID=모드ID" 목록에서 이 아이템에 해당하는 모드 ID를 찾는다. 도구가 아니면 null */
    private static String wrenchNamespace(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String entry : InsightConfig.WRENCH_TOOLS.get()) {
            int eq = entry.indexOf('=');
            if (eq > 0 && entry.substring(0, eq).trim().equals(id)) {
                return entry.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /**
     * 클라이언트에 로드된 블록 엔티티 중 설정한 대상 블록의 좌표를 가까운 순으로 모읍니다.
     * 실제 내용물은 서버가 읽으므로 여기서는 아이템 핸들러 여부를 따지지 않습니다.
     */
    private static List<BlockPos> collect(ClientLevel level, BlockPos center, int radius, int max) {
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        double r2 = (double) radius * radius;

        List<BlockPos> out = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.distSqr(center) > r2) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                    if (!ContainerFilter.matches(level, pos, id)) {
                        continue;
                    }
                    out.add(pos.immutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
