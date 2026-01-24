package com.hytown.systems;

import com.hytown.data.ClaimStorage;
import com.hytown.data.PlayerClaims;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.managers.TownBorderManager;
import com.hytown.util.ChunkUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionUpdate;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking system that renders visual borders for players who have them enabled.
 * Uses Hytale's BuilderToolSelectionUpdate packet to show selection-style borders.
 *
 * Two modes:
 * - Chunk borders: Shows the boundary of the current chunk
 * - Town borders: Shows the bounding box of all town claims for the player's town
 */
public class TownBorderRenderSystem extends EntityTickingSystem<EntityStore> {

    // Y bounds for visualization (full world height)
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    // Hytale uses 32-block chunks
    private static final int CHUNK_SIZE = 32;

    // Throttle updates to every 10 ticks (~0.5 seconds) to reduce packet spam
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private final TownBorderManager borderManager;
    private final TownStorage townStorage;
    private final ClaimStorage claimStorage;

    // Track last rendered position/state to avoid redundant packets
    private final Map<UUID, LastRenderState> lastRenderState = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public TownBorderRenderSystem(TownBorderManager borderManager, TownStorage townStorage, ClaimStorage claimStorage) {
        this.borderManager = borderManager;
        this.townStorage = townStorage;
        this.claimStorage = claimStorage;
    }

