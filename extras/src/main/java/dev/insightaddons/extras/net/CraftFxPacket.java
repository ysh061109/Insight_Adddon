package dev.insightaddons.extras.net;

import dev.insightaddons.extras.InsightAddons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/** 서버 -> 근처 클라이언트. 누군가 작업대 등에서 조합했을 때 재료가 날아가는 연출을 위한 정보. */
public record CraftFxPacket(BlockPos station, List<Fly> flies) implements CustomPacketPayload {
    public static final Type<CraftFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "craft_fx"));

    /** 재료 하나: 출발 위치(플레이어 손 또는 보관함 중심)와 보관함에서 왔는지 */
    public record Fly(Item item, double x, double y, double z, boolean fromContainer) {
        static void write(FriendlyByteBuf buf, Fly f) {
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(f.item));
            buf.writeDouble(f.x);
            buf.writeDouble(f.y);
            buf.writeDouble(f.z);
            buf.writeBoolean(f.fromContainer);
        }

        static Fly read(FriendlyByteBuf buf) {
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            return new Fly(item, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
        }
    }

    public static final StreamCodec<FriendlyByteBuf, CraftFxPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.station);
                buf.writeCollection(p.flies, Fly::write);
            },
            buf -> new CraftFxPacket(buf.readBlockPos(), buf.readList(Fly::read)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
