package dev.insightaddons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrsun.insightchests.physics.ChestBoundingBox;
import net.minecraft.client.renderer.MultiBufferSource;

/** 믹스인에서 패키지 전용 BackpackInterior를 부르기 위한 공개 통로 */
public final class BackpackInteriorAccess {
    private BackpackInteriorAccess() {}

    public static boolean render(PoseStack ps, MultiBufferSource buffers, ChestBoundingBox box) {
        return BackpackInterior.render(ps, buffers, box);
    }
}
