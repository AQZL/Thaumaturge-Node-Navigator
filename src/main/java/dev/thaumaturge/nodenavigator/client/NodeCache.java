package dev.thaumaturge.nodenavigator.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class NodeCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> TYPES = Set.of("normal", "unstable", "dark", "tainted", "hungry", "pure");
    private static final Set<String> MODIFIERS = Set.of("", "bright", "pale", "fading");
    private final Path file;
    private boolean writable = true;
    private boolean recovered;
    public record State(int schema, List<NodeRecord> nodes, String markedKey) {
        public static State empty() { return new State(1, List.of(), null); }
    }
    public NodeCache(Path file) { this.file = file; }
    public boolean recovered() { return recovered; }

    public State load() {
        if (!Files.exists(file)) return State.empty();
        try {
            if (Files.size(file) > 64 * 1024 * 1024) throw new IOException("Node cache exceeds 64 MiB");
            State state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), State.class);
            if (state == null || state.schema() != 1 || state.nodes() == null) throw new IOException("Unsupported node cache");
            var nodes = new LinkedHashMap<String, NodeRecord>();
            for (NodeRecord node : state.nodes()) {
                validate(node);
                NodeRecord previous = nodes.get(node.key());
                if (previous == null || node.lastScanned >= previous.lastScanned) nodes.put(node.key(), node);
            }
            String mark = nodes.containsKey(state.markedKey()) ? state.markedKey() : null;
            return new State(1, new ArrayList<>(nodes.values()), mark);
        } catch (Exception failure) {
            recovered = true;
            LogUtils.getLogger().error("Cannot load node cache {}; preserving original data", file, failure);
            try {
                Files.copy(file, file.resolveSibling("nodes.corrupt-" + UUID.randomUUID() + ".json"));
            } catch (IOException backupFailure) {
                writable = false;
                LogUtils.getLogger().error("Cannot back up damaged node cache; disk writes disabled for this session", backupFailure);
            }
            return State.empty();
        }
    }

    private static void validate(NodeRecord node) throws IOException {
        if (node == null || node.dimension == null || Identifier.tryParse(node.dimension) == null
                || node.type == null || !TYPES.contains(node.type)) throw new IOException("Invalid node record");
        if (node.modifier == null) node.modifier = "";
        if (!MODIFIERS.contains(node.modifier)) throw new IOException("Invalid node modifier");
        if (node.aspects == null) node.aspects = new LinkedHashMap<>();
        if (node.aspects.size() > 256) throw new IOException("Too many aspects");
        for (var entry : node.aspects.entrySet()) {
            if (entry.getKey() == null || Identifier.tryParse(entry.getKey()) == null
                    || entry.getValue() == null || entry.getValue() < 0) throw new IOException("Invalid aspect entry");
        }
    }

    public void save(State state) throws IOException {
        if (!writable) throw new IOException("Original damaged cache could not be backed up");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "nodes-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}