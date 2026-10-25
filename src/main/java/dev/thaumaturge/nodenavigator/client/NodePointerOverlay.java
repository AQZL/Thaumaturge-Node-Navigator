package dev.thaumaturge.nodenavigator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import com.mojang.math.Axis;

public final class NodePointerOverlay {
    private NodePointerOverlay() {}
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        NodeRecord node = NodeTrackerClient.marked();
        if (node == null) return;
        int center = graphics.guiWidth() / 2;
        if (!node.dimension.equals(NodeTrackerClient.currentDimension().toString())) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("hud.thaumaturge_node_navigator.other_dimension", NodeDisplay.dimensionName(node.dimension)), center, 14, 0xFFE0BC77);
            return;
        }
        double dx = node.blockPos().getX() + 0.5 - minecraft.player.getX();
        double dz = node.blockPos().getZ() + 0.5 - minecraft.player.getZ();
        double bearing = NavigationMath.bearing(dx, dz, minecraft.player.getYRot());
        graphics.pose().pushPose();
        graphics.pose().translate(center, 22, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) bearing));
        graphics.fill(-1, -6, 2, 8, 0xFFF1D191);
        for (int row = 0; row < 7; row++) graphics.fill(-row, -10 + row, row + 1, -9 + row, 0xFFF1D191);
        graphics.pose().popPose();
        String vertical = minecraft.player.getY() > node.blockPos().getY() + 1 ? "below"
                : minecraft.player.getY() < node.blockPos().getY() - 1 ? "above" : "level";
        Component status = Component.translatable("hud.thaumaturge_node_navigator.destination", NodeTrackerClient.distance(node),
                Component.translatable("hud.thaumaturge_node_navigator." + vertical));
        graphics.drawCenteredString(minecraft.font, status, center, 37, 0xFFFFFFFF);
    }
}
