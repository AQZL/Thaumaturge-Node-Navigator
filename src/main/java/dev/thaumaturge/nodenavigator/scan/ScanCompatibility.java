package dev.thaumaturge.nodenavigator.scan;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;

public final class ScanCompatibility {
    private static final Class<?> SCANNED_BLOCK = optionalType(
            "com.leclowndu93150.thaumaturge.api.research.scan.ScannedBlock");
    private static final Class<?> NODE_HOST = optionalType(
            "com.leclowndu93150.thaumaturge.content.aura.node.NodeHostBlock");
    private static final Method BLOCK_POSITION = blockPositionAccessor();

    private ScanCompatibility() {}

    public static BlockPos blockPosition(Object target) {
        if (target instanceof BlockPos position) return position;
        if (SCANNED_BLOCK == null || !SCANNED_BLOCK.isInstance(target)) return null;
        try {
            return (BlockPos) BLOCK_POSITION.invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the scanned node position", failure);
        }
    }

    public static boolean isNodeHost(Object block) {
        return NODE_HOST != null && NODE_HOST.isInstance(block);
    }

    private static Class<?> optionalType(String name) {
        try {
            return Class.forName(name, false, ScanCompatibility.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static Method blockPositionAccessor() {
        if (SCANNED_BLOCK == null) return null;
        try {
            return SCANNED_BLOCK.getMethod("pos");
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Unsupported scanned-block API", failure);
        }
    }
}
