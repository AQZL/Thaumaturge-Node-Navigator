package dev.thaumaturge.nodenavigator.client;

import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = ThaumaturgeNodeNavigator.MOD_ID, value = Dist.CLIENT)
public final class NodeNavigatorClientEvents {
    private static final ResourceLocation THAUMATURGE_LEFT_HUD =
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "left_hud_stack");

    private NodeNavigatorClientEvents() {}

    @SubscribeEvent
    public static void hideGogglesHudInNavigator(RenderGuiLayerEvent.Pre event) {
        if (THAUMATURGE_LEFT_HUD.equals(event.getName())
                && Minecraft.getInstance().screen instanceof NodeTrackerScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        NodeTrackerClient.disconnect();
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        NodeTrackerClient.checkMissingNodes();
        NodeTrackerClient.checkArrival();
        if (NodeNavigatorClientSetup.OPEN_TRACKER.consumeClick() && minecraft.screen == null && minecraft.level != null) {
            minecraft.setScreen(new NodeTrackerScreen());
        }
    }
}
