package dev.thaumaturge.nodenavigator.client;

import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = ThaumaturgeNodeNavigator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NodeNavigatorClientSetup {
    public static final KeyMapping OPEN_TRACKER = new KeyMapping(
            "key.thaumaturge_node_navigator.open_tracker",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_I,
            "key.category.thaumaturge_node_navigator.controls");

    private NodeNavigatorClientSetup() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TRACKER);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ThaumaturgeNodeNavigator.MOD_ID, "node_pointer"),
                NodePointerOverlay::render);
    }
}
