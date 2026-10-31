package dev.insightaddons.net;

import dev.insightaddons.client.ClientCache;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): 서버에 이 모드가 없어도 클라이언트가 접속할 수 있습니다. (서버 스캔 기능만 꺼짐)
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ScanRequest.TYPE, ScanRequest.CODEC, ServerScan::handle);
        registrar.playToServer(TakeRequest.TYPE, TakeRequest.CODEC, ServerTake::handle);
        registrar.playToClient(CraftFxPacket.TYPE, CraftFxPacket.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.insightaddons.client.CraftFx.addRemote(payload)));
        // 람다 안에서만 ClientCache를 참조하므로 전용 서버에서는 클래스가 로드되지 않습니다.
        registrar.playToClient(ScanResult.TYPE, ScanResult.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientCache.accept(payload)));
    }
}
