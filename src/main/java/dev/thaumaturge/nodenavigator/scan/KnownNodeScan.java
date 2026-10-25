package dev.thaumaturge.nodenavigator.scan;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class KnownNodeScan implements InvocationHandler {
    public static void register() {
        try {
            ClassLoader loader = ScanningManager.class.getClassLoader();
            Class<?> scanInterface;
            String registration;
            try {
                scanInterface = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.IScannable", false, loader);
                registration = "register";
            } catch (ClassNotFoundException legacy) {
                scanInterface = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.IScanThing", false, loader);
                registration = "addScannableThing";
            }
            Object subject = Proxy.newProxyInstance(loader, new Class<?>[]{scanInterface}, new KnownNodeScan());
            ScanningManager.class.getMethod(registration, scanInterface).invoke(null, subject);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register the node navigator scan handler", failure);
        }
    }

    public boolean matches(Player player, Object target) {
        BlockPos position = ScanCompatibility.blockPosition(target);
        return position != null && player.level().getBlockEntity(position) instanceof BlockEntityNode
                && KnowledgeAccess.of(player).isResearchKnown(ScanNode.researchKey(player.level(), position));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Thaumaturge Node Navigator scan handler";
            case "matches", "checkThing" -> matches((Player) arguments[0], arguments[1]);
            case "research", "getResearchKey" -> null;
            case "onScanned", "onSuccess" -> {
                if (matches((Player) arguments[0], arguments[1])) {
                    NodeScanRecorder.record((Player) arguments[0], arguments[1]);
                }
                yield null;
            }
            default -> {
                if (!method.isDefault()) throw new UnsupportedOperationException(method.toString());
                yield InvocationHandler.invokeDefault(proxy, method, arguments);
            }
        };
    }
}
