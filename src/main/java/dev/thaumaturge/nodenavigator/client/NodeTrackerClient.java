package dev.thaumaturge.nodenavigator.client;

import com.mojang.logging.LogUtils;
import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NodeTrackerClient {
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.0 * 2.0;
    private static final List<NodeRecord> NODES = new ArrayList<>();
    private static final NodeValidationSession VALIDATION = new NodeValidationSession();
    private static ClientLevel validationLevel;
    private static NodeCache cache;
    private static String markedKey;
    private static int revision;
    private static boolean storageError;
    private NodeTrackerClient() {}

    public static void configure(TrackerProfilePayload payload) {
        disconnect();
        Minecraft minecraft = Minecraft.getInstance();
        String server = minecraft.getSingleplayerServer() != null ? "singleplayer"
                : minecraft.getCurrentServer() == null ? "multiplayer" : minecraft.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        String identity = server + "|" + payload.world() + "|" + payload.player();
        cache = new NodeCache(minecraft.gameDirectory.toPath().resolve("thaumaturge_node_navigator")
                .resolve(profileName(server, identity)).resolve("nodes.json"));
        NodeCache.State state = cache.load();
        NODES.addAll(state.nodes());
        markedKey = state.markedKey();
        storageError = cache.recovered();
        revision++;
    }

    static String profileName(String server, String identity) {
        String prefix = server.replaceAll("[^a-zA-Z0-9._-]+", "_");
        prefix = prefix.substring(0, Math.min(prefix.length(), 32));
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
            return prefix + "-" + digest;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void disconnect() {
        VALIDATION.reset();
        validationLevel = null;
        NODES.clear();
        markedKey = null;
        cache = null;
        storageError = false;
        revision++;
    }
    public static boolean ready() { return cache != null; }
    public static boolean storageError() { return storageError; }
    public static int revision() { return revision; }

    public static void accept(NodeScannedPayload payload) {
        if (cache == null) {
            LogUtils.getLogger().warn("Ignoring node snapshot received before navigator profile synchronization");
            return;
        }
        NodeRecord incoming = NodeRecord.from(payload);
        NODES.removeIf(existing -> existing.key().equals(incoming.key()));
        NODES.add(incoming);
        revision++;
        save();
    }
    public static void delete(NodeRecord node) {
        NODES.removeIf(existing -> existing.key().equals(node.key()));
        if (node.key().equals(markedKey)) markedKey = null;
        revision++;
        save();
    }
    public static void mark(NodeRecord node) { markedKey = node.key(); revision++; save(); }
    public static void clearMark() { markedKey = null; revision++; save(); }
    public static void toggleMark(NodeRecord node) {
        if (node.key().equals(markedKey)) clearMark();
        else mark(node);
    }
    public static void checkMissingNodes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (validationLevel != minecraft.level) {
            VALIDATION.reset();
            validationLevel = minecraft.level;
        }
        if (cache == null || minecraft.level == null || minecraft.player == null
                || minecraft.getConnection() == null || minecraft.isPaused()) return;
        ClientLevel level = minecraft.level;
        NodeValidationPayload request = VALIDATION.tick(NODES, level.dimension().location(),
                node -> level.hasChunk(node.blockPos().getX() >> 4, node.blockPos().getZ() >> 4));
        if (request != null) PacketDistributor.sendToServer(request);
    }
    public static void acceptValidation(NodeValidationPayload reply) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cache == null || minecraft.level == null || validationLevel != minecraft.level
                || !minecraft.level.dimension().location().equals(reply.dimension())) return;
        NodeCache.State before = new NodeCache.State(1, NODES, markedKey);
        NodeCache.State after = VALIDATION.apply(before, reply);
        if (after == before) return;
        NODES.clear();
        NODES.addAll(after.nodes());
        markedKey = after.markedKey();
        revision++;
        save();
    }
    public static void checkArrival() {
        if (markedKey == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (hasArrived(marked(), minecraft.level.dimension().location().toString(),
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())) {
            clearMark();
        }
    }
    static boolean hasArrived(NodeRecord node, String playerDimension, double playerX, double playerY, double playerZ) {
        if (node == null || !node.dimension.equals(playerDimension)) return false;
        var pos = node.blockPos();
        double dx = pos.getX() + 0.5 - playerX;
        double dy = pos.getY() + 0.5 - playerY;
        double dz = pos.getZ() + 0.5 - playerZ;
        return dx * dx + dy * dy + dz * dz <= ARRIVAL_DISTANCE_SQUARED;
    }
    public static NodeRecord marked() {
        return markedKey == null ? null : NODES.stream().filter(node -> node.key().equals(markedKey)).findFirst().orElse(null);
    }
    public static ResourceLocation currentDimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "overworld") : level.dimension().location();
    }
    public static List<String> availableAspects() {
        return NodeFilters.collect(NODES.stream().map(node -> node.aspects).toList());
    }
    public static List<NodeRecord> sorted(Set<String> selectedAspects, boolean matchAll, String sort) {
        Comparator<NodeRecord> comparator = Comparator.comparingDouble(NodeTrackerClient::distanceExact);
        if (!"distance".equals(sort)) {
            comparator = Comparator.comparingInt((NodeRecord node) -> NodeFilters.amount(node.aspects, sort)).reversed()
                    .thenComparingDouble(NodeTrackerClient::distanceExact);
        }
        return NODES.stream().filter(node -> NodeFilters.matches(node.aspects, selectedAspects, matchAll))
                .sorted(comparator.thenComparing(node -> node.dimension).thenComparingLong(node -> node.position)).toList();
    }
    public static int distance(NodeRecord node) { return (int) Math.round(distanceExact(node)); }
    private static double distanceExact(NodeRecord node) {
        var player = Minecraft.getInstance().player;
        if (player == null || !node.dimension.equals(currentDimension().toString())) return Double.POSITIVE_INFINITY;
        return NavigationMath.horizontalDistance(node.blockPos().getX() + 0.5 - player.getX(),
                node.blockPos().getZ() + 0.5 - player.getZ());
    }
    private static void save() {
        if (cache == null) return;
        try {
            cache.save(new NodeCache.State(1, List.copyOf(NODES), markedKey));
        } catch (IOException failure) {
            storageError = true;
            LogUtils.getLogger().error("Cannot save node navigator cache; records remain available in memory", failure);
        }
    }
}