    private record LastRenderState(int chunkX, int chunkZ, boolean chunkBorders, boolean townBorders, boolean plotBorders, String townName, int plotCount) {}

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) return;

        UUID playerId = playerRef.getUuid();
        boolean chunkBordersEnabled = borderManager.isChunkBorderEnabled(playerId);
        boolean townBordersEnabled = borderManager.isTownBorderEnabled(playerId);
        boolean plotBordersEnabled = borderManager.isPlotBorderEnabled(playerId);

        // If neither enabled, clear any existing visualization
        if (!chunkBordersEnabled && !townBordersEnabled && !plotBordersEnabled) {
            LastRenderState last = lastRenderState.remove(playerId);
            if (last != null && (last.chunkBorders || last.townBorders || last.plotBorders)) {
                clearVisualization(playerRef);
            }
            return;
        }

        // Get player's current chunk
        double posX = playerRef.getTransform().getPosition().getX();
        double posZ = playerRef.getTransform().getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = player.getWorld().getName();

        // Get player's town (if any) for town borders
        Town town = townStorage.getPlayerTown(playerId);
        String townName = town != null ? town.getName() : null;
        PlayerClaims playerClaims = claimStorage.getPlayerClaims(playerId);
        int plotCount = playerClaims != null ? playerClaims.getClaimCount() : 0;

        // Check if state changed
        LastRenderState last = lastRenderState.get(playerId);
        boolean stateChanged = last == null ||
                last.chunkX != chunkX ||
                last.chunkZ != chunkZ ||
                last.chunkBorders != chunkBordersEnabled ||
                last.townBorders != townBordersEnabled ||
                last.plotBorders != plotBordersEnabled ||
                !java.util.Objects.equals(last.townName, townName) ||
                last.plotCount != plotCount;

        if (!stateChanged) {
            return; // No change, don't send packet
        }

        // Update last state
        lastRenderState.put(playerId, new LastRenderState(chunkX, chunkZ, chunkBordersEnabled, townBordersEnabled, plotBordersEnabled, townName, plotCount));

        // Determine what to render
        // Priority: Plot borders > Town borders > Chunk borders
        if (plotBordersEnabled && playerClaims != null && plotCount > 0) {
            renderPlotBorders(playerRef, playerClaims, worldName);
        } else if (townBordersEnabled && town != null) {
            renderTownBorders(playerRef, town, worldName);
        } else if (chunkBordersEnabled) {
            renderChunkBorders(playerRef, chunkX, chunkZ);
        }
    }

    /**
     * Renders the current chunk boundary.
     */
    private void renderChunkBorders(PlayerRef playerRef, int chunkX, int chunkZ) {
        int minX = chunkX * CHUNK_SIZE;
        int maxX = chunkX * CHUNK_SIZE + CHUNK_SIZE - 1;
        int minZ = chunkZ * CHUNK_SIZE;
        int maxZ = chunkZ * CHUNK_SIZE + CHUNK_SIZE - 1;

        sendSelectionPacket(playerRef, minX, MIN_Y, minZ, maxX, MAX_Y, maxZ);
    }

    /**
     * Renders the bounding box of all town claims in the current world.
     */
    private void renderTownBorders(PlayerRef playerRef, Town town, String worldName) {
        // Calculate bounding box of all town claims in this world
        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        int claimCount = 0;

        for (String claimKey : town.getClaimKeys()) {
            // Parse claim key: "world:chunkX,chunkZ"
            if (!claimKey.startsWith(worldName + ":")) {
                continue; // Different world
            }

            int[] coords = Town.parseClaimCoords(claimKey);
            if (coords == null) continue;

            int cx = coords[0];
            int cz = coords[1];
            minChunkX = Math.min(minChunkX, cx);
            maxChunkX = Math.max(maxChunkX, cx);
            minChunkZ = Math.min(minChunkZ, cz);
            maxChunkZ = Math.max(maxChunkZ, cz);
            claimCount++;
        }

        if (claimCount == 0) {
            clearVisualization(playerRef);
            return;
        }

        // Convert to block coordinates
        int minX = minChunkX * CHUNK_SIZE;
        int maxX = maxChunkX * CHUNK_SIZE + CHUNK_SIZE - 1;
        int minZ = minChunkZ * CHUNK_SIZE;
        int maxZ = maxChunkZ * CHUNK_SIZE + CHUNK_SIZE - 1;

        sendSelectionPacket(playerRef, minX, MIN_Y, minZ, maxX, MAX_Y, maxZ);
    }

    /**
     * Sends a BuilderToolSelectionUpdate packet to show the selection box.
     */

    /**
     * Renders the bounding box of all personal claims for a player in the current world.
     */
    private void renderPlotBorders(PlayerRef playerRef, PlayerClaims playerClaims, String worldName) {
        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        int claimCount = 0;

        for (var claim : playerClaims.getClaims()) {
            if (!claim.getWorld().equals(worldName)) {
                continue;
            }
            int cx = claim.getChunkX();
            int cz = claim.getChunkZ();
            minChunkX = Math.min(minChunkX, cx);
            maxChunkX = Math.max(maxChunkX, cx);
            minChunkZ = Math.min(minChunkZ, cz);
            maxChunkZ = Math.max(maxChunkZ, cz);
            claimCount++;
        }

        if (claimCount == 0) {
            clearVisualization(playerRef);
            return;
        }

        int minX = minChunkX * CHUNK_SIZE;
        int maxX = maxChunkX * CHUNK_SIZE + CHUNK_SIZE - 1;
        int minZ = minChunkZ * CHUNK_SIZE;
        int maxZ = maxChunkZ * CHUNK_SIZE + CHUNK_SIZE - 1;
        sendSelectionPacket(playerRef, minX, MIN_Y, minZ, maxX, MAX_Y, maxZ);
    }

    private void sendSelectionPacket(PlayerRef playerRef, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        try {
            BuilderToolSelectionUpdate packet = new BuilderToolSelectionUpdate(
                    minX, minY, minZ,
                    maxX, maxY, maxZ
            );
            playerRef.getPacketHandler().write(packet);
        } catch (Exception e) {
            // Silently ignore packet errors
        }
    }

    /**
     * Clears the selection visualization for a player.
     */
    private void clearVisualization(PlayerRef playerRef) {
        try {
            // Send a "zero" selection to clear
            BuilderToolSelectionUpdate packet = new BuilderToolSelectionUpdate(0, 0, 0, 0, 0, 0);
            playerRef.getPacketHandler().write(packet);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Cleans up render state when a player disconnects.
     */
    public void removePlayer(UUID playerId) {
        lastRenderState.remove(playerId);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
