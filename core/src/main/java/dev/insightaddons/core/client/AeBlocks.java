package dev.insightaddons.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;

/**
 * 서버룸에 놓는 AE2 블록 모델: 바닥 중앙은 전원이 들어온 ME 제어기, 양쪽 벽은 셀이 든 ME 드라이브.
 * 월드의 실제 블록이 아니라 블록 모델만 빌려서 그립니다. AE2가 없거나 실패하면 available()이 false입니다.
 */
final class AeBlocks {
    private static boolean tried;
    private static BlockState controller;
    private static BlockState driveLeft;
    private static BlockState driveRight;
    private static ModelData driveData = ModelData.EMPTY;
    private static boolean failed;
    private static BlockState cableBus;
    private static boolean busOk;

    private AeBlocks() {}

    /** 어떤 이름의 속성이든 값 이름이 value인 것을 찾아 설정한다 (AE2 제어기의 상태 속성 이름은 "state") */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withValue(BlockState s, String value) {
        for (Property p : s.getProperties()) {
            for (Object v : p.getPossibleValues()) {
                if (p.getName((Comparable) v).equals(value)) {
                    return s.setValue(p, (Comparable) v);
                }
            }
        }
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState s, String prop, String value) {
        for (Property p : s.getProperties()) {
            if (p.getName().equals(prop)) {
                for (Object v : p.getPossibleValues()) {
                    if (p.getName((Comparable) v).equals(value)) {
                        return s.setValue(p, (Comparable) v);
                    }
                }
            }
        }
        return s;
    }

