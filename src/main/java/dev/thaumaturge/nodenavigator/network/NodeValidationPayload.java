package dev.thaumaturge.nodenavigator.network;

import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NodeValidationPayload(long requestId, Identifier dimension, List<BlockPos> positions)
        implements CustomPacketPayload {
    public static final int MAX_POSITIONS = 64;
    public static final Type<NodeValidationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ThaumaturgeNodeNavigator.MOD_ID, "validate_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NodeValidationPayload> STREAM_CODEC =
            StreamCodec.of(NodeValidationPayload::write, NodeValidationPayload::read);

    public NodeValidationPayload {
        if (requestId <= 0 || positions.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("Invalid node validation request");
        }
        positions = positions.stream().map(BlockPos::immutable).toList();
    }

    private static void write(RegistryFriendlyByteBuf buffer, NodeValidationPayload payload) {
        buffer.writeVarLong(payload.requestId());
        Identifier.STREAM_CODEC.encode(buffer, payload.dimension());
        buffer.writeVarInt(payload.positions().size());
        for (BlockPos position : payload.positions()) BlockPos.STREAM_CODEC.encode(buffer, position);
    }

    private static NodeValidationPayload read(RegistryFriendlyByteBuf buffer) {
        long requestId = buffer.readVarLong();
        Identifier dimension = Identifier.STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        if (requestId <= 0 || count < 0 || count > MAX_POSITIONS) {
            throw new DecoderException("Invalid node validation payload");
        }
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add(BlockPos.STREAM_CODEC.decode(buffer));
        return new NodeValidationPayload(requestId, dimension, positions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
