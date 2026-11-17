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
        double h = 0.375 * CABLE_FLAT + 0.004;
        double zc = 3.0;
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
                    one(ps, buffers, mc, controller, ModelData.EMPTY, -1 + i, AeRoom.Y0, -(3 + j), 0.12f);
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
