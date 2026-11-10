package dev.insightaddons.core.mixin;

import dev.insightaddons.core.client.InsightConfig;
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

    /** 블록 모델의 파티클 아이콘(블록을 대표하는 질감)을 textures/...png 경로로 바꾼다 */
    private static ResourceLocation particleTexture(BlockState state) {
        try {
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance()
                    .getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
            ResourceLocation name = sprite.contents().name();
            if (name.getPath().contains("missingno")) {
                return null;
            }
            return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String tierOf(String path) {
        for (String t : new String[]{"netherite", "diamond", "gold", "iron", "copper"}) {
            if (path.contains(t)) {
                return t;
            }
        }
        return null;
    }

    private static ResourceLocation metalBlock(String tier) {
        return ResourceLocation.withDefaultNamespace("textures/block/" + tier + "_block.png");
    }

    @Inject(method = "fromChest", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void insightaddons$fit(BlockState state, BlockPos pos, CallbackInfoReturnable<Object> cir) {
        if (state != null) {
            ResourceLocation kid = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String kp = kid.getPath();
            String kind = kp.contains("shulker_box") ? "shulker"
                    : kp.contains("barrel") ? "barrel"
                    : kid.getNamespace().equals("sophisticatedbackpacks") ? "backpack" : "other";
            dev.insightaddons.core.client.ContainerKind.set(kind);
        }
        if (failed || state == null || cir.getReturnValue() == null || !InsightConfig.INSIGHT_MODE_ENABLED.get()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String ns = id.getNamespace();
        String path = id.getPath();

        boolean generic = false;
        float minX, maxX, minY, maxY, minZ, maxZ;
        ResourceLocation cavity;
        if (("minecraft".equals(ns) && path.equals("barrel")) || ("mekanism".equals(ns) && path.equals("personal_barrel"))) {
            // 통: 위가 뚫린 나무통. 안쪽 높이를 거의 끝까지 쓴다.
            minX = 0.14f; maxX = 0.86f; minZ = 0.14f; maxZ = 0.86f;
            minY = 0.07f; maxY = 0.93f;
            // Mekanism 개인 통은 열린 뚜껑 안쪽 질감(어두운 강철에 초록 표시등)을 그대로 쓴다
            cavity = "mekanism".equals(ns)
                    ? ResourceLocation.fromNamespaceAndPath("mekanism", "textures/block/personal_barrel/top_open.png")
                    : ResourceLocation.withDefaultNamespace("textures/block/barrel_bottom.png");
        } else if ("mekanism".equals(ns) && path.equals("personal_chest")) {
            // Mekanism 개인 상자: 상자 모양에 어두운 강철 느낌(진회색)의 바닥/벽
            minX = 0.08f; maxX = 0.92f; minZ = 0.08f; maxZ = 0.92f;
            minY = 0.06f; maxY = 0.58f;
            cavity = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/block/personal_steel_panel.png");
        } else if ("minecraft".equals(ns) && path.endsWith("shulker_box")) {
            // 셜커 상자: 몸통(아래 절반) 안쪽만 쓴다. 뚜껑이 위로 열린다.
            minX = 0.1f; maxX = 0.9f; minZ = 0.1f; maxZ = 0.9f;
            minY = 0.06f; maxY = 0.52f;
            // 바닐라 셜커 모델의 안쪽 바닥 질감(색마다 다름)을 그대로 잘라 쓴다
            String color = path.equals("shulker_box") ? "default" : path.substring(0, path.length() - "_shulker_box".length());
            cavity = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/block/shulker_inner/" + color + ".png");
        } else if (ns.equals("sophisticatedstorage") && path.contains("barrel")) {
            // Sophisticated Storage 통(제한 통 포함): 바닐라 통처럼 위가 뚫린 통. 등급 통은 해당 금속 질감, 나무 통은 참나무 통 바닥
            minX = 0.14f; maxX = 0.86f; minZ = 0.14f; maxZ = 0.86f;
            minY = 0.07f; maxY = 0.93f;
            String tier = tierOf(path);
            cavity = tier != null
                    ? ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "textures/block/" + tier + "_barrel_bottom.png")
                    : ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "textures/block/oak_barrel_bottom.png");
        } else if (ns.equals("sophisticatedstorage") && path.contains("shulker_box")) {
            // Sophisticated Storage 셜커 상자: 바닐라 셜커와 같은 몸통 안쪽
            minX = 0.1f; maxX = 0.9f; minZ = 0.1f; maxZ = 0.9f;
            minY = 0.06f; maxY = 0.52f;
            String tier = tierOf(path);
            cavity = tier != null ? metalBlock(tier)
                    : ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/block/shulker_inner/default.png");
        } else if (ns.equals("sophisticatedstorage") && path.contains("chest")) {
            // Sophisticated Storage 상자: 상자 모양 안쪽. 등급 상자는 해당 금속, 나무 상자는 어두운 나무 바닥
            minX = 0.08f; maxX = 0.92f; minZ = 0.08f; maxZ = 0.92f;
            minY = 0.06f; maxY = 0.56f;
            String tier = tierOf(path);
            cavity = tier != null ? metalBlock(tier)
                    : ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/block/sophisticated_chest_inner.png");
        } else if (ns.equals("sophisticatedbackpacks")) {
            // 배낭: 배낭 모델 안쪽 크기에 맞춰 좁고 통통하게. 내부는 InSight Chests 기본 방식으로 그린다.
            minX = 0.2f; maxX = 0.8f; minZ = 0.2f; maxZ = 0.8f;
            minY = 0.06f; maxY = 0.62f;
            cavity = ResourceLocation.fromNamespaceAndPath("insight_addons", "textures/block/backpack_inner.png");
        } else if ("create".equals(ns) && path.equals("item_vault")) {
            // Create 아이템 금고: 속이 꽉 찬 금속 상자. 금고 모델의 안쪽(어두운 금속에 세로 막대) 질감을 쓴다
            minX = 0.12f; maxX = 0.88f; minZ = 0.12f; maxZ = 0.88f;
            minY = 0.06f; maxY = 0.93f;
            cavity = ResourceLocation.fromNamespaceAndPath("create", "textures/block/vault/vault_bottom_small.png");
        } else if ("create".equals(ns) && path.endsWith("_toolbox")) {
            // Create 공구 상자: 나무 상자 안쪽
            minX = 0.14f; maxX = 0.86f; minZ = 0.2f; maxZ = 0.8f;
            minY = 0.06f; maxY = 0.5f;
            cavity = ResourceLocation.withDefaultNamespace("textures/block/spruce_planks.png");
        } else if (!"minecraft".equals(ns)) {
            // 그 밖의 모든 모드 보관함: 블록 모양에서 내부 공간을, 블록 모델의 대표 질감(파티클 아이콘)에서 바닥 질감을 자동으로 정한다
            ResourceLocation tex = particleTexture(state);
            net.minecraft.world.phys.AABB b;
            try {
                b = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
            } catch (Throwable t) {
                return;
            }
            if (tex == null || b.getXsize() < 0.3 || b.getZsize() < 0.3) {
                return;
            }
            generic = true;
            double h = b.getYsize();
            minX = (float) (b.minX + b.getXsize() * 0.1); maxX = (float) (b.maxX - b.getXsize() * 0.1);
            minZ = (float) (b.minZ + b.getZsize() * 0.1); maxZ = (float) (b.maxZ - b.getZsize() * 0.1);
            minY = (float) (b.minY + 0.06);
            maxY = (float) (h >= 0.99 ? b.minY + 0.93 : b.minY + h * 0.62);
            cavity = tex;
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
            if (generic && fIron.getBoolean(old)) {
                return;  // 철 상자류는 InSight가 이미 맞게 그린다
            }
            Object fixed = ctor.newInstance(minX, maxX, minY, maxY, minZ, maxZ,
                    fDouble.getBoolean(old), fFacing.get(old), fType.get(old),
                    fIron.getBoolean(old), cavity, fEnder.getBoolean(old));
            cir.setReturnValue(fixed);
            if (!logged) {
                logged = true;
                dev.insightaddons.core.InsightAddons.LOGGER.info("InSight 내부 크기 적용: {} -> x {}~{}, y {}~{}, 질감 {}", id, minX, maxX, minY, maxY, cavity);
            }
        } catch (Throwable t) {
            failed = true;
            dev.insightaddons.core.InsightAddons.LOGGER.warn("InSight 내부 크기 적용 실패(원래 값을 그대로 씁니다): {}", t.toString());
        }
    }
}
