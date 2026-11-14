package dev.insightaddons.extras.net;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 클라이언트 -> 서버. 보관함을 열지 않고 3D 표시를 클릭해 아이템을 인벤토리로 가져오기. mode: 0=1개, 1=한 스택, 2=전부. */
public record TakeRequest(BlockPos pos, ResourceLocation item, int mode) implements CustomPacketPayload {
    public static final Type<TakeRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "take_request"));

    public static final StreamCodec<FriendlyByteBuf, TakeRequest> CODEC = StreamCodec.of(
            (buf, r) -> {
                buf.writeBlockPos(r.pos);
                buf.writeResourceLocation(r.item);
                buf.writeByte(r.mode);
            },
            buf -> new TakeRequest(buf.readBlockPos(), buf.readResourceLocation(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
