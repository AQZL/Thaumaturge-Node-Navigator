package dev.thaumaturge.nodenavigator;

import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import dev.thaumaturge.nodenavigator.scan.NodeValidationService;
import dev.thaumaturge.nodenavigator.scan.KnownNodeScan;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ThaumaturgeNodeNavigator.MOD_ID)
public final class ThaumaturgeNodeNavigator {
    public static final String MOD_ID = "thaumaturge_node_navigator";

    public ThaumaturgeNodeNavigator(IEventBus modBus) {
        modBus.addListener(ThaumaturgeNodeNavigator::registerPayloads);
        ScanningManager.register(new KnownNodeScan());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(NodeScannedPayload.TYPE, NodeScannedPayload.STREAM_CODEC);
        registrar.playToClient(TrackerProfilePayload.TYPE, TrackerProfilePayload.STREAM_CODEC);
        registrar.playBidirectional(NodeValidationPayload.TYPE, NodeValidationPayload.STREAM_CODEC,
                NodeValidationService::handle);
    }
}
