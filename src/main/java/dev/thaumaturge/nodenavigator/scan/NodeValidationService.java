package dev.thaumaturge.nodenavigator.scan;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockJarNode;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockNode;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NodeValidationService {
    private static final Map<ServerPlayer, Long> LAST_CHECK = new WeakHashMap<>();
    private NodeValidationService() {}

    public static void handle(NodeValidationPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var level = player.level();
        if (!request.dimension().equals(level.dimension().location())) return;
        long now = level.getGameTime();
        Long previous = LAST_CHECK.get(player);
        if (previous != null && now >= previous && now - previous < 10) return;
        LAST_CHECK.put(player, now);
        var knowledge = KnowledgeAccess.of(player);
        var missing = new ArrayList<BlockPos>();
        for (BlockPos pos : request.positions()) {
            if (!knowledge.isResearchKnown(ScanNode.researchKey(level, pos))) continue;
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) continue;
            var block = chunk.getBlockState(pos).getBlock();
            if (block instanceof BlockNode || block instanceof BlockJarNode || ScanCompatibility.isNodeHost(block)
                    || chunk.getBlockEntities().get(pos) instanceof BlockEntityNode) continue;
            missing.add(pos);
        }
        context.reply(new NodeValidationPayload(request.requestId(), request.dimension(), missing));
    }
}
