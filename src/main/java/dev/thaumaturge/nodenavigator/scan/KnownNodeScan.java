package dev.thaumaturge.nodenavigator.scan;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.scan.IScannable;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanTarget;
import com.leclowndu93150.thaumaturge.api.research.scan.ScannedBlock;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class KnownNodeScan implements IScannable {
    @Override
    public boolean matches(Player player, ScanTarget target) {
        return target instanceof ScannedBlock(var position)
                && player.level().getBlockEntity(position) instanceof BlockEntityNode
                && KnowledgeAccess.of(player).isResearchKnown(ScanNode.researchKey(player.level(), position));
    }
    @Override
    public Identifier research(Player player, ScanTarget target) { return null; }
    @Override
    public void onScanned(Player player, ScanTarget target) {
        if (matches(player, target)) NodeScanRecorder.record(player, target);
    }
}