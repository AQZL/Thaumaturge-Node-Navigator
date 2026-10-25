package dev.thaumaturge.nodenavigator.network;

import com.leclowndu93150.thaumaturge.api.nodes.NodeModifier;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import dev.thaumaturge.nodenavigator.ThaumaturgeNodeNavigator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NodeScannedPayload(Identifier dimension, BlockPos position, NodeType nodeType,
                                NodeModifier modifier, List<AspectValue> aspects, long scannedAt) implements CustomPacketPayload {
    public static final Type<NodeScannedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ThaumaturgeNodeNavigator.MOD_ID, "node_scanned"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeScannedPayload> STREAM_CODEC =
            StreamCodec.of(NodeScannedPayload::write, NodeScannedPayload::read);

    public static void send(ServerPlayer player, NodeScannedPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void write(RegistryFriendlyByteBuf buffer, NodeScannedPayload payload) {
        Identifier.STREAM_CODEC.encode(buffer, payload.dimension());
        BlockPos.STREAM_CODEC.encode(buffer, payload.position());
        buffer.writeVarInt(payload.nodeType().ordinal());
        buffer.writeBoolean(payload.modifier() != null);
        if (payload.modifier() != null) {
            buffer.writeVarInt(payload.modifier().ordinal());
        }
        buffer.writeVarInt(payload.aspects().size());
        for (AspectValue value : payload.aspects()) {
            Identifier.STREAM_CODEC.encode(buffer, value.aspect());
            buffer.writeVarInt(value.amount());
        }
        buffer.writeVarLong(payload.scannedAt());
    }

    private static NodeScannedPayload read(RegistryFriendlyByteBuf buffer) {
        Identifier dimension = Identifier.STREAM_CODEC.decode(buffer);
        BlockPos position = BlockPos.STREAM_CODEC.decode(buffer);
        NodeType type = readEnum(buffer, NodeType.values());
        NodeModifier modifier = buffer.readBoolean() ? readEnum(buffer, NodeModifier.values()) : null;
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) throw new DecoderException("Invalid node aspect count: " + size);
        List<AspectValue> aspects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Identifier aspect = Identifier.STREAM_CODEC.decode(buffer);
            int amount = buffer.readVarInt();
            if (amount < 0) throw new DecoderException("Negative node aspect amount");
            aspects.add(new AspectValue(aspect, amount));
        }
        return new NodeScannedPayload(dimension, position, type, modifier, List.copyOf(aspects), buffer.readVarLong());
    }

    private static <E> E readEnum(RegistryFriendlyByteBuf buffer, E[] values) {
        int index = buffer.readVarInt();
        if (index < 0 || index >= values.length) throw new DecoderException("Invalid node enum value: " + index);
        return values[index];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record AspectValue(Identifier aspect, int amount) {}
}
