package dev.thaumaturge.nodenavigator;

import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import dev.thaumaturge.nodenavigator.scan.NodeValidationService;
import dev.thaumaturge.nodenavigator.scan.KnownNodeScan;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ThaumaturgeNodeNavigator.MOD_ID)
public final class ThaumaturgeNodeNavigator {
    public static final String MOD_ID = "thaumaturge_node_navigator";
    private static volatile Consumer<NodeScannedPayload> scannedHandler = payload -> missingClientHandler();
    private static volatile Consumer<TrackerProfilePayload> profileHandler = payload -> missingClientHandler();
    private static volatile Consumer<NodeValidationPayload> validationHandler = payload -> missingClientHandler();

    public ThaumaturgeNodeNavigator(IEventBus modBus) {
        modBus.addListener(ThaumaturgeNodeNavigator::registerPayloads);
        ScanningManager.register(new KnownNodeScan());
    }

    // NeoForge1.21.1 registers both directions on the common event. The client setup
    // installs callbacks without loading Minecraft client classes on a dedicated server.
    public static void installClientHandlers(Consumer<NodeScannedPayload> scanned,
                                             Consumer<TrackerProfilePayload> profile,
                                             Consumer<NodeValidationPayload> validation) {
        scannedHandler = scanned;
        profileHandler = profile;
        validationHandler = validation;
    }

    private static void missingClientHandler() {
        throw new IllegalStateException("Navigator client payload handlers have not been initialized");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(NodeScannedPayload.TYPE, NodeScannedPayload.STREAM_CODEC,
                (payload, context) -> scannedHandler.accept(payload));
        registrar.playToClient(TrackerProfilePayload.TYPE, TrackerProfilePayload.STREAM_CODEC,
                (payload, context) -> profileHandler.accept(payload));
        registrar.playBidirectional(NodeValidationPayload.TYPE, NodeValidationPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.flow().isClientbound()) validationHandler.accept(payload);
                    else NodeValidationService.handle(payload, context);
                });
    }
}
