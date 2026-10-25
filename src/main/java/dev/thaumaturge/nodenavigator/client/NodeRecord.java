package dev.thaumaturge.nodenavigator.client;

import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class NodeRecord {
    public String dimension;
    public long position;
    public String type;
    public String modifier;
    public Map<String, Integer> aspects;
    public long lastScanned;

    public NodeRecord() {
    }

    public NodeRecord(String dimension, long position, String type, String modifier,
                      Map<String, Integer> aspects, long lastScanned) {
        this.dimension = dimension;
        this.position = position;
        this.type = type;
        this.modifier = modifier;
        this.aspects = new LinkedHashMap<>(aspects);
        this.lastScanned = lastScanned;
    }

    public static NodeRecord from(NodeScannedPayload payload) {
        Map<String, Integer> aspects = new LinkedHashMap<>();
        for (NodeScannedPayload.AspectValue value : payload.aspects()) {
            aspects.put(value.aspect().toString(), value.amount());
        }
        return new NodeRecord(payload.dimension().toString(), payload.position().asLong(),
                payload.nodeType().getSerializedName(),
                payload.modifier() == null ? "" : payload.modifier().getSerializedName(),
                aspects, payload.scannedAt());
    }

    public BlockPos blockPos() {
        return BlockPos.of(position);
    }

    public String key() {
        return dimension + ":" + position;
    }

    public int primalAmount(String name) {
        return aspects == null ? 0 : aspects.getOrDefault("thaumaturge:" + name, 0);
    }
}
