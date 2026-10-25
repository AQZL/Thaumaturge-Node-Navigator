package dev.thaumaturge.nodenavigator.client;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class NodeFilters {
    private NodeFilters() {}

    public static List<String> collect(Iterable<? extends Map<String, Integer>> snapshots) {
        Set<String> available = new TreeSet<>();
        for (Map<String, Integer> aspects : snapshots) {
            if (aspects == null) continue;
            for (String aspect : aspects.keySet()) {
                if (aspect != null && amount(aspects, aspect) > 0) available.add(aspect);
            }
        }
        return List.copyOf(available);
    }

    public static boolean matches(Map<String, Integer> aspects, Set<String> selected, boolean matchAll) {
        if (selected.isEmpty()) return true;
        for (String aspect : selected) {
            boolean present = amount(aspects, aspect) > 0;
            if (matchAll && !present) return false;
            if (!matchAll && present) return true;
        }
        return matchAll;
    }

    public static int amount(Map<String, Integer> aspects, String aspect) {
        if (aspects == null) return 0;
        Integer value = aspects.get(aspect);
        return value == null ? 0 : Math.max(0, value);
    }

    public static String nextSort(String current, List<String> aspects, int direction) {
        int index = current.equals("distance") ? 0 : Math.max(0, aspects.indexOf(current) + 1);
        int next = Math.floorMod(index + direction, aspects.size() + 1);
        return next == 0 ? "distance" : aspects.get(next - 1);
    }
}