    private static Block block(String path) {
        Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("ae2", path));
        return b.orElse(null);
    }

    private static void init() {
        tried = true;
        try {
            Block c = block("controller");
            Block d = block("drive");
            if (c == null || d == null) {
                failed = true;
                return;
            }
            controller = withValue(c.defaultBlockState(), "online");
            driveLeft = with(d.defaultBlockState(), "facing", Direction.EAST.getSerializedName());
            driveRight = with(d.defaultBlockState(), "facing", Direction.WEST.getSerializedName());
            driveData = cells();
            Block cb = block("cable_bus");
            if (cb != null) {
                cableBus = cb.defaultBlockState();
                busOk = busData(java.util.Map.of(Direction.UP, false)) != null;
            }
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.core.InsightAddons.LOGGER.warn("AE2 가상 상자 준비 실패: {}", t.toString());
        }
    }

    /** 드라이브에 셀을 채운 모델 데이터. AE2 내부 클래스를 리플렉션으로 시도하고 안 되면 빈 값 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ModelData cells() {
        try {
            Item[] items = new Item[10];
            String[] ids = {"item_storage_cell_1k", "item_storage_cell_4k", "item_storage_cell_16k", "item_storage_cell_64k", "item_storage_cell_256k"};
            for (int i = 0; i < items.length; i++) {
                items[i] = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", ids[i % ids.length]));
            }
            // AE2 소스(19.2.x): appeng.client.render.model.DriveModelData.create(Item[]) -> ModelData
            Class<?> cls = Class.forName("appeng.client.render.model.DriveModelData");
            Object data = cls.getMethod("create", Item[].class).invoke(null, (Object) items);
            return data instanceof ModelData md ? md : ModelData.EMPTY;
        } catch (Throwable t) {
            dev.insightaddons.core.InsightAddons.LOGGER.info("AE2 드라이브 셀 모델 데이터를 만들지 못해 빈 드라이브로 그립니다: {}", t.toString());
            return ModelData.EMPTY;
        }
    }

    /** 바닥 홈에 진짜 AE2 케이블 블록을 쓸 수 있는가(못 쓰면 기존 납작 케이블로 그린다) */
    static boolean busReady() {
        if (!tried) {
            init();
        }
        return !failed && busOk;
    }

    private static final java.util.Map<Integer, ModelData> BUS_CACHE = new java.util.HashMap<>();

    /**
     * AE2 케이블 블록(밀집 스마트, 플루익스)의 모델 데이터. conns: 방향 -> true(옆 케이블과 이어짐) / false(드라이브·제어기 같은 장치와 이어짐).
     * AE2 소스(1.21.1): appeng.client.render.cablebus.CableBusRenderState (PROPERTY, setCableType/CoreType/Color/ConnectionTypes/CableBusAdjacent/ChannelsOnSide).
     * 실패하면 null.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ModelData busData(java.util.Map<Direction, Boolean> conns) {
        int key = 0;
        for (var e : conns.entrySet()) {
            key |= (1 << e.getKey().ordinal()) | (e.getValue() ? 1 << (8 + e.getKey().ordinal()) : 0);
        }
        ModelData cached = BUS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> rsC = Class.forName("appeng.client.render.cablebus.CableBusRenderState");
            Class<?> ctC = Class.forName("appeng.api.util.AECableType");
            Class<?> colC = Class.forName("appeng.api.util.AEColor");
            Class<?> coreC = Class.forName("appeng.client.render.cablebus.CableCoreType");
            Object dense = Enum.valueOf((Class) ctC, "DENSE_SMART");
            Object smart = Enum.valueOf((Class) ctC, "SMART");
            Object clear = Enum.valueOf((Class) colC, "TRANSPARENT");
            Object core = coreC.getMethod("fromCableType", ctC).invoke(null, dense);
            Object rs = rsC.getConstructor().newInstance();
            rsC.getMethod("setCableType", ctC).invoke(rs, dense);
            rsC.getMethod("setCoreType", coreC).invoke(rs, core);
            rsC.getMethod("setCableColor", colC).invoke(rs, clear);
            java.util.EnumMap<Direction, Object> types = new java.util.EnumMap<>(Direction.class);
            java.util.EnumSet<Direction> adj = java.util.EnumSet.noneOf(Direction.class);
            java.util.EnumMap<Direction, Integer> ch = new java.util.EnumMap<>(Direction.class);
            for (var e : conns.entrySet()) {
                types.put(e.getKey(), e.getValue() ? dense : smart);
                if (e.getValue()) {
                    adj.add(e.getKey());
                }
                ch.put(e.getKey(), 8);
            }
            rsC.getMethod("setConnectionTypes", java.util.EnumMap.class).invoke(rs, types);
            rsC.getMethod("setCableBusAdjacent", java.util.EnumSet.class).invoke(rs, adj);
            rsC.getMethod("setChannelsOnSide", java.util.EnumMap.class).invoke(rs, ch);
            ModelProperty prop = (ModelProperty) rsC.getField("PROPERTY").get(null);
            ModelData data = ModelData.builder().with(prop, rs).build();
            BUS_CACHE.put(key, data);
            return data;
        } catch (Throwable t) {
            dev.insightaddons.core.InsightAddons.LOGGER.info("AE2 케이블 블록 모델 데이터를 만들지 못해 납작 케이블로 그립니다: {}", t.toString());
            return null;
        }
    }

    /** 바닥 홈 안의 케이블 한 칸. xc/zc: 방 좌표 중심, conns: 이어진 방향(방 +z는 블록 NORTH) */
    private static void bus(PoseStack ps, MultiBufferSource buffers, Minecraft mc, double xc, double zc,
                            java.util.Map<Direction, Boolean> conns) {
        ModelData d = busData(conns);
        if (d == null) {
            return;
        }
        one(ps, buffers, mc, cableBus, d, xc - 0.5, AeRoom.Y0 - AeRoom.TRENCH_D, -zc - 0.5, 1f);
    }

    /** 홈 안 케이블망: 기존 십자 구조 그대로(중앙 세로줄, 제어기 양옆 가로줄, 앞쪽 가로줄)를 홈으로 옮기고, 양옆 드라이브 밑까지 이어서 드라이브와 연결한다 */
    private static void busCables(PoseStack ps, MultiBufferSource buffers, Minecraft mc) {
        final boolean B = true;
        final boolean H = false;
        // 중앙 세로줄(제어기 남쪽 면까지)
        for (int i = 0; i < 7; i++) {
            double z = AeRoom.Z0 + i + 0.5;
            java.util.Map<Direction, Boolean> c = new java.util.EnumMap<>(Direction.class);
            if (i > 0) {
                c.put(Direction.SOUTH, B);
            }
            c.put(Direction.NORTH, i < 6 ? B : H);
            bus(ps, buffers, mc, 0, z, c);
        }
        // 제어기 양옆 가로줄 (바깥 끝은 드라이브 밑에서 드라이브와 연결)
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            Direction in = sgn < 0 ? Direction.EAST : Direction.WEST;
            Direction out = sgn < 0 ? Direction.WEST : Direction.EAST;
            for (int k = 0; k < 3; k++) {
                double x = sgn * (1.5 + k);
                java.util.Map<Direction, Boolean> c = new java.util.EnumMap<>(Direction.class);
                c.put(in, k > 0 ? B : H); // 제일 안쪽은 제어기와 연결
                if (k < 2) {
                    c.put(out, B);
                } else {
                    c.put(Direction.UP, H); // 드라이브 바로 밑: 드라이브와 연결된 케이블
                }
                bus(ps, buffers, mc, x, 3.0, c);
            }
        }
        // 앞쪽 가로줄 (가운데 칸은 제어기 북쪽 면과 연결)
        for (int i = -2; i <= 2; i++) {
            java.util.Map<Direction, Boolean> c = new java.util.EnumMap<>(Direction.class);
            if (i > -2) {
                c.put(Direction.WEST, B);
            }
            if (i < 2) {
                c.put(Direction.EAST, B);
            }
            if (i == 0) {
                c.put(Direction.SOUTH, H);
                c.put(Direction.UP, B); // 가운데 칸에서 천장까지 세로 케이블이 올라간다
            }
            bus(ps, buffers, mc, i, 4.5, c);
        }
        // 가운데 세로 케이블: 홈 안 케이블에서 바닥 위로 천장까지 이어진다 (마지막 칸은 남은 높이만큼)
        double height = AeRoom.Y1 - AeRoom.Y0;
        int cells = (int) Math.ceil(height - 0.01);
        for (int k = 0; k < cells; k++) {
            float sy = (float) Math.min(1.0, height - k);
            java.util.Map<Direction, Boolean> c = new java.util.EnumMap<>(Direction.class);
            c.put(Direction.DOWN, B);
            c.put(Direction.UP, k < cells - 1 ? B : H);
            ModelData d = busData(c);
            if (d != null) {
                one(ps, buffers, mc, cableBus, d, -0.5, AeRoom.Y0 + k, -4.5 - 0.5, sy);
            }
        }
    }

    /** 페이드: 현재 열림 정도(0..1). 블록을 자기 중심 기준으로 이 비율만큼 줄여서 서서히 사라지게/나타나게 한다 */
    private static float fade = 1f;

    private static void one(PoseStack ps, MultiBufferSource buffers, Minecraft mc, BlockState s, ModelData data,
                            double x, double y, double z, float sy) {
        ps.pushPose();
        ps.translate(x + 0.5, y + 0.5 * sy, z + 0.5);
        ps.scale(fade, fade, fade);
        ps.translate(-0.5, -0.5 * sy, -0.5);
        ps.scale(1f, sy, 1f);
        mc.getBlockRenderer().renderSingleBlock(s, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, data, null);
        ps.popPose();
    }

    private static ItemStack cableStack;
    /** 케이블을 납작하게 눌러 바닥에 붙인다(아이템 모델 반두께 0.375 * 납작 비율 = 바닥 위 높이). */
    private static final float CABLE_FLAT = 0.38f;

    /** 액정(플루익스) ME 조밀 스마트 케이블 한 조각. 아이템 모델(길이 12/16)을 블록 1칸 길이로 늘려 이어 붙인다. axis: 0=x,1=y,2=z(방 기준) */
    private static void cable(PoseStack ps, MultiBufferSource buffers, Minecraft mc, double cx, double cy, double cz, int axis) {
        cable(ps, buffers, mc, cx, cy, cz, axis, 1.0);
    }

    /** len: 블록 1칸 길이에 대한 비율(천장에 닿는 마지막 조각을 짧게 자를 때 쓴다) */
    private static void cable(PoseStack ps, MultiBufferSource buffers, Minecraft mc, double cx, double cy, double cz, int axis, double len) {
        ps.pushPose();
        ps.translate(cx, cy, -cz);
        ps.scale(fade, fade, fade);
        if (axis == 0) {
            ps.mulPose(Axis.YP.rotationDegrees(90));
        } else if (axis == 1) {
            ps.mulPose(Axis.XP.rotationDegrees(90));
        }
        ps.scale(1f, CABLE_FLAT, (float) (16f / 12f * len));
        mc.getItemRenderer().renderStatic(cableStack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
        ps.popPose();
    }

    private static void cables(PoseStack ps, MultiBufferSource buffers, Minecraft mc) {
        if (cableStack == null) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", "fluix_smart_dense_cable"));
            if (it == null || it == net.minecraft.world.item.Items.AIR) {
                return;
            }
            cableStack = new ItemStack(it);
        }
        boolean bus = busReady();
        double h = 0.375 * CABLE_FLAT + 0.004;
        double zc = 3.0;
        if (bus) {
            busCables(ps, buffers, mc);
        } else {
            // 전부 바닥에 깐다: 바닥 십자(세로줄은 방 중앙, 가로줄은 앞쪽 z=3, 드라이브 벽 사이만) + 앞쪽 벽 아래 가로줄
            // 바닥 케이블은 한가운데 ME 제어기(x -1..1, z 2..4)를 향해 모인다: 앞뒤로 중앙선, 좌우로 가로줄
            for (int i = 0; i < 10; i++) {
                double z = AeRoom.Z0 + i + 0.5;
                if (z > 2.0 && z < 4.0) {
                    continue; // 제어기 자리
                }
                cable(ps, buffers, mc, 0, AeRoom.Y0 + h, z, 2);
            }
            // 중앙에서 양쪽 드라이브 쪽으로 뻗는 가로줄은 드라이브 밑을 따라가는 케이블에 이어진다
            for (double x : new double[]{-2.2, -1.5, 1.5, 2.2}) {
                cable(ps, buffers, mc, x, AeRoom.Y0 + h, zc, 0);
            }
            // 드라이브 바닥: 드라이브 앞면에 붙여 벽 끝까지 이어지는 케이블 (드라이브와 연결된 모양, 블록과 겹치지 않는다)
            for (int i = 0; i < 10; i++) {
                double z = AeRoom.Z0 + i + 0.5;
                cable(ps, buffers, mc, -2.6, AeRoom.Y0 + h, z, 2);
                cable(ps, buffers, mc, 2.6, AeRoom.Y0 + h, z, 2);
            }
            for (int i = 0; i < 5; i++) {
                cable(ps, buffers, mc, -2.0 + i, AeRoom.Y0 + h, AeRoom.Z1 - 0.4, 0);
            }
        }
        if (bus) {
            return; // 가운데 세로 케이블도 busCables가 이어서 그린다
        }
        // 앞쪽 벽 가운데 세로선: 벽에 붙여 세로로 케이블을 세운다
        // 바닥에서 천장까지 올라가서 천장에서 끝난다 (마지막 조각은 남은 높이만큼만)
        double height = AeRoom.Y1 - AeRoom.Y0;
        for (int k = 0; k * 1.0 < height - 0.01; k++) {
            double len = Math.min(1.0, height - k);
            cable(ps, buffers, mc, 0, AeRoom.Y0 + k + len / 2, AeRoom.Z1 - 0.2, 1, len);
        }
    }

    static boolean available() {
        if (!tried) {
            init();
        }
        return !failed;
    }

    /**
     * 방 좌표(x=오른쪽, y=위, z=앞)의 정수 칸에 맞춰 블록을 놓는다. 카메라가 방 원점이다.
     * 블록 모델 좌표계는 오른손 좌표계(z가 뒤쪽)라서 방의 z를 뒤집어 넣는다.
     */
    static boolean render(PoseStack ps, Minecraft mc, MultiBufferSource.BufferSource buffers, AeRoom.Frame f, float open) {
        if (!available()) {
            return false;
        }
        fade = Math.max(0f, Math.min(1f, open));
        if (fade < 0.01f) {
            return true;
        }
        ps.pushPose();
        try {
            double theta = Math.atan2(-f.right().z, f.right().x);
            ps.mulPose(Axis.YP.rotation((float) theta));
            ps.scale((float) f.s(), (float) f.s(), (float) f.s());
            // 바닥 한가운데: ME 제어기 2x2를 납작하게 깔아 발광 패드처럼 (방 좌표 x -1..1, z 2..4). 아이템은 이 위에 쌓인다
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    if (busReady()) {
                        // 케이블과 같은 높이의 구덩이에 제어기를 완전한 블록으로 앉혀서 케이블이 옆면에 곧바로 이어지게 한다
                        one(ps, buffers, mc, controller, ModelData.EMPTY, -1 + i, AeRoom.Y0 - AeRoom.TRENCH_D, -(3 + j), 1f);
                    } else {
                        one(ps, buffers, mc, controller, ModelData.EMPTY, -1 + i, AeRoom.Y0, -(3 + j), 0.12f);
                    }
                }
            }
            // 양쪽 벽: 3단 x 8열 ME 드라이브 (앞면이 방 안쪽을 향함)
            for (int r = 0; r < 3; r++) {
                for (int c = -4; c < 4; c++) {
                    double y = AeRoom.Y0 + r;
                    double z = -(c + 1);
                    one(ps, buffers, mc, driveLeft, driveData, AeRoom.X0, y, z, 1f);
                    one(ps, buffers, mc, driveRight, driveData, AeRoom.X1 - 1, y, z, 1f);
                }
            }
            cables(ps, buffers, mc);
            buffers.endBatch();
            return true;
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.core.InsightAddons.LOGGER.warn("AE2 서버룸 블록 그리기 실패, 색 면 장식으로 대체합니다: {}", t.toString());
            return false;
        } finally {
            ps.popPose();
        }
    }
}
