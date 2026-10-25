package dev.thaumaturge.nodenavigator.network;

import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TrackerProfilePayload(UUID world, UUID player) implements CustomPacketPayload {
    public static final Type<TrackerProfilePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            ThaumaturgeNodeNavigator.MOD_ID, "tracker_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackerProfilePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeUUID(payload.world()); buffer.writeUUID(payload.player()); },
            buffer -> new TrackerProfilePayload(buffer.readUUID(), buffer.readUUID()));
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}