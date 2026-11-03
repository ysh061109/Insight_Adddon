package dev.insightaddons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.insightaddons.net.ScanResult;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 월드 안에 3D 아이템/라벨/테두리를 그립니다. */
public final class StorageRenderer {
    private static final Map<Item, ItemStack> ICONS = new IdentityHashMap<>();

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD84A;
    private static final int GRAY = 0xFFB0B0B0;

    /** 보관함 종류에 맞춘 색 */
    private enum Theme {
        CHEST(0.78f, 0.57f, 0.29f),
        BARREL(0.66f, 0.45f, 0.25f),
        SHULKER(0.63f, 0.44f, 0.78f),
        BACKPACK(0.72f, 0.47f, 0.22f),
        VAULT(0.78f, 0.78f, 0.82f),
        ME(0.35f, 0.72f, 0.95f),
        MEKANISM(0.55f, 0.85f, 0.62f),
        OTHER(0.9f, 0.9f, 0.9f);

        final float r;
        final float g;
        final float b;

        Theme(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        int argb() {
            return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
        }

        static Theme of(ResourceLocation id) {
            String p = id.getPath();
            if (id.getNamespace().equals("ae2")) {
                return ME;
            }
            if (id.getNamespace().equals("mekanism")) {
                return MEKANISM;
            }
            if (p.contains("backpack")) {
                return BACKPACK;
            }
            if (p.contains("shulker")) {
                return SHULKER;
            }
            if (p.contains("barrel")) {
                return BARREL;
            }
            if (p.contains("vault") || p.contains("chute") || p.contains("toolbox")) {
                return VAULT;
            }
            if (p.contains("chest")) {
                return CHEST;
            }
            return OTHER;
        }
    }

    /** 2차 패스에서 그릴 테두리 */
    private record Frame(Vec3 lo, Vec3 hi, Vec3 side, Theme theme) {}

    // 지금 십자선이 가리키는 3D 아이템 (클릭하면 인벤토리로 가져온다)
    private static Item hoverItem;
    private static BlockPos hoverPos;
    private static double hoverBestT;
    private static Item prevHoverItem;

    public static Item hoveredItem() {
        return hoverItem;
    }

    public static BlockPos hoveredPos() {
        return hoverPos;
    }

    /** 시선 방향 직선과 구(중심 c, 반지름 r)의 교차 거리. 안 닿으면 -1 */
    private static double rayHit(Vec3 eye, Vec3 look, Vec3 c, double r) {
        Vec3 to = c.subtract(eye);
        double t = to.dot(look);
        if (t < 0.2) {
            return -1;
        }
        double d2 = to.lengthSqr() - t * t;
        return d2 <= r * r ? t : -1;
    }

    private static boolean considerHover(Vec3 eye, Vec3 look, Vec3 center, double radius, Item item, BlockPos pos) {
        double t = rayHit(eye, look, center, radius);
        if (t >= 0 && t < hoverBestT) {
            hoverBestT = t;
            hoverItem = item;
            hoverPos = pos;
        }
        return item == prevHoverItem;
    }

    /** 글씨가 아이템에 묻히지 않도록 카메라 쪽으로 살짝 당긴다 */
    private static Vec3 front(Vec3 p, Vec3 camPos, double distance) {
        Vec3 dir = camPos.subtract(p);
        double len = dir.length();
        return len < 1.0e-4 ? p : p.add(dir.scale(distance / len));
    }

    private StorageRenderer() {}

    public static void render(PoseStack ps, Camera camera, Minecraft mc, float partialTick) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        beginFrame();
        Vec3 camPos = camera.getPosition();
        float time = mc.level.getGameTime() + partialTick;
        prevHoverItem = hoverItem;
        hoverItem = null;
        hoverPos = null;
        hoverBestT = 1.0e9;

        // JEI 조합 재료 표시 중에는 다른 표시를 모두 숨기고 재료만 보여준다
        if (RecipeFocus.isActive()) {
            renderRecipe(ps, camera, camPos, mc, buffers, time, partialTick);
            flush(buffers);
            return;
        }

        if (InsightConfig.NEAR_ENABLED.get()) {
            renderNear(ps, camera, camPos, mc, buffers, time, partialTick);
        }
        if (InsightConfig.WRENCH_ENABLED.get()) {
            renderWrench(ps, camera, camPos, mc, buffers, time, partialTick);
        }
        flush(buffers);
    }

