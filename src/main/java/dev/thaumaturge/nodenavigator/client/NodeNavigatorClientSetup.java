package dev.thaumaturge.nodenavigator.client;

import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;

@EventBusSubscriber(modid = ThaumaturgeNodeNavigator.MOD_ID, value = Dist.CLIENT)
public final class NodeNavigatorClientSetup {
    public static final KeyMapping OPEN_TRACKER = new KeyMapping(
            "key.thaumaturge_node_navigator.open_tracker",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_I,
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(
                    ThaumaturgeNodeNavigator.MOD_ID, "controls")));

    private NodeNavigatorClientSetup() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(OPEN_TRACKER.getCategory());
        event.register(OPEN_TRACKER);
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(TrackerProfilePayload.TYPE, (payload, context) -> NodeTrackerClient.configure(payload));
        event.register(NodeScannedPayload.TYPE, (payload, context) -> NodeTrackerClient.accept(payload));
        event.register(NodeValidationPayload.TYPE, (payload, context) -> NodeTrackerClient.acceptValidation(payload));
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(ThaumaturgeNodeNavigator.MOD_ID, "node_pointer"),
                NodePointerOverlay::render);
    }
}
