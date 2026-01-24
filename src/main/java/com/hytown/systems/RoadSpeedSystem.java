package com.hytown.systems;

import com.hytown.config.PluginConfig;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.util.ChunkUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking system that applies speed boost when players are on town roads.
 * Checks every tick and applies/removes the speed multiplier as needed.
 */
public class RoadSpeedSystem extends EntityTickingSystem<EntityStore> {

    private final TownStorage townStorage;
    private final PluginConfig config;

    // Track which players currently have road speed boost
    private final Map<UUID, Boolean> playerOnRoad = new ConcurrentHashMap<>();
    // Track original base speed for restoration
    private final Map<UUID, Float> playerOriginalSpeed = new ConcurrentHashMap<>();

    public RoadSpeedSystem(TownStorage townStorage, PluginConfig config) {
        this.townStorage = townStorage;
        this.config = config;
    }

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) return;

        UUID playerId = playerRef.getUuid();

        // Get player's current position and convert to chunk coordinates
        double posX = playerRef.getTransform().getPosition().getX();
        double posZ = playerRef.getTransform().getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = player.getWorld().getName();

        // Check if current chunk is a road
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);
        boolean isOnRoad = town != null && town.isRoadClaim(claimKey);

        // Get current state
        Boolean wasOnRoad = playerOnRoad.get(playerId);

        // State changed?
        if (wasOnRoad == null || wasOnRoad != isOnRoad) {
            playerOnRoad.put(playerId, isOnRoad);

            // Get MovementManager component
            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager != null) {
                MovementSettings settings = movementManager.getSettings();
                if (settings != null) {
                    if (isOnRoad) {
                        // Entering road - store original speed and apply boost
                        if (!playerOriginalSpeed.containsKey(playerId)) {
                            playerOriginalSpeed.put(playerId, settings.baseSpeed);
                        }
                        float originalSpeed = playerOriginalSpeed.get(playerId);
                        float multiplier = (float) config.getRoadSpeedMultiplier();
                        settings.baseSpeed = originalSpeed * multiplier;
                        movementManager.update(playerRef.getPacketHandler());
                    } else {
                        // Leaving road - restore original speed
                        Float originalSpeed = playerOriginalSpeed.get(playerId);
                        if (originalSpeed != null) {
                            settings.baseSpeed = originalSpeed;
                            movementManager.update(playerRef.getPacketHandler());
                        }
                    }
                }
            }
        }
    }

    /**
     * Remove player from tracking when they disconnect.
     */
    public void removePlayer(UUID playerId) {
        playerOnRoad.remove(playerId);
        playerOriginalSpeed.remove(playerId);
    }

    /**
     * Force reset speed for a player (called on death, teleport, etc.)
     */
    public void resetPlayerSpeed(UUID playerId, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        // Clear state so next tick will re-evaluate
        playerOnRoad.remove(playerId);

        // Restore original speed if we have it stored
        Float originalSpeed = playerOriginalSpeed.remove(playerId);
        if (originalSpeed != null && store != null && ref != null && playerRef != null) {
            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager != null) {
                MovementSettings settings = movementManager.getSettings();
                if (settings != null) {
                    settings.baseSpeed = originalSpeed;
                    movementManager.update(playerRef.getPacketHandler());
                }
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
