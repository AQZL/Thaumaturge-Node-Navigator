package dev.thaumaturge.nodenavigator;

import com.mojang.logging.LogUtils;
import dev.thaumaturge.nodenavigator.network.TrackerProfilePayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ThaumaturgeNodeNavigator.MOD_ID)
public final class TrackerServerEvents {
    private static final Map<MinecraftServer, UUID> WORLDS = new WeakHashMap<>();
    private TrackerServerEvents() {}
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID world = WORLDS.computeIfAbsent(player.level().getServer(), TrackerServerEvents::worldId);
            PacketDistributor.sendToPlayer(player, new TrackerProfilePayload(world, player.getUUID()));
        }
    }
    private static UUID worldId(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data/thaumaturge_node_navigator/world-id.txt");
        try {
            if (Files.exists(path)) return UUID.fromString(Files.readString(path).trim());
            UUID id = UUID.randomUUID();
            Files.createDirectories(path.getParent());
            Files.writeString(path, id.toString(), StandardOpenOption.CREATE_NEW);
            return id;
        } catch (IOException | IllegalArgumentException failure) {
            LogUtils.getLogger().error("Cannot read/write navigator world ID at {}; using a temporary profile", path, failure);
            return UUID.randomUUID();
        }
    }
}