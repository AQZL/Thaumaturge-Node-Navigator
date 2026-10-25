package dev.thaumaturge.nodenavigator.mixin;

import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import dev.thaumaturge.nodenavigator.scan.NodeScanRecorder;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScanNode.class, remap = false)
public abstract class ScanNodeMixin {
    @Group(name = "node_scan_record", min = 1)
    @Inject(method = "onScanned", at = @At("RETURN"), require = 0)
    private void thaumaturgeNodeNavigator$record(Player player, @Coerce Object target, CallbackInfo ci) {
        NodeScanRecorder.record(player, target);
    }

    @Group(name = "node_scan_record", min = 1)
    @Inject(method = "onSuccess", at = @At("RETURN"), require = 0)
    private void thaumaturgeNodeNavigator$recordLegacy(Player player, Object target, CallbackInfo ci) {
        NodeScanRecorder.record(player, target);
    }
}