    // ────────────────────────────────────────────────────────────────
    // ① 보관함 옆 3D 표시: 가장 많은 아이템이 앞에 크게, 나머지는 뒤쪽 줄에 작게
    // ────────────────────────────────────────────────────────────────
    private static void renderNear(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc,
                                   MultiBufferSource.BufferSource buffers, float time, float partialTick) {
        // 화면(인벤토리 등)이 열려 있을 때와 보관함을 바라보지 않을 때는 그리지 않는다
        BlockPos target = ClientCache.target();
        if (target == null || mc.screen != null) {
            return;
        }
        ClientCache.View view = ClientCache.targetView(mc.level, target);
        if (view == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getViewVector(partialTick);
        boolean clickable = InsightConfig.CLICK_TAKE.get() && ClientEvents.serverHasMod();
        Component hovered = null;
        int hoveredCount = 0;
        int maxStacks = InsightConfig.NEAR_MAX_ICONS.get();
        float mainScale = InsightConfig.NEAR_MAIN_SCALE.get().floatValue();
        float gridScale = InsightConfig.NEAR_ICON_SCALE.get().floatValue();
        boolean showUpgrades = InsightConfig.NEAR_UPGRADES.get();
        List<Frame> frames = new ArrayList<>();

        Vec3 center = Vec3.atCenterOf(view.pos());
        double dist = Math.max(1.0, center.distanceTo(eye));
        // 멀리서도 개수 글씨가 읽히도록 거리에 따라 글씨를 키운다 (1.0 ~ 2.4배)
        float textBoost = (float) Math.max(1.0, Math.min(2.4, dist / 2.6));

        // 플레이어가 서 있는 쪽 면에 맞춰 보관함 "옆"에 배치
        double dx = eye.x - center.x;
        double dz = eye.z - center.z;
        Vec3 side;
        Vec3 tangent;
        if (Math.abs(dx) >= Math.abs(dz)) {
            double s = dx >= 0 ? 1 : -1;
            side = new Vec3(s, 0, 0);
            tangent = new Vec3(0, 0, -s);
        } else {
            double s = dz >= 0 ? 1 : -1;
            side = new Vec3(0, 0, s);
            tangent = new Vec3(s, 0, 0);
        }
        Vec3 base = center.add(side.scale(0.9)).add(0, 0.1, 0);
        Theme theme = Theme.of(view.block());
        int color = theme.argb();

        List<ScanResult.Stack> stacks = view.stacks();
        int n = Math.min(maxStacks, stacks.size());
        int others = Math.max(0, n - 1);

        // 나머지 아이템은 작은 아이콘 격자: 가로로 넓게, 줄이 늘어나면 위로 쌓는다
        double cell = Math.max(0.2, 0.62 * gridScale + 0.06);
        double rowH = cell * 1.25;
        int cols = others == 0 ? 0 : Math.min(10, Math.max(3, (int) Math.ceil(Math.sqrt(others * 1.8))));
        int gridRows = others == 0 ? 0 : (others + cols - 1) / cols;
        double ySecondary = 0.28 + 0.4 * mainScale;
        double gridTop = ySecondary + Math.max(0, gridRows - 1) * rowH;
        double headerBase = (others > 0 ? gridTop + 0.28 : 0.42 + 0.3 * mainScale);
        if (n == 0) {
            headerBase = 0.35;
        }

        // 가장 많은 아이템: 앞에 크게 (개수 글씨는 항상 보이게 SEE_THROUGH)
        if (n > 0) {
            ScanResult.Stack primary = stacks.get(0);
            Vec3 p = base.add(side.scale(0.12));
            boolean hv = clickable && considerHover(eye, look, p, 0.32 * mainScale + 0.08, primary.item(), view.pos());
            float ms = hv ? mainScale * 1.25f : mainScale;
            drawIcon(ps, camPos, p, icon(primary.item()), ms, time * 2.5f, buffers, mc);
            drawCount(ps, camera, camPos, front(p, camPos, 0.25), ms, compact(primary.count()), hv ? GOLD : WHITE, 3);
            if (hv) {
                hovered = icon(primary.item()).getHoverName();
                hoveredCount = primary.count();
            }
        } else {
            drawText(ps, camera, camPos, base.add(0, 0.1, 0),
                    Component.translatable("insight_addons.empty"), 0.014f * textBoost, GRAY, true, buffers, mc.font);
        }

        // 나머지: 개수가 붙은 작은 아이콘 (위쪽 줄부터 채운다)
        for (int i = 1; i < n; i++) {
            ScanResult.Stack stack = stacks.get(i);
            int idx = i - 1;
            int r = idx / cols;
            int c = idx % cols;
            int inRow = Math.min(cols, others - r * cols);
            double xOff = (c - (inRow - 1) / 2.0) * cell;
            double yOff = ySecondary + (gridRows - 1 - r) * rowH;
            Vec3 p = base.add(side.scale(-0.05)).add(tangent.scale(xOff)).add(0, yOff, 0);
            boolean hv = clickable && considerHover(eye, look, p, 0.5 * cell, stack.item(), view.pos());
            float gs = hv ? gridScale * 1.4f : gridScale;
            drawIcon(ps, camPos, p, icon(stack.item()), gs, time * 2.5f + i * 30f, buffers, mc);
            // 작은 아이콘의 개수는 자기 칸 폭 안에 들어갈 때만 그린다 (겹치면 지저분하므로)
            drawCount(ps, camera, camPos, front(p, camPos, 0.25), gs, compact(stack.count()), hv ? GOLD : WHITE, hv ? 3 : 1);
            if (hv) {
                hovered = icon(stack.item()).getHoverName();
                hoveredCount = stack.count();
            }
        }

        // 이름 · 합계 · 기록 시각은 한 줄로 합쳐서 보여준다
        Component name = blockName(mc, view.block(), view.pos());
        String custom = ContainerNames.get(mc.level, view.pos());
        MutableComponent header = custom == null ? name.copy()
                : Component.literal("\u2605 " + custom).append(Component.literal("  (")).append(name).append(Component.literal(")"));
        if (n > 0) {
            header.append(Component.literal("  ·  ")).append(
                    Component.translatable("insight_addons.total", compact(view.total()), view.kinds()));
        }
        if (view.ageTicks() >= 0) {
            header.append(Component.literal("  ·  ")).append(ageText(view.ageTicks()));
        }
        drawLabel(ps, camera, camPos, base.add(0, headerBase + 0.2, 0), header, 0.014f * textBoost, color, 0.0, 4);

        // 바라보는 아이템은 십자선 아래에 이름과 정확한 개수, 조작법을 크게 보여준다
        if (hovered != null) {
            drawFixed(Component.translatable("insight_addons.take.target", hovered, hoveredCount), GOLD, 18f);
            drawFixed(Component.translatable("insight_addons.take.hint2"), GRAY, 30f);
        }

        // 배낭 업그레이드
        int upCount = 0;
        double yUp = headerBase + 0.62;
        if (showUpgrades && !view.upgrades().isEmpty()) {
            upCount = Math.min(10, view.upgrades().size());
            double upSpacing = 0.28;
            for (int i = 0; i < upCount; i++) {
                double offset = (i - (upCount - 1) / 2.0) * upSpacing;
                Vec3 p = base.add(tangent.scale(offset)).add(0, yUp, 0);
                drawIcon(ps, camPos, p, icon(view.upgrades().get(i)), 0.2f, time * 2f + i * 20f, buffers, mc);
            }
            drawLabel(ps, camera, camPos, base.add(0, yUp + 0.2, 0),
                    Component.translatable("insight_addons.upgrades"), 0.010f * textBoost, GOLD, 0.0, 1);
        }

        // 보관함 종류 색의 테두리
        double halfW = Math.max(0.6, Math.max(cols * cell / 2.0 + 0.2, upCount * 0.28 / 2.0 + 0.2));
        double yHigh = upCount > 0 ? yUp + 0.35 : headerBase + 0.45;
        frames.add(new Frame(
                base.add(tangent.scale(-halfW)).add(0, -0.42, 0),
                base.add(tangent.scale(halfW)).add(0, yHigh, 0),
                side, theme));
        flush(buffers);

        if (InsightConfig.NEAR_FRAME.get()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (Frame f : frames) {
                AABB box = new AABB(f.lo().x, f.lo().y, f.lo().z, f.hi().x, f.hi().y, f.hi().z)
                        .inflate(f.side().x != 0 ? 0.01 : 0, 0, f.side().z != 0 ? 0.01 : 0)
                        .move(-camPos.x, -camPos.y, -camPos.z);
                LevelRenderer.renderLineBox(ps, lines, box, f.theme().r, f.theme().g, f.theme().b, 0.9f);
            }
            buffers.endBatch(RenderType.lines());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // ② 렌치 스캔: 플레이어 주변을 도는 3D 아이템 + 보관함 표시
    // ────────────────────────────────────────────────────────────────
    private static void renderWrench(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc,
                                     MultiBufferSource.BufferSource buffers, float time, float partialTick) {
        List<ClientCache.Agg> aggs = ClientCache.wrench();
        if (aggs.isEmpty()) {
            return;
        }

        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getViewVector(partialTick);
        Vec3 center = new Vec3(eye.x, eye.y - 0.25, eye.z);
        double orbit = InsightConfig.WRENCH_ORBIT_RADIUS.get();
        float scale = InsightConfig.WRENCH_ICON_SCALE.get().floatValue();
        double selectCos = InsightConfig.WRENCH_SELECT_ANGLE.get();

        int n = aggs.size();
        Vec3[] pos = new Vec3[n];
        int selected = -1;
        double best = selectCos;
        for (int i = 0; i < n; i++) {
            double angle = time * 0.035 + i * (Math.PI * 2 / n);
            pos[i] = center.add(Math.cos(angle) * orbit,
                    Math.sin(time * 0.09 + i * 1.7) * 0.08,
                    Math.sin(angle) * orbit);
            double dot = pos[i].subtract(eye).normalize().dot(look);
            if (dot > best) {
                best = dot;
                selected = i;
            }
        }

        // 플레이어 주변을 도는 3D 아이템 + 개수
        for (int i = 0; i < n; i++) {
            ClientCache.Agg agg = aggs.get(i);
            boolean sel = i == selected;
            float s = scale * (sel ? 1.5f : 1.0f);
            drawIcon(ps, camPos, pos[i], icon(agg.item()), s, time * 3f + i * 40f, buffers, mc);
            double slot = Math.min(0.9, 2 * Math.PI * orbit / Math.max(1, n) * 0.9);
            drawLabel(ps, camera, camPos, pos[i].add(0, -(0.12 + 0.2 * s), 0),
                    Component.literal(compact(agg.count())), 0.014f, sel ? GOLD : WHITE, sel ? 0.0 : slot, sel ? 3 : 1);
            if (sel) {
                drawLabel(ps, camera, camPos, pos[i].add(0, 0.2 + 0.3 * s, 0),
                        icon(agg.item()).getHoverName(), 0.014f, GOLD, 0.0, 4);
                drawLabel(ps, camera, camPos, pos[i].add(0, 0.1 + 0.3 * s, 0),
                        Component.translatable("insight_addons.wrench.selected",
                                compact(agg.count()), agg.sources().size()),
                        0.011f, WHITE, 0.0, 3);
            }
        }
        flush(buffers);

        List<ClientCache.View> views = ClientCache.wrenchViews();
        Set<BlockPos> highlight = selected >= 0 ? new HashSet<>(aggs.get(selected).sources()) : Set.of();

        // 모든 해당 보관함의 테두리 (벽 너머에서도 보임), 선택한 아이템이 든 곳은 금색 + 연결선
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.LINES_NO_DEPTH);
        for (ClientCache.View v : views) {
            AABB box = new AABB(v.pos()).inflate(0.01).move(-camPos.x, -camPos.y, -camPos.z);
            if (highlight.contains(v.pos())) {
                LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 0.85f, 0.2f, 1.0f);
            } else {
                LevelRenderer.renderLineBox(ps, lines, box, 0.3f, 0.9f, 0.9f, 0.5f);
            }
        }
        if (selected >= 0) {
            PoseStack.Pose pose = ps.last();
            Vec3 from = pos[selected].subtract(camPos);
            for (BlockPos bp : highlight) {
                Vec3 to = Vec3.atCenterOf(bp).subtract(camPos);
                Vec3 nrm = to.subtract(from).normalize();
                lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                        .setColor(255, 216, 74, 255)
                        .setNormal(pose, (float) nrm.x, (float) nrm.y, (float) nrm.z);
                lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                        .setColor(255, 216, 74, 255)
                        .setNormal(pose, (float) nrm.x, (float) nrm.y, (float) nrm.z);
            }
        }
        buffers.endBatch(ModRenderTypes.LINES_NO_DEPTH);

        // 모든 보관함 위에 "아이템 이름 x개수"를 항상 표시 (선택한 아이템이 든 곳은 크게 + 거리)
        if (InsightConfig.WRENCH_LABELS.get()) {
            int maxLabeled = InsightConfig.WRENCH_MAX_LABELED.get();
            int labeled = 0;
            BlockPos gazed = ClientCache.target();
            for (ClientCache.View v : views) {
                if (labeled >= maxLabeled) {
                    break;
                }
                boolean hi = highlight.contains(v.pos());
                // 겹쳐 보이지 않도록 선택한 아이템이 든 보관함과 바라보는 보관함에만 라벨을 붙인다
                if (v.stacks().isEmpty() || (!hi && !v.pos().equals(gazed))) {
                    continue;
                }
                labeled++;
                Vec3 c = Vec3.atCenterOf(v.pos());

                ScanResult.Stack shown = v.stacks().get(0);
                if (hi) {
                    Item selItem = aggs.get(selected).item();
                    for (ScanResult.Stack s : v.stacks()) {
                        if (s.item() == selItem) {
                            shown = s;
                            break;
                        }
                    }
                }
                MutableComponent line = icon(shown.item()).getHoverName().copy()
                        .append(Component.literal(" x" + compact(shown.count())));
                if (!hi && v.stacks().size() > 1) {
                    line.append(Component.literal(" +" + (v.stacks().size() - 1)));
                }
                drawLabel(ps, camera, camPos, c.add(0, 0.95, 0), line,
                        hi ? 0.020f : 0.012f, hi ? GOLD : WHITE, 0.0, hi ? 4 : 0);
                if (hi) {
                    long dist = Math.round(c.distanceTo(eye));
                    drawLabel(ps, camera, camPos, c.add(0, 1.25, 0),
                            Component.literal(dist + "m"), 0.014f, GRAY, 0.0, 3);
                }
            }
            flush(buffers);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // ③ JEI 조합 재료 표시: 재료를 플레이어 앞에 3D로 띄우고, 어느 보관함에 있는지 표시
    // ────────────────────────────────────────────────────────────────
    private static final int[] PALETTE = {
            0xFFFF6B6B, 0xFF6BCBFF, 0xFFFFD84A, 0xFF7BE07B, 0xFFE08BFF,
            0xFFFFA14A, 0xFF4AE0C8, 0xFFB0B0FF, 0xFFFF8AC8
    };

    /** 인벤토리 개수가 필요 개수에 가까울수록 초록: 빨강 → 주황 → 노랑 → 초록 */
    private static int ratioColor(int have, int need) {
        double r = need <= 0 ? 1.0 : (double) have / need;
        if (r >= 1.0) {
            return 0xFF55FF55;
        }
        if (r >= 0.6) {
            return 0xFFFFE04A;
        }
        if (r >= 0.3) {
            return 0xFFFFA23A;
        }
        return 0xFFFF5555;
    }

    private static int paletteColor(int index) {
        return PALETTE[index % PALETTE.length];
    }

    private static void renderRecipe(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc,
                                     MultiBufferSource.BufferSource buffers, float time, float partialTick) {
        List<RecipeFocus.Row> rows = RecipeFocus.shownRows();
        List<RecipeFocus.Row> inInv = RecipeFocus.inventoryRows();
        List<RecipeFocus.Row> missing = RecipeFocus.missingRows();
        if (RecipeFocus.finishing()) {
            renderFinish(ps, camera, camPos, mc, buffers, time, partialTick);
            return;
        }
        if (rows.isEmpty() && inInv.isEmpty() && missing.isEmpty()) {
            return;
        }
        // 카메라가 보는 방향 정면에 놓는다: 고개를 돌리지 않아도 십자선 근처에 같이 보이도록
        org.joml.Vector3f lk = camera.getLookVector();
        org.joml.Vector3f lf = camera.getLeftVector();
        org.joml.Vector3f upv = camera.getUpVector();
        Vec3 fwd = new Vec3(lk.x, lk.y, lk.z);
        Vec3 right = new Vec3(-lf.x, -lf.y, -lf.z);
        Vec3 up = new Vec3(upv.x, upv.y, upv.z);
        Vec3 center = camPos.add(fwd.scale(2.3)).add(up.scale(0.55));
        float scale = InsightConfig.RECIPE_ICON_SCALE.get().floatValue();

        int n = rows.size();
        double spacing = 0.62;
        Vec3[] pos = new Vec3[n];
        // 마우스 휠로 고른 재료 (시선과 무관하게 항상 하나가 선택되어 있다)
        int selected = RecipeFocus.selectedIndex();
        for (int i = 0; i < n; i++) {
            pos[i] = center.add(right.scale((i - (n - 1) / 2.0) * spacing));
        }

        // 제목과 안내
        drawText(ps, camera, camPos, center.add(up.scale(0.72)),
                Component.translatable("insight_addons.recipe.title", RecipeFocus.title()),
                0.016f, GOLD, true, buffers, mc.font);
        if (n > 0) {
            drawText(ps, camera, camPos, center.add(up.scale(0.58)),
                    Component.translatable("insight_addons.recipe.hint", selected + 1, n), 0.010f, GRAY, true, buffers, mc.font);
        }
        // 인벤토리에 이미 있는 재료와, 어디에도 없는 재료는 3D 대신 글씨로만 알려 준다
        double statusY = 0.44;
        if (!inInv.isEmpty()) {
            drawText(ps, camera, camPos, center.add(up.scale(statusY)),
                    Component.translatable("insight_addons.recipe.inv", rowNames(inInv, true, false)),
                    0.011f, 0xFF7BE07B, true, buffers, mc.font);
            statusY -= 0.14;
        }
        List<RecipeFocus.Row> viaWireless = new ArrayList<>();
        for (RecipeFocus.Row r : rows) {
            if (r.wireless() > 0) {
                viaWireless.add(r);
            }
        }
        if (!viaWireless.isEmpty()) {
            Component terminal = viaWireless.get(0).wirelessTerminal() == null ? Component.empty()
                    : new ItemStack(viaWireless.get(0).wirelessTerminal()).getHoverName();
            drawText(ps, camera, camPos, center.add(up.scale(statusY)),
                    Component.translatable("insight_addons.recipe.wireless.list", terminal, rowNames(viaWireless, false, true)),
                    0.011f, 0xFF7FD4FF, true, buffers, mc.font);
            statusY -= 0.14;
        }
        if (!missing.isEmpty()) {
            drawText(ps, camera, camPos, center.add(up.scale(statusY)),
                    Component.translatable("insight_addons.recipe.missing", rowNames(missing, false, false)),
                    0.011f, 0xFFFF6B6B, true, buffers, mc.font);
        }

        // 재료 아이템 + 가진 개수 / 필요한 개수
        for (int i = 0; i < n; i++) {
            RecipeFocus.Row row = rows.get(i);
            boolean sel = i == selected;
            int needCount = Math.max(1, row.need().count());
            int inv = row.inv();
            float s = scale * (sel ? 1.4f : 1.0f);
            drawIcon(ps, camPos, pos[i], icon(row.item()), s, time * 3f + i * 40f, buffers, mc);
            // 인벤토리에 있는 개수 / 필요한 개수. 목표에 가까울수록 초록, 멀수록 빨강
            drawLabel(ps, camera, camPos, pos[i].add(up.scale(-(0.14 + 0.2 * s))),
                    Component.literal(inv + "/" + needCount),
                    0.016f, ratioColor(inv, needCount), 0.0, 4);
            drawText(ps, camera, camPos, pos[i].add(up.scale(0.18 + 0.28 * s)),
                    icon(row.item()).getHoverName(), sel ? 0.014f : 0.010f, paletteColor(i), true, buffers, mc.font);
            if (sel) {
                Component where = row.sources().isEmpty()
                        ? (row.wireless() > 0
                                ? Component.translatable("insight_addons.recipe.wireless.where", compact(row.wireless()))
                                : Component.translatable("insight_addons.recipe.none"))
                        : Component.translatable("insight_addons.wrench.selected",
                                compact(row.have()), row.sources().size());
                if (!row.sources().isEmpty() && row.wireless() > 0) {
                    where = where.copy().append(Component.literal("  +  "))
                            .append(Component.translatable("insight_addons.recipe.wireless.where", compact(row.wireless())));
                }
                drawText(ps, camera, camPos, pos[i].add(up.scale(0.05 + 0.28 * s)),
                        where, 0.011f, WHITE, true, buffers, mc.font);
            }
        }
        renderMissing(ps, camera, camPos, mc, buffers, time, missing, center, right, up, fwd, scale);
        flush(buffers);

        // 보관함 테두리와 연결선 (벽 너머에서도 보이게)
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.LINES_NO_DEPTH);
        PoseStack.Pose pose = ps.last();
        Map<BlockPos, List<int[]>> perContainer = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            RecipeFocus.Row row = rows.get(i);
            int argb = paletteColor(i);
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;
            boolean sel = i == selected;
            float alpha = sel ? 1.0f : 0.55f;
            for (RecipeFocus.Source src : row.sources()) {
                AABB box = new AABB(src.pos()).inflate(sel ? 0.03 : 0.01).move(-camPos.x, -camPos.y, -camPos.z);
                LevelRenderer.renderLineBox(ps, lines, box, r, g, b, alpha);

                Vec3 from = pos[i].subtract(camPos);
                Vec3 to = Vec3.atCenterOf(src.pos()).subtract(camPos);
                Vec3 nrm = to.subtract(from).normalize();
                int cr = (argb >> 16) & 0xFF;
                int cg = (argb >> 8) & 0xFF;
                int cb = argb & 0xFF;
                int ca = (int) (alpha * 255);
                lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                        .setColor(cr, cg, cb, ca)
                        .setNormal(pose, (float) nrm.x, (float) nrm.y, (float) nrm.z);
                lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                        .setColor(cr, cg, cb, ca)
                        .setNormal(pose, (float) nrm.x, (float) nrm.y, (float) nrm.z);

                perContainer.computeIfAbsent(src.pos(), k -> new ArrayList<>()).add(new int[]{i, src.count()});
            }
        }
        buffers.endBatch(ModRenderTypes.LINES_NO_DEPTH);

        // 각 보관함 위에 "그 보관함에 든 재료 이름 xN"
        for (Map.Entry<BlockPos, List<int[]>> e : perContainer.entrySet()) {
            Vec3 c = Vec3.atCenterOf(e.getKey());
            double y = 0.85;
            for (int[] info : e.getValue()) {
                RecipeFocus.Row row = rows.get(info[0]);
                boolean sel = info[0] == selected;
                MutableComponent line = icon(row.item()).getHoverName().copy()
                        .append(Component.literal(" x" + compact(info[1])));
                String label = ContainerNames.get(mc.level, e.getKey());
                if (label != null) {
                    line.append(Component.literal("  \u2605 " + label));
                }
                drawText(ps, camera, camPos, c.add(0, y, 0), line, sel ? 0.018f : 0.012f,
                        paletteColor(info[0]), true, buffers, mc.font);
                y += sel ? 0.28 : 0.2;
            }
            if (e.getValue().size() > 0) {
                long dist = Math.round(c.distanceTo(camPos));
                drawText(ps, camera, camPos, c.add(0, y, 0), Component.literal(dist + "m"),
                        0.011f, GRAY, true, buffers, mc.font);
            }
        }
        flush(buffers);
    }


    /** 어디에도 없는 재료: 재료 아이콘 위에 배리어 아이템과 빨간 X를 겹쳐 "없음"을 눈에 띄게 보여 준다. */
    private static void renderMissing(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc,
                                      MultiBufferSource.BufferSource buffers, float time, List<RecipeFocus.Row> missing,
                                      Vec3 center, Vec3 right, Vec3 up, Vec3 fwd, float scale) {
        int m = missing.size();
        if (m == 0) {
            return;
        }
        double spacing = 0.5;
        float s = scale * 0.85f;
        boolean fx = RecipeFocus.takeMissingFx();
        if (fx) {
            // 금고에 열쇠가 안 맞을 때의 "딸깍" 거절음 + 장치가 꺼지는 낮은 소리
            Fx.sound(net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL, 1.0f, 1.0f);
            Fx.sound(net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.45f);
        }
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.LINES_NO_DEPTH);
        PoseStack.Pose pose = ps.last();
        // 가루 파티클은 금방 사라지므로 몇 틱마다 다시 내보내 X가 계속 떠 있게 한다
        long gameTime = mc.level == null ? 0 : mc.level.getGameTime();
        // 없는 재료의 X는 파티클 없이 굵은 면으로만 그린다 (기본 파티클 제거)
        boolean emit = false;
        if (emit) {
            lastMissingEmit = gameTime;
        }
        net.minecraft.core.particles.DustParticleOptions red =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.15f, 0.15f), 1.4f);
        for (int i = 0; i < m; i++) {
            RecipeFocus.Row row = missing.get(i);
            Vec3 pos = center.add(up.scale(-0.95)).add(right.scale((i - (m - 1) / 2.0) * spacing));
            double shake = Math.sin(time * 0.9 + i) * 0.012;
            drawIcon(ps, camPos, pos.add(right.scale(shake)), icon(row.item()), s, time * 0.8f + i * 40f, buffers, mc);
            // 아이콘을 먼저 화면에 내보내야 아래 X가 아이콘 위에 덮어 그려진다
            buffers.endBatch();
            // 아이템 위에 큰 X (아이콘보다 조금 크게)
            double r = 0.53 * s;
            Vec3 front = pos.add(fwd.scale(-0.12));
            if (emit) {
                for (int d = -1; d <= 1; d += 2) {
                    for (int k = -8; k <= 8; k++) {
                        double t = k / 8.0;
                        Vec3 pp = front.add(right.scale(t * r)).add(up.scale(t * r * d));
                        mc.level.addParticle(red, pp.x, pp.y, pp.z, 0, 0, 0);
                    }
                }
                if (fx) {
                    for (int k = 0; k < 6; k++) {
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z,
                                (RANDOM.nextDouble() - 0.5) * 0.35, (RANDOM.nextDouble() - 0.5) * 0.35, (RANDOM.nextDouble() - 0.5) * 0.35);
                    }
                }
            }
            // 굵은 X: 아이콘 앞에 빨간 면으로 그린다 (어두운 테두리 + 밝은 안쪽)
            {
                float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.35 + i);
                Vec3 c = front.subtract(camPos);
                VertexConsumer quads = buffers.getBuffer(ModRenderTypes.QUADS_NO_DEPTH);
                for (int pass = 0; pass < 2; pass++) {
                    double half = (pass == 0 ? 0.085 : 0.05) * s;
                    int cr = pass == 0 ? 120 : 255;
                    int cg = pass == 0 ? 10 : 50;
                    int cb = pass == 0 ? 10 : 50;
                    int ca = (int) (255 * pulse);
                    for (int d = -1; d <= 1; d += 2) {
                        Vec3 a = c.add(right.scale(-r)).add(up.scale(-r * d));
                        Vec3 bb = c.add(right.scale(r)).add(up.scale(r * d));
                        // 대각선에 수직인 방향으로 폭을 준다
                        Vec3 n = right.scale(-d).add(up).normalize().scale(half);
                        Vec3[] v = {a.add(n), a.subtract(n), bb.subtract(n), bb.add(n)};
                        for (Vec3 p : v) {
                            quads.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(cr, cg, cb, ca);
                        }
                    }
                }
            }
            drawText(ps, camera, camPos, pos.add(up.scale(-0.2 - 0.25 * s)),
                    Component.translatable("insight_addons.recipe.lack", icon(row.item()).getHoverName())
                            .withStyle(net.minecraft.ChatFormatting.BOLD),
                    0.011f, 0xFFFF6B6B, true, buffers, mc.font);
        }
        buffers.endBatch(ModRenderTypes.LINES_NO_DEPTH);
        buffers.endBatch(ModRenderTypes.QUADS_NO_DEPTH);
    }

    private static long lastMissingEmit;

    /** 재료를 다 챙겼을 때: 재료 아이콘이 커지며 떠오르고 초록 체크와 반짝이는 파티클, 효과음이 나온다. */
    private static void renderFinish(PoseStack ps, Camera camera, Vec3 camPos, Minecraft mc,
                                     MultiBufferSource.BufferSource buffers, float time, float partialTick) {
        List<Item> items = RecipeFocus.finishItems();
        if (items.isEmpty() || mc.level == null) {
            return;
        }
        org.joml.Vector3f lk = camera.getLookVector();
        org.joml.Vector3f lf = camera.getLeftVector();
        org.joml.Vector3f upv = camera.getUpVector();
        Vec3 fwd = new Vec3(lk.x, lk.y, lk.z);
        Vec3 right = new Vec3(-lf.x, -lf.y, -lf.z);
        Vec3 up = new Vec3(upv.x, upv.y, upv.z);
        Vec3 center = camPos.add(fwd.scale(2.3)).add(up.scale(0.55));
        float p = RecipeFocus.finishProgress(mc.level, partialTick);
        float baseScale = InsightConfig.RECIPE_ICON_SCALE.get().floatValue();
        int n = items.size();
        double spacing = 0.62;
        boolean fx = RecipeFocus.takeFinishFx();
        double ease = 1 - Math.pow(1 - p, 3);
        float pop = (float) (1.0 + 0.45 * Math.sin(Math.min(1.0, p * 2.0) * Math.PI));
        for (int i = 0; i < n; i++) {
            Vec3 pos = center.add(right.scale((i - (n - 1) / 2.0) * spacing)).add(up.scale(0.35 * ease));
            drawIcon(ps, camPos, pos, icon(items.get(i)), baseScale * pop * (1f - 0.35f * p), time * 9f + i * 50f, buffers, mc);
            drawText(ps, camera, camPos, pos.add(up.scale(-0.3 * baseScale - 0.12)), Component.literal("\u2714"),
                    0.02f, 0xFF55FF55, true, buffers, mc.font);
            if (fx && Fx.particles()) {
                for (int k = 0; k < 8; k++) {
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            pos.x + (RANDOM.nextDouble() - 0.5) * 0.5, pos.y + (RANDOM.nextDouble() - 0.5) * 0.5,
                            pos.z + (RANDOM.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
                }
            }
        }
        drawText(ps, camera, camPos, center.add(up.scale(0.72 + 0.1 * ease)),
                Component.translatable("insight_addons.recipe.finish"), 0.022f, 0xFF55FF55, true, buffers, mc.font);
        if (fx && Fx.particles()) {
            for (int k = 0; k < 24; k++) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                        center.x, center.y, center.z,
                        (RANDOM.nextDouble() - 0.5) * 0.25, (RANDOM.nextDouble() - 0.2) * 0.25, (RANDOM.nextDouble() - 0.5) * 0.25);
            }
        }
        flush(buffers);
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    // ────────────────────────────────────────────────────────────────
    // 공용 그리기 도구
    // ────────────────────────────────────────────────────────────────
    private static Component rowNames(List<RecipeFocus.Row> list, boolean withCount, boolean wirelessCount) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            RecipeFocus.Row row = list.get(i);
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(icon(row.item()).getHoverName());
            if (wirelessCount) {
                out.append(Component.literal(" x" + compact(row.wireless())));
            } else if (withCount) {
                out.append(Component.literal(" x" + row.need().count()));
            } else if (row.inv() > 0) {
                out.append(Component.literal(" (" + row.inv() + "/" + row.need().count() + ")"));
            }
        }
        return out;
    }

    /**
     * 보관함 이름. 바라보는 블록이면 "집어 올리기(pick block)" 아이템 이름을 쓴다
     * (AE2 케이블 버스는 바라보는 부품(예: ME 패턴 인코딩 터미널) 이름이 나온다).
     * 그 외에는 블록의 아이템 이름, 아이템이 없는 블록(배낭 등)은 블록 이름을 쓴다.
     */
    private record NamedPos(net.minecraft.resources.ResourceLocation block, Component name) {}

    /** 바라봤을 때 알아낸 이름(케이블 버스의 부품 등). 시선을 떼도 같은 이름을 쓰기 위해 기억한다. */
    private static final Map<Long, NamedPos> NAMES = new java.util.HashMap<>();

    static void clearNames() {
        NAMES.clear();
    }

    static Component blockName(Minecraft mc, net.minecraft.resources.ResourceLocation id, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(id);
        try {
            if (mc.level != null && mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(pos)) {
                ItemStack picked = mc.level.getBlockState(pos).getCloneItemStack(hit, mc.level, pos, mc.player);
                if (!picked.isEmpty()) {
                    Component name = picked.getHoverName();
                    NAMES.put(pos.asLong(), new NamedPos(id, name));
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // 일부 블록은 집어 올리기 계산이 실패할 수 있다: 아래 기본 이름을 쓴다
        }
        NamedPos known = NAMES.get(pos.asLong());
        if (known != null && known.block().equals(id)) {
            return known.name();
        }
        // 시선을 뗀 뒤에도 같은 이름이 나오도록, 마지막으로 바라봤을 때의 이름을 기억해 쓴다 (블록이 바뀌었으면 무시)
        Object[] remembered = PICKED.get(pos);
        if (remembered != null && id.equals(remembered[0])) {
            return (Component) remembered[1];
        }
        Item item = block.asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(item).getHoverName();
        }
        return block.getName();
    }

    private static final Map<BlockPos, Object[]> PICKED = new java.util.HashMap<>();

    /** 단말기를 여는 순간에도 이름을 기억해 둔다 (바라보고 있을 때만 알 수 있으므로) */
    static void rememberName(Minecraft mc, BlockPos pos) {
        if (mc.level != null) {
            blockName(mc, BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()), pos);
        }
    }

    static ItemStack icon(Item item) {
        return ICONS.computeIfAbsent(item, i -> new ItemStack(i));
    }

    static void drawIcon(PoseStack ps, Vec3 camPos, Vec3 p, ItemStack stack, float scale, float spinDeg,
                                 MultiBufferSource buffers, Minecraft mc) {
        ps.pushPose();
        ps.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        ps.scale(scale, scale, scale);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
        ps.popPose();
    }

    /**
     * 글씨(개수, 이름, 안내)는 3D 글꼴 대신 "화면 위 글씨(HUD)"로 그립니다.
     * 3D 글꼴은 셰이더/렌더 순서/다른 모드에 따라 아이템에 묻히거나 안 보일 수 있어서,
     * 3D 좌표를 화면 좌표로 투영해 GUI 글씨로 그리면 항상 선명하게 보입니다.
     */
    private record Label(float x, float y, float scale, Component text, int color, int priority, boolean fixed, boolean count) {}

    private static final List<Label> LABELS = new ArrayList<>();

    private static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch();
    }

    private static void drawText(PoseStack ps, Camera camera, Vec3 camPos, Vec3 p, Component text, float scale,
                                 int color, boolean seeThrough, MultiBufferSource buffers, Font font) {
        drawLabel(ps, camera, camPos, p, text, scale, color, 0.0, 2);
    }

    /**
     * 3D 좌표를 화면에 투영해 GUI 글씨로 쌓아 둔다.
     * maxWorldWidth(월드 길이)가 0보다 크면 그 폭 안에 들어가도록 글씨를 줄이고, 그래도 너무 작으면(읽을 수 없으면) 그리지 않는다.
     * priority가 2 이상이면 다른 글씨와 겹쳐도 항상 그린다. 그 아래는 이미 그려진 글씨와 겹치면 건너뛴다.
     */
    static void drawLabel(PoseStack ps, Camera camera, Vec3 camPos, Vec3 p, Component text, float scale,
                                  int color, double maxWorldWidth, int priority) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f vp = viewProj(ps, camera);
        org.joml.Vector4f c = new org.joml.Vector4f((float) (p.x - camPos.x), (float) (p.y - camPos.y), (float) (p.z - camPos.z), 1f);
        vp.transform(c);
        if (c.w <= 0.05f || c.w > 48f) {
            return; // 카메라 뒤쪽이거나 너무 멂
        }
        float nx = c.x / c.w;
        float ny = c.y / c.w;
        if (Math.abs(nx) > 1.4f || Math.abs(ny) > 1.4f) {
            return;
        }
        // 글자 한 줄의 월드 높이(8px * scale)가 화면에서 몇 픽셀인지로 글씨 크기를 정한다
        org.joml.Vector3f up = new org.joml.Vector3f(camera.getUpVector()).mul(8f * scale);
        org.joml.Vector4f q = new org.joml.Vector4f((float) (p.x - camPos.x) + up.x, (float) (p.y - camPos.y) + up.y,
                (float) (p.z - camPos.z) + up.z, 1f);
        vp.transform(q);
        float ny2 = q.w > 0.05f ? q.y / q.w : ny;
        float guiW = mc.getWindow().getGuiScaledWidth();
        float guiH = mc.getWindow().getGuiScaledHeight();
        float pixels = Math.abs(ny2 - ny) * 0.5f * guiH;
        float fs = Math.max(0.6f, Math.min(2.4f, pixels / 8f));
        if (maxWorldWidth > 0) {
            float pxPerWorld = pixels / (8f * scale);
            float maxPx = (float) (maxWorldWidth * pxPerWorld);
            int w = Math.max(1, mc.font.width(text));
            if (w * fs > maxPx) {
                fs = maxPx / w;
            }
            if (fs < 0.6f) {
                return; // 칸에 안 들어가면 지저분하게 겹치느니 그리지 않는다
            }
        }
        LABELS.add(new Label((nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH - 4f * fs, fs, text, color, priority, false, false));
    }


    /**
     * 월드 좌표(카메라 기준 상대값)를 화면으로 보내는 행렬.
     * 레벨 렌더 이벤트의 PoseStack에는 카메라 회전이 들어 있지 않은 경우가 있어서(회전은 RenderSystem 쪽 모델뷰에 있음),
     * 포즈 행렬이 회전 없는 단위 행렬이면 카메라 회전을 직접 곱해 준다. 이미 회전이 들어 있으면 그대로 쓴다.
     */
    private static Matrix4f eventProjection;

    /** 레벨 렌더 이벤트가 알려 주는 3D 투영 행렬을 저장한다. (이 시점의 RenderSystem 행렬은 GUI용일 수 있어서 이벤트 값을 우선 쓴다) */
    static void setProjection(Matrix4f m) {
        eventProjection = m == null ? null : new Matrix4f(m);
    }

    static Matrix4f projection() {
        return eventProjection != null ? eventProjection : RenderSystem.getProjectionMatrix();
    }

    private static Matrix4f viewProj(PoseStack ps, Camera camera) {
        Matrix4f pose = ps.last().pose();
        Matrix4f vp = new Matrix4f(projection());
        boolean noRotation = Math.abs(pose.m00() - 1f) < 1.0e-3f && Math.abs(pose.m11() - 1f) < 1.0e-3f
                && Math.abs(pose.m22() - 1f) < 1.0e-3f && Math.abs(pose.m01()) < 1.0e-3f && Math.abs(pose.m10()) < 1.0e-3f;
        if (noRotation) {
            vp.mul(new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf())));
        }
        return vp.mul(pose);
    }

    /**
     * Jade처럼 아이콘의 오른쪽 아래 모서리에 인벤토리 숫자 모양으로 개수를 그린다. 배경 상자는 없다.
     * iconSize는 FIXED 표시에서 아이콘의 월드 크기(= 렌더 배율)이다.
     */
    static void drawCount(PoseStack ps, Camera camera, Vec3 camPos, Vec3 p, float iconSize, String text, int color, int priority) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f vp = viewProj(ps, camera);
        org.joml.Vector3f r = new org.joml.Vector3f(camera.getLeftVector()).mul(-0.5f * iconSize);
        org.joml.Vector3f u = new org.joml.Vector3f(camera.getUpVector()).mul(-0.5f * iconSize);
        org.joml.Vector4f corner = new org.joml.Vector4f((float) (p.x - camPos.x) + r.x + u.x, (float) (p.y - camPos.y) + r.y + u.y,
                (float) (p.z - camPos.z) + r.z + u.z, 1f);
        vp.transform(corner);
        if (corner.w <= 0.05f || corner.w > 48f) {
            return;
        }
        float nx = corner.x / corner.w;
        float ny = corner.y / corner.w;
        if (Math.abs(nx) > 1.4f || Math.abs(ny) > 1.4f) {
            return;
        }
        org.joml.Vector3f full = new org.joml.Vector3f(camera.getUpVector()).mul(iconSize);
        org.joml.Vector4f top = new org.joml.Vector4f((float) (p.x - camPos.x) + full.x, (float) (p.y - camPos.y) + full.y,
                (float) (p.z - camPos.z) + full.z, 1f);
        org.joml.Vector4f bottom = new org.joml.Vector4f((float) (p.x - camPos.x), (float) (p.y - camPos.y), (float) (p.z - camPos.z), 1f);
        vp.transform(top);
        vp.transform(bottom);
        float guiW = mc.getWindow().getGuiScaledWidth();
        float guiH = mc.getWindow().getGuiScaledHeight();
        float iconPx = top.w > 0.05f && bottom.w > 0.05f ? Math.abs(top.y / top.w - bottom.y / bottom.w) * 0.5f * guiH : 16f;
        float fs = Math.max(0.6f, Math.min(2.0f, iconPx / 16f)) * InsightConfig.COUNT_SCALE.get().floatValue();
        if (iconPx < 7f && priority < 2) {
            return;
        }
        float x = (nx * 0.5f + 0.5f) * guiW;
        float y = (0.5f - ny * 0.5f) * guiH - 8f * fs + 1f * fs;
        LABELS.add(new Label(x, y, fs, Component.literal(text), color, priority, false, true));
    }

    /** 화면 고정 위치(십자선 아래 등)에 글씨를 하나 올린다. 항상 그린다. */
    private static void drawFixed(Component text, int color, float yOffsetFromCenter) {
        Minecraft mc = Minecraft.getInstance();
        float cx = mc.getWindow().getGuiScaledWidth() / 2f;
        float cy = mc.getWindow().getGuiScaledHeight() / 2f + yOffsetFromCenter;
        LABELS.add(new Label(cx, cy, 1.0f, text, color, 9, true, false));
    }

    /** RenderGuiEvent.Post에서 호출: 이번 프레임에 모인 글씨를 그린다. */
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc) {
        if (LABELS.isEmpty()) {
            return;
        }
        // 중요한 것부터 그리고, 중요도가 낮은 글씨는 이미 그린 글씨와 겹치면 건너뛴다
        List<Label> order = new ArrayList<>(LABELS);
        order.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        List<float[]> placed = new ArrayList<>();
        for (Label l : order) {
            int w = mc.font.width(l.text());
            float hw = w * l.scale() / 2f + 2f;
            float top = l.y() - 1f;
            float bottom = l.y() + 9f * l.scale() + 1f;
            float left = l.count() ? l.x() - w * l.scale() - 1f : l.x() - hw;
            float right = l.count() ? l.x() + 1f : l.x() + hw;
            if (l.priority() < 2) {
                boolean clash = false;
                for (float[] r : placed) {
                    if (left < r[2] && right > r[0] && top < r[3] && bottom > r[1]) {
                        clash = true;
                        break;
                    }
                }
                if (clash) {
                    continue;
                }
            }
            placed.add(new float[]{left, top, right, bottom});

            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(l.x(), l.y(), 200f);
            ps.scale(l.scale(), l.scale(), 1f);
            if (l.count()) {
                g.drawString(mc.font, l.text(), -w, 0, l.color(), true);
            } else {
                g.fill(-w / 2 - 2, -1, w / 2 + 2, 9, 0x48000000);
                g.drawString(mc.font, l.text(), -w / 2, 0, l.color(), true);
            }
            ps.popPose();
        }
        LABELS.clear();
    }

    /** 이번 프레임 시작 시 호출: 지난 프레임의 글씨를 비운다. */
    static void beginFrame() {
        LABELS.clear();
    }

    private static Component ageText(long ticks) {
        long sec = ticks / 20;
        Component unit;
        if (sec < 60) {
            unit = Component.translatable("insight_addons.age.sec", sec);
        } else if (sec < 3600) {
            unit = Component.translatable("insight_addons.age.min", sec / 60);
        } else {
            unit = Component.translatable("insight_addons.age.hour", sec / 3600);
        }
        return Component.translatable("insight_addons.remembered", unit);
    }

    private static String compact(int n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000f);
        }
        if (n >= 1_000) {
            return String.format(Locale.ROOT, "%.1fK", n / 1_000f);
        }
        return Integer.toString(n);
    }
}
