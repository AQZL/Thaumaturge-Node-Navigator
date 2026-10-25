package dev.thaumaturge.nodenavigator.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.api.nodes.NodeModifier;
import dev.thaumaturge.nodenavigator.network.NodeScannedPayload;
import dev.thaumaturge.nodenavigator.network.NodeValidationPayload;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class TrackerSelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        checks += dev.thaumaturge.nodenavigator.scan.ScanCompatibilitySelfTest.run();
        filterChecks();
        sortingChecks();
        buttonChecks();
        scrollChecks();
        packetChecks();
        validationPacketChecks();
        arrivalChecks();
        near(NavigationMath.bearing(0, 10, 0), 0, "south when facing south");
        near(NavigationMath.bearing(10, 0, 0), 90, "east appears right when facing south");
        near(NavigationMath.bearing(-10, 0, 90), 0, "west when facing west");
        near(NavigationMath.bearing(10, 0, -90), 0, "east when facing east");
        near(NavigationMath.bearing(0, -10, 180), 0, "north when facing north");
        near(NavigationMath.bearing(10, 0, 270), 0, "yaw beyond one revolution");
        near(NavigationMath.bearing(0, -10, 0), -180, "target behind player");
        near(NavigationMath.horizontalDistance(3, 4), 5, "horizontal distance ignores height");
        layoutChecks();
        String first = NodeTrackerClient.profileName("a:b", "server-a|world-a|player-a");
        check(!first.equals(NodeTrackerClient.profileName("a/b", "server-b|world-a|player-a")), "sanitized names cannot collide");
        check(!first.equals(NodeTrackerClient.profileName("a:b", "server-a|world-b|player-a")), "world reset uses a new cache");
        check(!first.equals(NodeTrackerClient.profileName("a:b", "server-a|world-a|player-b")), "accounts use separate caches");
        Path directory = Files.createTempDirectory("node-navigator-check-");
        try {
            validationChecks(directory.resolve("validated-nodes.json"));
            Path file = directory.resolve("nodes.json");
            NodeCache cache = new NodeCache(file);
            NodeRecord original = new NodeRecord("minecraft:overworld", 123L, "normal", "", Map.of("thaumaturge:aer", 10), 1);
            NodeRecord updated = new NodeRecord("minecraft:overworld", 123L, "normal", null, Map.of("thaumaturge:aer", 20), 2);
            NodeRecord nether = new NodeRecord("minecraft:the_nether", 123L, "pure", "bright", Map.of("thaumaturge:ignis", 5), 1);
            cache.save(new NodeCache.State(1, List.of(original, updated, nether), updated.key()));
            NodeCache.State loaded = cache.load();
            check(loaded.nodes().size() == 2, "duplicates merge but dimensions remain separate");
            check(loaded.nodes().getFirst().primalAmount("aer") == 20, "newest snapshot survives");
            check(loaded.nodes().getFirst().modifier.equals(""), "null modifiers are normalized");
            check(updated.key().equals(loaded.markedKey()), "selected destination survives restart");
            cache.save(new NodeCache.State(1, loaded.nodes(), null));
            NodeCache.State arrived = new NodeCache(file).load();
            check(arrived.nodes().size() == 2, "completing navigation retains all scanned node records");
            check(arrived.nodes().getFirst().key().equals(updated.key())
                            && arrived.nodes().getFirst().primalAmount("aer") == 20
                            && arrived.nodes().getLast().key().equals(nether.key()),
                    "completing navigation preserves recorded destinations and aspect data");
            check(arrived.markedKey() == null, "completed navigation stays unmarked after cache reload");
            cache.save(new NodeCache.State(1, List.of(nether), updated.key()));
            check(cache.load().markedKey() == null, "deleted destination cannot remain selected");
            Files.writeString(file, "{truncated json");
            System.out.println("Testing corrupt-cache recovery: the following logged parse error is expected.");
            NodeCache broken = new NodeCache(file);
            check(broken.load().nodes().isEmpty(), "damaged cache does not crash loading");
            check(broken.recovered(), "cache recovery is visible to the UI");
            try (var paths = Files.list(directory)) {
                Path backup = paths.filter(p -> p.getFileName().toString().startsWith("nodes.corrupt-")).findFirst().orElseThrow();
                check(Files.readString(backup).equals("{truncated json"), "original damaged bytes are preserved");
            }
            broken.save(new NodeCache.State(1, List.of(nether), null));
            check(broken.load().nodes().size() == 1, "cache remains usable after recovery");
            System.out.println("Node navigator self-test: " + checks + " checks passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }

    }
    private static void validationChecks(Path cacheFile) throws Exception {
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        NodeRecord first = validationNode(overworld, 10);
        NodeRecord second = validationNode(overworld, 11);
        NodeRecord valid = validationNode(overworld, 12);
        NodeRecord unloaded = validationNode(overworld, 13);
        NodeRecord otherDimension = validationNode(nether, 10);
        var state = new NodeCache.State(1, List.of(first, second, valid, unloaded, otherDimension), first.key());
        NodeValidationSession session = new NodeValidationSession();
        int[] examined = {0};
        check(pollValidation(session, List.of(), overworld, node -> { examined[0]++; return true; }) == null
                        && examined[0] == 0,
                "empty node caches do not issue validation requests");
        check(pollValidation(session, state.nodes(), overworld, node -> { examined[0]++; return false; }) == null
                        && examined[0] == 4,
                "unloaded records are retained without requesting validation");
        examined[0] = 0;
        check(pollValidation(session, List.of(otherDimension), overworld, node -> { examined[0]++; return true; }) == null
                        && examined[0] == 0,
                "records in another dimension never probe local chunks");
        session.reset();
        boolean quietBeforeInterval = true;
        for (int tick = 0; tick < 19; tick++) {
            quietBeforeInterval &= session.tick(state.nodes(), overworld, node -> { examined[0]++; return true; }) == null;
        }
        check(quietBeforeInterval && examined[0] == 0, "validation waits twenty ticks before examining records");
        NodeValidationPayload request = session.tick(state.nodes(), overworld, node -> node != unloaded);
        check(request != null && request.requestId() > 0 && request.dimension().equals(overworld)
                        && request.positions().equals(List.of(first.blockPos(), second.blockPos(), valid.blockPos())),
                "validation requests contain only loaded records in the current dimension");
        var wrongDimension = new NodeValidationPayload(request.requestId(), nether, List.of(first.blockPos()));
        check(session.apply(state, wrongDimension) == state, "a reply for another dimension cannot remove a record");
        var wrongId = new NodeValidationPayload(request.requestId() + 1, overworld, List.of(first.blockPos()));
        check(session.apply(state, wrongId) == state, "an unrelated request ID leaves records and the pending request intact");
        var missing = new NodeValidationPayload(request.requestId(), overworld,
                List.of(first.blockPos(), second.blockPos(), first.blockPos(), unloaded.blockPos(), new BlockPos(900, 64, 900)));
        NodeCache.State cleaned = session.apply(state, missing);
        check(cleaned != state && cleaned.schema() == state.schema()
                        && cleaned.nodes().equals(List.of(valid, unloaded, otherDimension)),
                "confirmed missing records are removed in one batch while valid and unrequested records survive");
        check(cleaned.nodes().contains(otherDimension), "a missing node cannot delete the same coordinates in another dimension");
        check(cleaned.markedKey() == null, "deleting the marked destination also cancels navigation");
        check(session.apply(cleaned, missing) == cleaned, "duplicate replies cannot perform a second deletion");
        NodeCache cache = new NodeCache(cacheFile);
        cache.save(cleaned);
        NodeCache.State reloaded = new NodeCache(cacheFile).load();
        check(reloaded.nodes().stream().map(NodeRecord::key).toList()
                        .equals(List.of(valid.key(), unloaded.key(), otherDimension.key())),
                "automatic deletions remain deleted after saving and reopening the cache");
        check(reloaded.markedKey() == null && reloaded.nodes().getFirst().primalAmount("aer") == 12,
                "cache reload preserves cancellation and the surviving node aspect data");

        session = new NodeValidationSession();
        request = pollValidation(session, state.nodes(), overworld, node -> true);
        var noMissing = new NodeValidationPayload(request.requestId(), overworld, List.of());
        check(session.apply(state, noMissing) == state, "an empty server reply preserves the original state and marker");
        NodeValidationPayload nextRequest = pollValidation(session, state.nodes(), overworld, node -> true);
        check(nextRequest != null && nextRequest.requestId() > request.requestId(),
                "an empty reply completes the request so later checks continue");
        var unrelatedPosition = new NodeValidationPayload(nextRequest.requestId(), overworld, List.of(new BlockPos(999, 64, 999)));
        check(session.apply(state, unrelatedPosition) == state, "positions not present in the request cannot remove any record");

        session = new NodeValidationSession();
        request = pollValidation(session, state.nodes(), overworld, node -> true);
        NodeRecord rescanned = new NodeRecord(first.dimension, first.position, first.type, first.modifier,
                first.aspects, first.lastScanned);
        var refreshed = new NodeCache.State(1, List.of(rescanned, second, valid, unloaded, otherDimension), rescanned.key());
        var staleMissing = new NodeValidationPayload(request.requestId(), overworld, List.of(first.blockPos()));
        check(session.apply(refreshed, staleMissing) == refreshed && refreshed.nodes().getFirst() == rescanned,
                "a scan replacing the requested object survives even when its coordinates and timestamp are unchanged");
        request = pollValidation(session, refreshed.nodes(), overworld, node -> true);
        NodeCache.State stillMarked = session.apply(refreshed,
                new NodeValidationPayload(request.requestId(), overworld, List.of(second.blockPos())));
        check(stillMarked.nodes().equals(List.of(rescanned, valid, unloaded, otherDimension))
                        && rescanned.key().equals(stillMarked.markedKey()),
                "removing an unmarked node preserves navigation to a surviving destination");

        session = new NodeValidationSession();
        request = pollValidation(session, state.nodes(), overworld, node -> true);
        session.reset();
        var beforeReset = new NodeValidationPayload(request.requestId(), overworld, List.of(first.blockPos()));
        check(session.apply(state, beforeReset) == state, "reset discards an old world's pending deletion reply");
        nextRequest = pollValidation(session, state.nodes(), overworld, node -> true);
        check(nextRequest.requestId() > request.requestId(), "request identifiers are never reused after a world reset");
        check(session.apply(state, beforeReset) == state, "an old reply cannot match a new request after resetting");
        NodeCache.State afterReset = session.apply(state,
                new NodeValidationPayload(nextRequest.requestId(), overworld, List.of(first.blockPos())));
        check(afterReset.nodes().size() == state.nodes().size() - 1 && afterReset.markedKey() == null,
                "a matching reply after reset still performs normal cleanup");

        session = new NodeValidationSession();
        request = pollValidation(session, List.of(first), overworld, node -> true);
        examined[0] = 0;
        boolean waitsForTimeout = true;
        for (int tick = 0; tick < 99; tick++) {
            waitsForTimeout &= session.tick(List.of(first), overworld, node -> { examined[0]++; return true; }) == null;
        }
        check(waitsForTimeout && examined[0] == 0, "an outstanding request blocks extra polling for ninety-nine ticks");
        nextRequest = session.tick(List.of(first), overworld, node -> { examined[0]++; return true; });
        check(nextRequest != null && nextRequest.requestId() > request.requestId() && examined[0] == 1,
                "a lost response times out after one hundred ticks and validation retries");
        check(session.apply(state, new NodeValidationPayload(request.requestId(), overworld, List.of(first.blockPos()))) == state,
                "a timeout never deletes records and its late response is ignored");
        check(session.apply(state, new NodeValidationPayload(nextRequest.requestId(), overworld, List.of())) == state,
                "a healthy retry confirms the retained record without changing navigation");
        check(pollValidation(session, List.of(first), overworld, node -> true) != null,
                "periodic checks continue after a timed-out request and successful retry");
        validationBatchChecks(overworld);
    }

    private static void validationBatchChecks(ResourceLocation dimension) {
        List<NodeRecord> nodes = new ArrayList<>();
        for (int index = 0; index < 130; index++) nodes.add(validationNode(dimension, index));
        NodeValidationSession session = new NodeValidationSession();
        Set<BlockPos> visited = new HashSet<>();
        var state = new NodeCache.State(1, nodes, null);
        List<NodeValidationPayload> requests = new ArrayList<>();
        for (int batch = 0; batch < 3; batch++) {
            NodeValidationPayload request = pollValidation(session, nodes, dimension, node -> true);
            check(request != null && request.positions().size() == 64
                            && new HashSet<>(request.positions()).size() == 64,
                    "loaded record batch " + batch + " is bounded to sixty-four distinct positions");
            requests.add(request);
            visited.addAll(request.positions());
            check(session.apply(state, new NodeValidationPayload(request.requestId(), dimension, List.of())) == state,
                    "a healthy polling batch does not replace stored records");
        }
        check(requests.getFirst().positions().getFirst().equals(nodes.getFirst().blockPos())
                        && requests.get(1).positions().getFirst().equals(nodes.get(64).blockPos())
                        && requests.get(2).positions().getFirst().equals(nodes.get(128).blockPos()),
                "batch polling resumes at the next record instead of restarting the list");
        check(visited.size() == nodes.size(), "round-robin polling eventually checks every loaded recorded node");

        nodes = new ArrayList<>();
        for (int index = 0; index < 600; index++) nodes.add(validationNode(dimension, index));
        session = new NodeValidationSession();
        int[] examined = {0};
        Predicate<NodeRecord> loaded = node -> { examined[0]++; return node.blockPos().getX() >= 512; };
        check(pollValidation(session, nodes, dimension, loaded) == null && examined[0] == 256,
                "one poll examines at most two hundred fifty-six unloaded records");
        check(pollValidation(session, nodes, dimension, loaded) == null && examined[0] == 512,
                "an unloaded prefix does not prevent the cursor from advancing");
        NodeValidationPayload request = pollValidation(session, nodes, dimension, loaded);
        check(request != null && request.positions().size() == 64
                        && request.positions().getFirst().equals(nodes.get(512).blockPos()) && examined[0] == 576,
                "loaded records after a large unloaded prefix are eventually requested within the batch limit");
        state = new NodeCache.State(1, nodes, null);
        session.apply(state, new NodeValidationPayload(request.requestId(), dimension, List.of()));
        request = pollValidation(session, nodes, dimension, loaded);
        check(request != null && request.positions().size() == 24
                        && request.positions().getFirst().equals(nodes.get(576).blockPos()) && examined[0] == 832,
                "a partial final batch wraps safely while keeping the per-poll inspection budget");
    }

    private static NodeRecord validationNode(ResourceLocation dimension, int x) {
        return new NodeRecord(dimension.toString(), new BlockPos(x, 64, -x).asLong(),
                "normal", "", Map.of("thaumaturge:aer", x), 1L);
    }

    private static NodeValidationPayload pollValidation(NodeValidationSession session, List<NodeRecord> nodes,
                                                         ResourceLocation dimension, Predicate<NodeRecord> loaded) {
        for (int tick = 0; tick < 20; tick++) {
            NodeValidationPayload request = session.tick(nodes, dimension, loaded);
            if (request != null) return request;
        }
        return null;
    }

    private static void validationPacketChecks() {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("example", "custom_world");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (List<BlockPos> positions : List.of(List.<BlockPos>of(),
                    List.of(new BlockPos(-300000, -60, 12345)),
                    java.util.stream.IntStream.range(0, 64).mapToObj(index -> new BlockPos(index, 64, -index)).toList())) {
                buffer.clear();
                var payload = new NodeValidationPayload(Long.MAX_VALUE, dimension, positions);
                NodeValidationPayload.STREAM_CODEC.encode(buffer, payload);
                check(payload.equals(NodeValidationPayload.STREAM_CODEC.decode(buffer)) && buffer.readableBytes() == 0,
                        "validation packet round trip preserves a list containing " + positions.size() + " positions");
            }
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 64, 2);
            List<BlockPos> source = new ArrayList<>(List.of(mutable));
            var copied = new NodeValidationPayload(1, dimension, source);
            mutable.set(9, 80, 9);
            source.clear();
            check(copied.positions().equals(List.of(new BlockPos(1, 64, 2))),
                    "validation payload freezes both the source list and mutable positions");
            for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
                try {
                    new NodeValidationPayload(id, dimension, List.of());
                    throw new AssertionError("Invalid validation request ID accepted: " + id);
                } catch (IllegalArgumentException expected) { checks++; }
            }
            try {
                new NodeValidationPayload(1, dimension, java.util.Collections.nCopies(65, BlockPos.ZERO));
                throw new AssertionError("Oversized validation request accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            for (long[] malformed : new long[][]{{0, 0}, {-1, 0}, {Long.MIN_VALUE, 0},
                    {1, -1}, {1, Integer.MIN_VALUE}, {1, 65}, {1, Integer.MAX_VALUE}}) {
                buffer.clear();
                buffer.writeVarLong(malformed[0]);
                ResourceLocation.STREAM_CODEC.encode(buffer, dimension);
                buffer.writeVarInt((int) malformed[1]);
                try {
                    NodeValidationPayload.STREAM_CODEC.decode(buffer);
                    throw new AssertionError("Malformed validation packet accepted: ID=" + malformed[0] + ", count=" + malformed[1]);
                } catch (DecoderException expected) { checks++; }
            }
        } finally { buffer.release(); }
    }

    private static void scrollChecks() {
        SmoothScroll scroll = new SmoothScroll();
        near(scroll.position(), 0, "new scroller starts at the top");
        near(scroll.maximum(), 0, "new scroller has no scroll range");
        scroll.setMaximum(400);
        near(scroll.maximum(), 400, "content establishes the scroll range");
        scroll.scroll(120);
        near(scroll.position(), 0, "wheel input queues motion instead of jumping");
        scroll.advance(1.0 / 60);
        check(scroll.position() > 0 && scroll.position() < 120, "first frame moves gradually toward the wheel target");
        boolean monotonic = true;
        boolean bounded = true;
        for (int frame = 0; frame < 120; frame++) {
            double previous = scroll.position();
            scroll.advance(1.0 / 60);
            monotonic &= scroll.position() >= previous;
            bounded &= scroll.position() >= 0 && scroll.position() <= 120;
        }
        check(monotonic, "positive wheel motion settles without bouncing backward");
        check(bounded, "the spring does not overshoot a stationary target");
        near(scroll.position(), 120, "wheel motion settles exactly at the requested offset");
        scroll.scroll(-80);
        scroll.advance(1.0 / 60);
        check(scroll.position() < 120 && scroll.position() > 40, "reversed wheel input starts a smooth upward motion");
        scroll.advance(2);
        near(scroll.position(), 40, "reversed motion settles at the accumulated target");
        scroll.scroll(Double.MAX_VALUE);
        scroll.advance(2);
        near(scroll.position(), 400, "large downward wheel input clamps at the bottom");
        scroll.scroll(Double.MAX_VALUE);
        scroll.advance(2);
        near(scroll.position(), 400, "repeated large wheel input remains finite and bounded");
        scroll.scroll(-Double.MAX_VALUE);
        scroll.advance(2);
        near(scroll.position(), 0, "large upward wheel input clamps at the top");

        scroll.setMaximum(100);
        scroll.scroll(90);
        scroll.advance(0.05);
        scroll.setMaximum(10);
        near(scroll.maximum(), 10, "filtering can reduce the content bounds during motion");
        check(scroll.position() >= 0 && scroll.position() <= 10, "reducing bounds immediately clamps the visible offset");
        scroll.advance(2);
        near(scroll.position(), 10, "reducing bounds also clamps the pending wheel target");
        scroll.setMaximum(200);
        scroll.jumpTo(150);
        scroll.scroll(40);
        scroll.advance(0.03);
        scroll.setMaximum(100);
        near(scroll.position(), 100, "resizing clamps an offset beyond the new bottom");
        scroll.advance(2);
        near(scroll.position(), 100, "resizing removes outward momentum at the new bottom");
        scroll.setMaximum(0);
        scroll.scroll(100);
        scroll.advance(2);
        check(scroll.position() == 0 && scroll.maximum() == 0, "short or empty content never scrolls");
        scroll.setMaximum(-10);
        near(scroll.maximum(), 0, "negative content overflow becomes an empty range");

        double reference = scrollAfterFrames(20, false);
        near(scrollAfterFrames(60, false), reference, "smooth offset agrees at 20 and 60 FPS");
        near(scrollAfterFrames(144, false), reference, "smooth offset agrees at 20 and 144 FPS");
        double reversed = scrollAfterFrames(20, true);
        near(scrollAfterFrames(60, true), reversed, "mid-motion reversal agrees at 20 and 60 FPS");
        near(scrollAfterFrames(144, true), reversed, "mid-motion reversal agrees at 20 and 144 FPS");

        scroll.setMaximum(100);
        scroll.scroll(100);
        scroll.advance(0.05);
        scroll.jumpTo(75);
        near(scroll.position(), 75, "dragging positions the scrollbar immediately");
        scroll.advance(1);
        near(scroll.position(), 75, "dragging cancels queued wheel motion and momentum");
        scroll.jumpTo(500);
        near(scroll.position(), 100, "dragging cannot leave the bottom of the range");
        scroll.jumpTo(-500);
        near(scroll.position(), 0, "dragging cannot leave the top of the range");
        scroll.scroll(80);
        scroll.advance(0.05);
        scroll.reset();
        check(scroll.position() == 0 && scroll.maximum() == 100, "reset returns to the top and preserves content bounds");
        scroll.advance(1);
        near(scroll.position(), 0, "reset removes pending target and velocity");
        scroll.jumpTo(50);
        scroll.scroll(Double.NaN);
        scroll.scroll(Double.POSITIVE_INFINITY);
        scroll.advance(Double.NaN);
        scroll.advance(Double.POSITIVE_INFINITY);
        scroll.advance(-1);
        scroll.advance(0);
        near(scroll.position(), 50, "invalid input and elapsed times cannot poison the animation");
        scroll.setMaximum(Double.NaN);
        check(scroll.position() == 0 && scroll.maximum() == 0, "invalid content bounds normalize safely");
        scroll.setMaximum(Double.POSITIVE_INFINITY);
        near(scroll.maximum(), 0, "infinite content bounds cannot escape finite coordinates");
        scroll.setMaximum(100);
        scroll.jumpTo(Double.NaN);
        near(scroll.position(), 0, "invalid direct positioning normalizes safely");
        scroll.scroll(90);
        scroll.advance(Double.MAX_VALUE);
        near(scroll.position(), 90, "a long rendering suspension settles without numerical overflow");
    }

    private static double scrollAfterFrames(int framesPerSecond, boolean reverse) {
        SmoothScroll scroll = new SmoothScroll();
        scroll.setMaximum(400);
        scroll.scroll(280);
        for (int frame = 0; frame < framesPerSecond / 2; frame++) {
            if (reverse && frame == framesPerSecond / 4) scroll.scroll(-180);
            scroll.advance(1.0 / framesPerSecond);
        }
        return scroll.position();
    }

    private static void arrivalChecks() {
        String dimension = "minecraft:overworld";
        NodeRecord node = new NodeRecord(dimension, new BlockPos(10, 64, -20).asLong(),
                "normal", "", Map.of("thaumaturge:aer", 10), 1L);
        check(NodeTrackerClient.hasArrived(node, dimension, 10.5, 64.5, -19.5),
                "being at the node center completes navigation");
        check(NodeTrackerClient.hasArrived(node, dimension, 12.5, 64.5, -19.5),
                "exactly two blocks from the node center completes navigation");
        check(!NodeTrackerClient.hasArrived(node, dimension, Math.nextUp(12.5), 64.5, -19.5),
                "unrounded distance just outside two blocks does not complete navigation");
        check(NodeTrackerClient.hasArrived(node, dimension, 11.5, 65.5, -18.5),
                "a three-dimensional diagonal within two blocks completes navigation");
        check(!NodeTrackerClient.hasArrived(node, dimension, 12.0, 66.0, -19.5),
                "diagonal axis offsets below two blocks cannot bypass the spherical radius");
        check(!NodeTrackerClient.hasArrived(node, dimension, 10.5, 67.5, -19.5),
                "matching horizontal coordinates cannot complete navigation from too far above");
        check(NodeTrackerClient.hasArrived(node, dimension, 10.5, 66.5, -19.5),
                "the exact two-block vertical boundary completes navigation");
        NodeRecord negative = new NodeRecord(dimension, new BlockPos(-12, -5, -9).asLong(),
                "normal", "", Map.of(), 1L);
        check(NodeTrackerClient.hasArrived(negative, dimension, -13.5, -4.5, -8.5),
                "negative block coordinates use the same centered arrival boundary");
        check(!NodeTrackerClient.hasArrived(node, "minecraft:the_nether", 10.5, 64.5, -19.5),
                "identical coordinates in another dimension do not complete navigation");
        check(!NodeTrackerClient.hasArrived(null, dimension, 10.5, 64.5, -19.5),
                "a missing destination does not count as an arrival");
    }
    private static void sortingChecks() {
        List<String> aspects = List.of("thaumaturge:aer", "addon:praecantatio");
        check(NodeFilters.nextSort("distance", aspects, 1).equals(aspects.getFirst()), "left sort advances from distance");
        check(NodeFilters.nextSort("distance", aspects, -1).equals(aspects.getLast()), "right sort wraps to last aspect");
        check(NodeFilters.nextSort(aspects.getFirst(), aspects, -1).equals("distance"), "right sort returns to distance");
        check(NodeFilters.nextSort(aspects.getLast(), aspects, 1).equals("distance"), "left sort wraps to distance");
        check(NodeFilters.nextSort(aspects.getLast(), aspects, -1).equals(aspects.getFirst()), "right sort returns to previous aspect");
        check(NodeFilters.nextSort("distance", List.of(), 1).equals("distance")
                        && NodeFilters.nextSort("distance", List.of(), -1).equals("distance"), "empty palette sorts safely in either direction");
        check(NodeFilters.nextSort("removed:aspect", aspects, -1).equals(aspects.getLast()), "removed sort choice safely falls back before cycling");
    }
    private static void buttonChecks() {
        int[] clicks = new int[2];
        NavigatorButton sort = new NavigatorButton(0, 0, 100, 18, Component.literal("sort"), false, () -> clicks[0]++)
                .withSecondaryAction(() -> clicks[1]++);
        int left = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        int right = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        int middle = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        check(sort.isValidClickButton(left) && sort.isValidClickButton(right), "sort button accepts both left and right clicks");
        check(!sort.isValidClickButton(middle), "sort button ignores middle clicks");
        sort.onClick(5, 5, left);
        check(clicks[0] == 1 && clicks[1] == 0, "left click dispatches only forward action");
        sort.onClick(5, 5, right);
        check(clicks[0] == 1 && clicks[1] == 1, "right click dispatches only reverse action");
        NavigatorButton ordinary = new NavigatorButton(0, 0, 100, 18, Component.literal("mark"), false, () -> clicks[0]++);
        check(ordinary.isValidClickButton(left) && !ordinary.isValidClickButton(right), "ordinary buttons retain left-click-only behavior");
        ordinary.setViewport(0, 8, 100, 18);
        check(!ordinary.isMouseOver(5, 5) && ordinary.isMouseOver(5, 10),
                "partially clipped native button only accepts visible hover positions");
        check(!ordinary.mouseClicked(5, 5, left),
                "native button outside the scroll viewport cannot fire a click");
        AspectFilterButton aspect = new AspectFilterButton(0, 0, 34, 29, Component.literal("air"),
                false, "thaumaturge:aer", () -> clicks[0]++);
        aspect.setViewport(0, 10, 34, 20);
        check(!aspect.isMouseOver(5, 5) && aspect.isMouseOver(5, 15) && !aspect.isMouseOver(5, 25),
                "aspect hit testing stays within both clipped edges");
        check(!aspect.mouseClicked(5, 25, left),
                "clipped aspect cannot toggle a filter through the controls below it");
    }
    private static void near(double actual, double expected, String context) { check(Math.abs(actual - expected) < 0.000001, context); }
    private static void layoutChecks() {
        for (int width : new int[]{320, 427, 854, 1280}) {
            for (int height : new int[]{180, 240, 360, 720}) {
                NavigatorLayout layout = NavigatorLayout.of(width, height);
                String size = width + "x" + height;
                check(layout.rows() > 0 && layout.rowHeight() > 0 && layout.listTop() >= 0
                                && layout.listViewportHeight() > 0 && layout.listBottom() <= height
                                && layout.doneY() + 20 == layout.listBottom(),
                        "list geometry is positive at " + size);
                check(layout.listTop() + layout.rows() * layout.rowHeight() <= layout.footerY(),
                        "list rows do not overlap footer at " + size);
                check(layout.controlsTop() + 44 + 4 <= layout.doneY(),
                        "sidebar controls stay clear of the bottom-aligned Done button at " + size);
                check(layout.left() >= 0 && layout.listWidth() > 0
                                && layout.sidebarX() + layout.sidebarWidth() <= width,
                        "both columns fit within the screen at " + size);
                check(layout.sidebarWidth() > 0 && layout.sidebarX() > layout.left() + layout.listWidth(),
                        "left list and right sidebar remain separate at " + size);
                check(layout.aspectColumns() > 0 && layout.aspectRows() > 0 && layout.aspectViewportHeight() > 0
                                && layout.aspectViewportHeight() == layout.aspectRows() * 32
                                && layout.aspectBottom() == layout.gridTop() + layout.aspectViewportHeight(),
                        "aspect viewport has the full positive grid height at " + size);
                check(layout.aspectCellWidth() > 0
                                && layout.aspectColumns() * layout.aspectCellWidth() <= layout.sidebarWidth() - 8
                                && layout.listContentRight() == layout.left() + layout.listWidth() - 8,
                        "both viewports reserve space for their scrollbars at " + size);
            }
        }
        NavigatorLayout wide = NavigatorLayout.of(854, 480, 11);
        check(wide.sidebarX() + wide.sidebarWidth() - wide.left() <= 720
                        && wide.left() > 12 && wide.titleY() > 12 && wide.footerY() + 20 < 480,
                "wide navigator is grouped centrally instead of hugging screen edges");
        check(wide.rowHeight() == 60, "wide node rows use reduced vertical spacing");
        for (int count : new int[]{0, 1, 5, 11, 70}) {
            NavigatorLayout layout = NavigatorLayout.of(854, 480, count);
            int needed = Math.max(1, (count + layout.aspectColumns() - 1) / layout.aspectColumns());
            check(layout.aspectRows() == Math.min(4, needed), "palette uses only needed rows for " + count + " aspects");
            check(layout.controlsTop() + 44 + 4 <= layout.doneY(), "adaptive palette controls fit for " + count + " aspects");
        }
    }
    private static void filterChecks() {
        Map<String, Integer> overworldAspects = Map.of("thaumaturge:aer", 10, "thaumaturge:praecantatio", 6,
                "thaumaturge:vitium", 0);
        Map<String, Integer> netherAspects = Map.of("thaumaturge:ignis", 20, "addon:aer", 3,
                "thaumaturge:praecantatio", 2);
        Map<String, Integer> customAspects = Map.of("addon:praecantatio", 8, "addon:vis/rare", 7,
                "thaumaturge:vitium", 1, "addon:empty", 0, "addon:invalid", -1);
        List<NodeRecord> nodes = List.of(
                new NodeRecord("minecraft:overworld", 1L, "normal", "", overworldAspects, 1L),
                new NodeRecord("minecraft:the_nether", 2L, "normal", "", netherAspects, 1L),
                new NodeRecord("addon:moon", 3L, "normal", "", customAspects, 1L));
        List<String> expected = List.of("addon:aer", "addon:praecantatio", "addon:vis/rare",
                "thaumaturge:aer", "thaumaturge:ignis", "thaumaturge:praecantatio", "thaumaturge:vitium");
        check(NodeFilters.collect(nodes.stream().map(node -> node.aspects).toList()).equals(expected),
                "filter icons include the sorted union of all positive aspects across every dimension");
        check(NodeFilters.collect(List.of(customAspects, overworldAspects, netherAspects)).equals(expected),
                "aspect order does not depend on node insertion order");
        check(NodeFilters.collect(List.of()).isEmpty(), "no records produce no aspect icons");
        check(NodeFilters.matches(overworldAspects, Set.of(), true), "empty AND filter shows all nodes");
        check(NodeFilters.matches(Map.of(), Set.of(), false), "empty OR filter includes nodes without aspects");
        Set<String> both = Set.of("thaumaturge:aer", "thaumaturge:praecantatio");
        check(NodeFilters.matches(overworldAspects, both, true), "AND filter accepts every selected aspect");
        check(!NodeFilters.matches(netherAspects, both, true), "AND filter rejects a missing selected aspect");
        check(NodeFilters.matches(netherAspects, both, false), "OR filter accepts any selected aspect");
        check(!NodeFilters.matches(customAspects, both, false), "OR filter rejects nodes with no exact matches");
        check(NodeFilters.matches(customAspects, Set.of("addon:vis/rare"), true),
                "compound namespaced aspect paths remain intact");
        check(!NodeFilters.matches(overworldAspects, Set.of("addon:aer"), false),
                "same aspect path in a different namespace does not match");
        check(!NodeFilters.matches(overworldAspects, Set.of("thaumaturge:vitium"), true),
                "zero amount is not a present aspect");
        check(!NodeFilters.matches(customAspects, Set.of("addon:invalid"), false),
                "negative amount is not a present aspect");
        check(NodeFilters.amount(overworldAspects, "thaumaturge:praecantatio") == 6,
                "sorting reads compound aspect amounts by full identifier");
        check(NodeFilters.amount(netherAspects, "thaumaturge:aer") == 0,
                "amount lookup preserves namespace separation");
        check(NodeFilters.matches(null, Set.of(), true)
                && !NodeFilters.matches(null, Set.of("thaumaturge:aer"), false),
                "absent aspect snapshots honor empty and selected filters");
    }
    private static void packetChecks() {
        var dimension = ResourceLocation.fromNamespaceAndPath("example", "custom_world");
        var node = new NodeScannedPayload(dimension, new BlockPos(-300000, -60, 12345), NodeType.PURE,
                NodeModifier.BRIGHT, List.of(new NodeScannedPayload.AspectValue(
                ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"), 127)), 1792843200000L);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            NodeScannedPayload.STREAM_CODEC.encode(buffer, node);
            check(node.equals(NodeScannedPayload.STREAM_CODEC.decode(buffer)), "node packet round trip preserves all fields");
            check(buffer.readableBytes() == 0, "decoder consumes entire packet");
            var profile = new TrackerProfilePayload(UUID.randomUUID(), UUID.randomUUID());
            TrackerProfilePayload.STREAM_CODEC.encode(buffer, profile);
            check(profile.equals(TrackerProfilePayload.STREAM_CODEC.decode(buffer)), "profile packet round trip");
            for (int mode = 0; mode < 4; mode++) {
                buffer.clear();
                ResourceLocation.STREAM_CODEC.encode(buffer, dimension);
                BlockPos.STREAM_CODEC.encode(buffer, BlockPos.ZERO);
                buffer.writeVarInt(mode == 0 ? 999 : 0);
                buffer.writeBoolean(false);
                buffer.writeVarInt(mode == 1 ? 257 : mode == 2 ? -1 : 1);
                ResourceLocation.STREAM_CODEC.encode(buffer, ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
                buffer.writeVarInt(-1);
                buffer.writeVarLong(0);
                try {
                    NodeScannedPayload.STREAM_CODEC.decode(buffer);
                    throw new AssertionError("Malformed node packet accepted: " + mode);
                } catch (DecoderException expected) {
                    checks++;
                }
            }
        } finally { buffer.release(); }
    }
    private static void check(boolean condition, String context) {
        checks++;
        if (!condition) throw new AssertionError(context);
    }
}
