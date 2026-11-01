package dev.insightaddons.mixin;

import dev.insightaddons.client.InsightConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * InSight Chests의 ChestBoundingBox.fromChest()는 상자 모양(높이 약 0.5)의 내부 공간만 계산합니다.
 * 통, 셜커 상자, 배낭은 모양이 달라서 아이템 물리와 속 빈 내부가 어색하게 나오므로
 * 블록 종류에 맞는 내부 공간과 바닥 질감으로 바꿔 줍니다.
 *
 * 리플렉션을 쓰는 이유: InSight Chests 클래스를 컴파일 시점에 참조하지 않기 위해서입니다.
 * 어떤 단계에서든 실패하면 원래 값을 그대로 둡니다.
 */
@Mixin(targets = "com.mrsun.insightchests.physics.ChestBoundingBox", remap = false)
public abstract class ChestBoundsMixin {
    private static Constructor<?> ctor;
    private static Field fDouble, fEnder, fIron, fFacing, fType, fCavity;
    private static boolean failed;
    private static boolean logged;

    @Inject(method = "fromChest", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$fit(BlockState state, BlockPos pos, CallbackInfoReturnable<Object> cir) {
        if (state != null) {
            ResourceLocation kid = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String kp = kid.getPath();
            String kind = kp.contains("shulker_box") ? "shulker"
                    : kp.endsWith("barrel") || kp.startsWith("barrel") ? "barrel"
                    : kid.getNamespace().equals("sophisticatedbackpacks") ? "backpack" : "other";
            dev.insightaddons.client.ContainerKind.set(kind);
        }
        if (failed || state == null || cir.getReturnValue() == null || !InsightConfig.INSIGHT_MODE_ENABLED.get()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String ns = id.getNamespace();
        String path = id.getPath();

        float minX, maxX, minY, maxY, minZ, maxZ;
        ResourceLocation cavity;
        if ("minecraft".equals(ns) && path.equals("barrel")) {
            // 통: 위가 뚫린 나무통. 안쪽 높이를 거의 끝까지 쓴다.
            minX = 0.14f; maxX = 0.86f; minZ = 0.14f; maxZ = 0.86f;
            minY = 0.07f; maxY = 0.93f;
            cavity = ResourceLocation.withDefaultNamespace("textures/block/barrel_bottom.png");
        } else if ("minecraft".equals(ns) && path.endsWith("shulker_box")) {
            // 셜커 상자: 몸통(아래 절반) 안쪽만 쓴다. 뚜껑이 위로 열린다.
            minX = 0.1f; maxX = 0.9f; minZ = 0.1f; maxZ = 0.9f;
            minY = 0.06f; maxY = 0.52f;
            String color = path.equals("shulker_box") ? "purple" : path.substring(0, path.length() - "_shulker_box".length());
            cavity = ResourceLocation.withDefaultNamespace("textures/block/" + color + "_concrete.png");
        } else if (ns.equals("sophisticatedbackpacks")) {
            // 배낭: 좁고 통통한 모양
            // 배낭 모양에 맞춰 좁고 통통하게 (배낭 모델 안쪽 크기)
            minX = 0.2f; maxX = 0.8f; minZ = 0.2f; maxZ = 0.8f;
            minY = 0.06f; maxY = 0.62f;
            cavity = dev.insightaddons.client.BackpackCavity.get();
        } else {
            return;
        }

        try {
            Object old = cir.getReturnValue();
            if (ctor == null) {
                Class<?> cls = old.getClass();
                fDouble = cls.getField("isDoubleChest");
                fEnder = cls.getField("isEnderChest");
                fIron = cls.getField("isIronChest");
                fFacing = cls.getField("facing");
                fType = cls.getField("chestType");
                fCavity = cls.getField("cavityTexture");
                ctor = cls.getConstructor(float.class, float.class, float.class, float.class, float.class, float.class,
                        boolean.class, Direction.class, fType.getType(), boolean.class, ResourceLocation.class, boolean.class);
            }
            Object fixed = ctor.newInstance(minX, maxX, minY, maxY, minZ, maxZ,
                    fDouble.getBoolean(old), fFacing.get(old), fType.get(old),
                    fIron.getBoolean(old), cavity, fEnder.getBoolean(old));
            cir.setReturnValue(fixed);
            if (!logged) {
                logged = true;
                dev.insightaddons.InsightAddons.LOGGER.info("InSight 내부 크기 적용: {} -> x {}~{}, y {}~{}, 질감 {}", id, minX, maxX, minY, maxY, cavity);
            }
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.InsightAddons.LOGGER.warn("InSight 내부 크기 적용 실패(원래 값을 그대로 씁니다): {}", t.toString());
        }
    }
}
