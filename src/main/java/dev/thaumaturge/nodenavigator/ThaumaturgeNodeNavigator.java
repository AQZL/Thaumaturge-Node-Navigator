package dev.thaumaturge.nodenavigator;

import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import dev.thaumaturge.nodenavigator.scan.NodeValidationService;
import dev.thaumaturge.nodenavigator.scan.KnownNodeScan;
import dev.thaumaturge.nodenavigator.client.NodeTrackerClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;

@Mod(ThaumaturgeNodeNavigator.MOD_ID)
public final class ThaumaturgeNodeNavigator {
    public static final String MOD_ID = "thaumaturge_node_navigator";

    public ThaumaturgeNodeNavigator(IEventBus modBus) {
        modBus.addListener(ThaumaturgeNodeNavigator::registerPayloads);
        KnownNodeScan.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(NodeScannedPayload.TYPE, NodeScannedPayload.STREAM_CODEC,
                (payload, context) -> NodeTrackerClient.accept(payload));
        registrar.playToClient(TrackerProfilePayload.TYPE, TrackerProfilePayload.STREAM_CODEC,
                (payload, context) -> NodeTrackerClient.configure(payload));
        registrar.playBidirectional(NodeValidationPayload.TYPE, NodeValidationPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> NodeTrackerClient.acceptValidation(payload),
                        NodeValidationService::handle));
    }
}
