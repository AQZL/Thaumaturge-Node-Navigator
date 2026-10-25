package dev.thaumaturge.nodenavigator.mixin;

import com.leclowndu93150.thaumaturge.api.research.scan.ScanTarget;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import dev.thaumaturge.nodenavigator.scan.NodeScanRecorder;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScanNode.class, remap = false)
public abstract class ScanNodeMixin {
    @Inject(method = "onScanned", at = @At("RETURN"))
    private void thaumaturgeNodeNavigator$record(Player player, ScanTarget target, CallbackInfo ci) {
        NodeScanRecorder.record(player, target);
    }
}