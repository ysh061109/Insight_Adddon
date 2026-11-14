package dev.insightaddons.extras.net;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** 서버 -> 클라이언트. 꺼내기 결과: 실제로 인벤토리에 들어간 개수. (날아오는 연출용) */
public record TakeResult(BlockPos pos, Item item, int moved) implements CustomPacketPayload {
    public static final Type<TakeResult> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "take_result"));

    public static final StreamCodec<FriendlyByteBuf, TakeResult> CODEC = StreamCodec.of(
            (buf, r) -> {
                buf.writeBlockPos(r.pos);
                buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(r.item));
                buf.writeVarInt(r.moved);
            },
            buf -> new TakeResult(buf.readBlockPos(), BuiltInRegistries.ITEM.get(buf.readResourceLocation()), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
