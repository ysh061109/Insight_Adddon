package dev.insightaddons.extras.net;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 클라이언트 -> 서버. 내용물을 알고 싶은 보관함 좌표 목록.
 *
 * @param mode      0 = 바라보는 보관함(전체 내용물), 1 = 렌치 스캔(namespace 필터 적용), 2 = 조합 재료 찾기(items 필터 적용)
 * @param namespace mode 1에서 남길 아이템 네임스페이스(예: "create"). 빈 문자열이면 필터 없음
 * @param items     mode 2에서 찾을 아이템 ID 목록. 다른 모드에서는 비어 있음
 */
public record ScanRequest(int mode, String namespace, List<BlockPos> positions, List<ResourceLocation> items)
        implements CustomPacketPayload {
    public static final Type<ScanRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "scan_request"));

    public static final StreamCodec<FriendlyByteBuf, ScanRequest> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mode);
                buf.writeUtf(p.namespace, 64);
                buf.writeCollection(p.positions, (b, pos) -> b.writeBlockPos(pos));
                buf.writeCollection(p.items, (b, id) -> b.writeResourceLocation(id));
            },
            buf -> new ScanRequest(
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readList(b -> b.readBlockPos()),
                    buf.readList(b -> b.readResourceLocation())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
