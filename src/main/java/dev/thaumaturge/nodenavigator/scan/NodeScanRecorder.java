package dev.thaumaturge.nodenavigator.scan;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

public final class NodeScanRecorder {
    private static final Map<ServerPlayer, LastScan> LAST = new WeakHashMap<>();
    private record LastScan(String dimension, long position, long tick) {}
    private NodeScanRecorder() {}
    public static void record(Player player, Object target) {
        BlockPos pos = ScanCompatibility.blockPosition(target);
        if (!(player instanceof ServerPlayer serverPlayer) || pos == null
                || !(player.level().getBlockEntity(pos) instanceof BlockEntityNode node)
                || !KnowledgeAccess.of(player).isResearchKnown(ScanNode.researchKey(player.level(), pos))) return;
        var dimension = player.level().dimension().location();
        var scan = new LastScan(dimension.toString(), pos.asLong(), player.level().getGameTime());
        if (scan.equals(LAST.put(serverPlayer, scan))) return;
        List<NodeScannedPayload.AspectValue> aspects = new ArrayList<>();
        for (var entry : node.getAspects().entries()) {
            entry.aspect().unwrapKey().ifPresent(key -> aspects.add(
                    new NodeScannedPayload.AspectValue(key.location(), entry.amount())));
        }
        NodeScannedPayload.send(serverPlayer, new NodeScannedPayload(dimension, pos.immutable(), node.getNodeType(),
                node.getNodeModifier(), List.copyOf(aspects), System.currentTimeMillis()));
    }
}
