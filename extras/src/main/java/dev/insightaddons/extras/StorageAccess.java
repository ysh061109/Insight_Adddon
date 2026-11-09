package dev.insightaddons.extras;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 블록의 IItemHandler를 찾는 공용 헬퍼.
 * Create 금고/슈트/작업대, Sophisticated Backpacks 블록, 바닐라 상자 모두 NeoForge 표준 capability를 제공하므로
 * 각 모드에 컴파일 의존성 없이 동일하게 읽을 수 있습니다.
 */
public final class StorageAccess {
    private StorageAccess() {}

    @Nullable
    public static IItemHandler find(Level level, BlockPos pos) {
        // AE2의 ME 상자/드라이브/인터페이스는 일반 IItemHandler로는 내용물이 안 보이므로 ME 저장소를 따로 읽는다
        if (ModList.get().isLoaded("ae2")
                && AeBridge.isAeBlock(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))) {
            IItemHandler ae = AeBridge.handler(level, pos);
            if (ae != null) {
                return ae;
            }
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}
