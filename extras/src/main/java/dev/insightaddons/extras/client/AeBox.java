package dev.insightaddons.extras.client;

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
 * 물리 연출용 가상 상자: 바닥은 전원이 들어온 ME 제어기, 양쪽 벽은 셀이 든 ME 드라이브 모델.
 * 월드의 실제 블록이 아니라 블록 모델만 빌려서 그립니다. AE2가 없거나 실패하면 false를 돌려줍니다.
 */
final class AeBox {
    private static boolean tried;
    private static BlockState controller;
    private static BlockState driveLeft;
    private static BlockState driveRight;
    private static ModelData driveData = ModelData.EMPTY;
    private static boolean failed;

    private AeBox() {}

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
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("AE2 가상 상자 준비 실패: {}", t.toString());
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
            dev.insightaddons.extras.InsightAddons.LOGGER.info("AE2 드라이브 셀 모델 데이터를 만들지 못해 빈 드라이브로 그립니다: {}", t.toString());
            return ModelData.EMPTY;
        }
    }

    private static void one(PoseStack ps, MultiBufferSource buffers, Minecraft mc, BlockState s, ModelData data, double x, double y, double z) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(0.5f, 0.5f, 0.5f);
        mc.getBlockRenderer().renderSingleBlock(s, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, data, null);
        ps.popPose();
    }

    /**
     * @param floor 상자 바닥 중앙(월드 좌표). right/up/flat은 상자의 가로/세로/앞 방향
     */
    static boolean render(PoseStack ps, Minecraft mc, MultiBufferSource.BufferSource buffers, Vec3 camPos, Vec3 floor, Vec3 right, float tiltDeg) {
        if (!tried) {
            init();
        }
        if (failed) {
            return false;
        }
        ps.pushPose();
        try {
            double theta = Math.atan2(-right.z, right.x);
            ps.translate(floor.x - camPos.x, floor.y - camPos.y, floor.z - camPos.z);
            ps.mulPose(Axis.YP.rotation((float) theta));
            ps.mulPose(Axis.XP.rotationDegrees(tiltDeg));
            // 바닥: 5x2 제어기
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 2; j++) {
                    one(ps, buffers, mc, controller, ModelData.EMPTY, -1.25 + i * 0.5, -0.5, -0.5 + j * 0.5);
                }
            }
            // 양쪽 벽: 3단 x 2열 드라이브 (앞면이 안쪽을 향함)
            for (int k = 0; k < 3; k++) {
                for (int j = 0; j < 2; j++) {
                    one(ps, buffers, mc, driveLeft, driveData, -1.25, k * 0.5, -0.5 + j * 0.5);
                    one(ps, buffers, mc, driveRight, driveData, 0.75, k * 0.5, -0.5 + j * 0.5);
                }
            }
            buffers.endBatch();
            return true;
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.extras.InsightAddons.LOGGER.warn("AE2 가상 상자 그리기 실패, 선 테두리로 대체합니다: {}", t.toString());
            return false;
        } finally {
            ps.popPose();
        }
    }
}
