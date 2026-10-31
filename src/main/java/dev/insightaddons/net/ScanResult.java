package dev.insightaddons.net;

import dev.insightaddons.InsightAddons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/** 서버 -> 클라이언트. 보관함별 내용물 요약. */
public record ScanResult(int mode, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ScanResult> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InsightAddons.MOD_ID, "scan_result"));

    /** 같은 아이템 종류의 합산 개수 */
    public record Stack(Item item, int count) {
        static void write(FriendlyByteBuf buf, Stack s) {
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(s.item));
            buf.writeVarInt(s.count);
        }

        static Stack read(FriendlyByteBuf buf) {
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            return new Stack(item, buf.readVarInt());
        }
    }

    /**
     * 보관함 하나(여러 블록으로 이어진 Create 금고는 하나로 묶음).
     *
     * @param pos    표시 기준 좌표(플레이어에게 가장 가까운 블록)
     * @param total  전체 아이템 개수(상위 목록에 잘려 나간 것 포함)
     * @param kinds  전체 아이템 종류 수
     * @param stacks 개수 내림차순 상위 목록
     */
    public record Entry(BlockPos pos, int total, int kinds, List<Stack> stacks) {
        static void write(FriendlyByteBuf buf, Entry e) {
            buf.writeBlockPos(e.pos);
            buf.writeVarInt(e.total);
            buf.writeVarInt(e.kinds);
            buf.writeCollection(e.stacks, Stack::write);
        }

        static Entry read(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int total = buf.readVarInt();
            int kinds = buf.readVarInt();
            return new Entry(pos, total, kinds, buf.readList(Stack::read));
        }
    }

    public static final StreamCodec<FriendlyByteBuf, ScanResult> CODEC = StreamCodec.of(
            (buf, r) -> {
                buf.writeVarInt(r.mode);
                buf.writeCollection(r.entries, Entry::write);
            },
            buf -> new ScanResult(buf.readVarInt(), buf.readList(Entry::read)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
