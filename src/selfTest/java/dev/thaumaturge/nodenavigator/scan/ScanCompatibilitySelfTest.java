package dev.thaumaturge.nodenavigator.scan;

import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import com.leclowndu93150.thaumaturge.content.research.scan.ScanNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class ScanCompatibilitySelfTest {
    private static int checks;

    public static int run() throws Exception {
        checks = 0;
        BlockPos position = new BlockPos(-30, 64, 72);
        check(ScanCompatibility.blockPosition(position) == position, "legacy block target keeps its coordinates");
        check(ScanCompatibility.blockPosition(null) == null, "null target is ignored");
        check(ScanCompatibility.blockPosition(new Object()) == null, "unrelated target is ignored");
        check(!ScanCompatibility.isNodeHost(null) && !ScanCompatibility.isNodeHost(new Object()),
                "ordinary blocks are not optional node hosts");
        NodeScanRecorder.record(null, null);
        NodeScanRecorder.record(null, position);

        Class<?> scanInterface;
        Class<?> targetType;
        String matchMethod;
        String researchMethod;
        String successMethod;
        String refusalMethod;
        boolean modern;
        try {
            scanInterface = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.IScannable");
            targetType = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.ScanTarget");
            Class<?> blockTarget = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.ScannedBlock");
            Object scannedBlock = blockTarget.getConstructor(BlockPos.class).newInstance(position);
            check(position.equals(ScanCompatibility.blockPosition(scannedBlock)), "modern block target unwraps");
            Class<?> hostType = Class.forName("com.leclowndu93150.thaumaturge.content.aura.node.NodeHostBlock");
            Object host = Proxy.newProxyInstance(hostType.getClassLoader(), new Class<?>[]{hostType},
                    (proxy, method, arguments) -> null);
            check(ScanCompatibility.isNodeHost(host), "modern node hosts remain protected from pruning");
            matchMethod = "matches";
            researchMethod = "research";
            successMethod = "onScanned";
            refusalMethod = "refusal";
            modern = true;
        } catch (ClassNotFoundException legacy) {
            scanInterface = Class.forName("com.leclowndu93150.thaumaturge.api.research.scan.IScanThing");
            targetType = Object.class;
            matchMethod = "checkThing";
            researchMethod = "getResearchKey";
            successMethod = "onSuccess";
            refusalMethod = "scanFailure";
            modern = false;
        }

        Method callback = ScanNode.class.getMethod(successMethod, Player.class, targetType);
        check(callback.getReturnType() == void.class, "installed ScanNode exposes the expected callback");
        KnownNodeScan.register();
        List<?> subjects = null;
        Object subject = null;
        for (Field field : ScanningManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            List<?> candidates = (List<?>) field.get(null);
            for (Object candidate : candidates) {
                if (Proxy.isProxyClass(candidate.getClass())
                        && Proxy.getInvocationHandler(candidate) instanceof KnownNodeScan) {
                    subjects = candidates;
                    subject = candidate;
                }
            }
        }
        check(subject != null && scanInterface.isInstance(subject), "handler registers with the installed scan API");
        try {
            check(subject.equals(subject) && !subject.equals(new Object()) && !subject.equals(null),
                    "scan subject uses identity equality");
            check(subject.hashCode() == System.identityHashCode(subject), "scan subject has a stable identity hash");
            check(subject.toString().contains("Node Navigator"), "scan subject can be logged safely");
            check(Boolean.FALSE.equals(scanInterface.getMethod(matchMethod, Player.class, targetType)
                    .invoke(subject, null, null)), "null targets never match");
            check(scanInterface.getMethod(researchMethod, Player.class, targetType).invoke(subject, null, null) == null,
                    "repeat scans grant no additional research");
            check(scanInterface.getMethod(refusalMethod, Player.class, targetType).invoke(subject, null, null) == null,
                    "default refusal method permits valid repeat scans");
            check(scanInterface.getMethod(successMethod, Player.class, targetType).invoke(subject, null, null) == null,
                    "success callback ignores invalid targets");
            if (modern) {
                check(Boolean.FALSE.equals(scanInterface.getMethod("rescannable", Player.class, targetType)
                        .invoke(subject, null, null)), "default rescannable behavior remains intact");
            } else {
                check(Boolean.FALSE.equals(scanInterface.getMethod(matchMethod, Player.class, targetType)
                        .invoke(subject, null, new Object())), "legacy non-block objects do not match");
            }
        } finally {
            subjects.remove(subject);
        }
        System.out.println("Scan compatibility: " + (modern ? "modern" : "legacy") + " API, " + checks + " checks passed.");
        return checks;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
