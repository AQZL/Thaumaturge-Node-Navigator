package dev.thaumaturge.nodenavigator.client;

import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

final class NodeValidationSession {
    private static final int INTERVAL_TICKS = 20;
    private static final int MAX_RECORDS_PER_POLL = 256;
    private static final int TIMEOUT_POLLS = 5;
    private long sequence;
    private int ticks;
    private int cursor;
    private int waitingPolls;
    private Pending pending;

    private record Pending(long id, ResourceLocation dimension, Map<Long, NodeRecord> records) {}

    void reset() {
        ticks = 0;
        cursor = 0;
        waitingPolls = 0;
        pending = null;
    }

    NodeValidationPayload tick(List<NodeRecord> nodes, ResourceLocation dimension, Predicate<NodeRecord> loaded) {
        if (++ticks < INTERVAL_TICKS) return null;
        ticks = 0;
        if (pending != null && ++waitingPolls < TIMEOUT_POLLS) return null;
        pending = null;
        waitingPolls = 0;
        if (nodes.isEmpty()) { cursor = 0; return null; }
        Map<Long, NodeRecord> candidates = new LinkedHashMap<>();
        int limit = Math.min(nodes.size(), MAX_RECORDS_PER_POLL);
        for (int checked = 0; checked < limit && candidates.size() < NodeValidationPayload.MAX_POSITIONS; checked++) {
            cursor %= nodes.size();
            NodeRecord node = nodes.get(cursor++);
            if (node.dimension.equals(dimension.toString()) && loaded.test(node)) {
                candidates.put(node.position, node);
            }
        }
        if (candidates.isEmpty()) return null;
        pending = new Pending(++sequence, dimension, candidates);
        return new NodeValidationPayload(pending.id(), dimension,
                candidates.values().stream().map(NodeRecord::blockPos).toList());
    }

    NodeCache.State apply(NodeCache.State state, NodeValidationPayload reply) {
        if (pending == null || pending.id() != reply.requestId() || !pending.dimension().equals(reply.dimension())) {
            return state;
        }
        var missing = new HashSet<NodeRecord>();
        for (var position : reply.positions()) {
            NodeRecord original = pending.records().get(position.asLong());
            if (original != null) missing.add(original);
        }
        pending = null;
        waitingPolls = 0;
        List<NodeRecord> kept = state.nodes().stream().filter(node -> !missing.contains(node)).toList();
        if (kept.size() == state.nodes().size()) return state;
        String marked = kept.stream().anyMatch(node -> node.key().equals(state.markedKey())) ? state.markedKey() : null;
        return new NodeCache.State(state.schema(), kept, marked);
    }
}
