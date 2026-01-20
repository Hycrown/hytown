package com.hytown.map;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hytown.HyTownAccess;
import com.hytown.data.ClaimStorage;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.util.ChunkUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides claim overlay visualization on the world map.
 * Implements WorldMapManager.MarkerProvider to integrate with Hytale's map system.
 *
 * This provider:
 * 1. Sends colored overlays for claimed chunks
 * 2. Adds markers at claim centers with owner names
 * 3. Uses unique colors per player for easy identification
 */
public class ClaimMapOverlayProvider implements WorldMapManager.MarkerProvider {

    private final ClaimStorage claimStorage;
    private final TownStorage townStorage;
    private final HytaleLogger logger;

    // Track which chunks we've already sent overlays for per player
    private final Map<UUID, Set<String>> sentOverlays = new ConcurrentHashMap<>();

    // View radius in chunks for sending overlays
    private static final int VIEW_RADIUS = 100;

    // Debug: only log once per player to avoid spam
    private final Map<UUID, Boolean> hasLoggedDebug = new ConcurrentHashMap<>();

    public ClaimMapOverlayProvider(ClaimStorage claimStorage, TownStorage townStorage, HytaleLogger logger) {
        this.claimStorage = claimStorage;
        this.townStorage = townStorage;
        this.logger = logger;
    }

    /**
     * Legacy constructor for backwards compatibility.
     */
    public ClaimMapOverlayProvider(ClaimStorage claimStorage, HytaleLogger logger) {
        this(claimStorage, null, logger);
    }

    @Override
    public void update(World world, GameplayConfig gameplayConfig,
                       WorldMapTracker tracker, int centerX, int centerY, int centerZ) {
        try {
            Player player = tracker.getPlayer();
            if (player == null) return;

            UUID playerId = player.getUuid();
            String worldName = world.getName();

            // Get player's chunk position
            int playerChunkX = ChunkUtil.toChunkX(centerX);
            int playerChunkZ = ChunkUtil.toChunkZ(centerZ);

            // Calculate view bounds
            int minChunkX = playerChunkX - VIEW_RADIUS;
            int maxChunkX = playerChunkX + VIEW_RADIUS;
            int minChunkZ = playerChunkZ - VIEW_RADIUS;
            int maxChunkZ = playerChunkZ + VIEW_RADIUS;

            // Get all personal claims in the visible area
            Map<String, UUID> worldClaims = new HashMap<>(claimStorage.getClaimedChunksInWorld(worldName));

            // Also add town claims to the map
            if (townStorage != null) {
                for (Town town : townStorage.getAllTowns()) {
                    for (String claimKey : town.getClaimKeys()) {
                        // claimKey format is "worldName:chunkX,chunkZ"
                        if (claimKey.startsWith(worldName + ":")) {
                            // Extract chunk coordinates from the key
                            String chunkPart = claimKey.substring(worldName.length() + 1);
                            // Add town owner UUID for this claim
                            worldClaims.put(chunkPart, town.getMayorId());
                        }
                    }
                }
            }

            // Debug logging (once per player)
            if (logger != null && !hasLoggedDebug.getOrDefault(playerId, false)) {
                hasLoggedDebug.put(playerId, true);
                logger.atWarning().log("[ClaimMap] DEBUG: centerX=%d, centerY=%d, centerZ=%d, playerChunk=(%d,%d), worldClaims=%d",
                    centerX, centerY, centerZ, playerChunkX, playerChunkZ, worldClaims.size());
            }

            if (worldClaims.isEmpty()) {
                return;
            }

            // Collect claims that need markers
            Map<UUID, List<int[]>> ownerClaimChunks = new HashMap<>();
            int foundClaims = 0;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    String chunkKey = ChunkUtil.chunkKey(cx, cz);
                    UUID ownerId = worldClaims.get(chunkKey);

                    if (ownerId != null) {
                        foundClaims++;
                        // Track for marker placement
                        ownerClaimChunks.computeIfAbsent(ownerId, k -> new ArrayList<>())
                                       .add(new int[]{cx, cz});
                    }
                }
            }

            if (logger != null && foundClaims > 0 && !hasLoggedDebug.getOrDefault(playerId, false)) {
                logger.atWarning().log("[ClaimMap] DEBUG: Found %d claims in view for %d owners", foundClaims, ownerClaimChunks.size());
            }

            // Create center markers for each owner's claims in view
            for (Map.Entry<UUID, List<int[]>> entry : ownerClaimChunks.entrySet()) {
                UUID ownerId = entry.getKey();
                List<int[]> chunks = entry.getValue();

                // Find center of this owner's visible claims
                int sumX = 0, sumZ = 0;
                for (int[] chunk : chunks) {
                    sumX += chunk[0];
                    sumZ += chunk[1];
                }
                int centerChunkX = sumX / chunks.size();
                int centerChunkZ = sumZ / chunks.size();

                // Get the display name using HyTownAccess (handles both town and personal claims)
                // Use one of the chunks to look up the proper name
                int[] firstChunk = chunks.get(0);
                String ownerName = HyTownAccess.getOwnerName(worldName, firstChunk[0], firstChunk[1]);
                if (ownerName == null) {
                    ownerName = claimStorage.getPlayerName(ownerId);
                }

                // Convert to block coordinates (center of chunk)
                double markerX = (centerChunkX * 16) + 8;
                double markerZ = (centerChunkZ * 16) + 8;

                // Create marker ID unique to this owner in this area
                String markerId = "claim_" + ownerId.toString().substring(0, 8) + "_" + centerChunkX + "_" + centerChunkZ;

                // Determine display name - check if it's a town or player's own claim
                String displayName;
                // Check if this is a town claim (ownerName won't contain "'s" for towns)
                Town town = townStorage != null ? townStorage.getTownByClaimKey(worldName + ":" + firstChunk[0] + "," + firstChunk[1]) : null;
                if (town != null) {
                    // It's a town claim
                    if (town.isMember(playerId)) {
                        displayName = "Your Town: " + ownerName;
                    } else {
                        displayName = "Town: " + ownerName;
                    }
                } else if (ownerId.equals(playerId)) {
                    displayName = "Your Claim";
                } else {
                    displayName = ownerName + "'s Claim";
                }

                // Use the factory-based trySendMarker like built-in providers do
                // Parameters: centerX, centerY, centerZ, position, viewRadius, markerId, markerName, data, factory
                com.hypixel.hytale.math.vector.Vector3d markerPos = new com.hypixel.hytale.math.vector.Vector3d(markerX, centerY, markerZ);

                // viewRadius controls how far the marker is visible - use a large value
                float viewRadius = 100.0f;

                final String finalDisplayName = displayName;
                tracker.trySendMarker(
                    centerX, centerY, centerZ,
                    markerPos,
                    viewRadius,
                    markerId,
                    finalDisplayName,
                    markerPos,
                    (id, name, pos) -> {
                        // Create the Transform using the same pattern as SpawnMarkerProvider
                        com.hypixel.hytale.math.vector.Transform mathTransform =
                            new com.hypixel.hytale.math.vector.Transform(pos);
                        Transform protoTransform = com.hypixel.hytale.server.core.util.PositionUtil.toTransformPacket(mathTransform);

                        // Use "Spawn.png" as a known working marker icon, null for context menu
                        return new MapMarker(id, name, "Spawn.png", protoTransform, null);
                    }
                );
            }

            // Send corner markers for each claimed chunk to outline the boundaries
            for (Map.Entry<UUID, List<int[]>> entry : ownerClaimChunks.entrySet()) {
                UUID ownerId = entry.getKey();
                List<int[]> chunks = entry.getValue();

                for (int[] chunk : chunks) {
                    int cx = chunk[0];
                    int cz = chunk[1];

                    // Get the display name using HyTownAccess (handles both town and personal claims)
                    String ownerName = HyTownAccess.getOwnerName(worldName, cx, cz);
                    if (ownerName == null) {
                        ownerName = claimStorage.getPlayerName(ownerId);
                    }

                    // Determine corner display name based on claim type
                    String cornerDisplayName;
                    Town town = townStorage != null ? townStorage.getTownByClaimKey(worldName + ":" + cx + "," + cz) : null;
                    if (town != null) {
                        cornerDisplayName = "Town: " + ownerName;
                    } else if (ownerId.equals(playerId)) {
                        cornerDisplayName = "Your Claim";
                    } else {
                        cornerDisplayName = ownerName + "'s Claim";
                    }

                    // Create markers at all 4 corners of this chunk
                    // Chunk is 16 blocks, so corners are at (cx*16, cz*16) to (cx*16+15, cz*16+15)
                    int blockX = cx * 16;
                    int blockZ = cz * 16;

                    // Send corner markers
                    String[] cornerNames = {"NW", "NE", "SW", "SE"};
                    int[][] corners = {
                        {blockX, blockZ},           // NW corner
                        {blockX + 15, blockZ},      // NE corner
                        {blockX, blockZ + 15},      // SW corner
                        {blockX + 15, blockZ + 15}  // SE corner
                    };

                    for (int i = 0; i < 4; i++) {
                        String cornerMarkerId = "claim_corner_" + cx + "_" + cz + "_" + cornerNames[i];

                        com.hypixel.hytale.math.vector.Vector3d cornerPos =
                            new com.hypixel.hytale.math.vector.Vector3d(corners[i][0], centerY, corners[i][1]);

                        float cornerViewRadius = 100.0f;

                        tracker.trySendMarker(
                            centerX, centerY, centerZ,
                            cornerPos,
                            cornerViewRadius,
                            cornerMarkerId,
                            cornerDisplayName,
                            cornerPos,
                            (id, name, pos) -> {
                                com.hypixel.hytale.math.vector.Transform mathTransform =
                                    new com.hypixel.hytale.math.vector.Transform(pos);
                                Transform protoTransform = com.hypixel.hytale.server.core.util.PositionUtil.toTransformPacket(mathTransform);
                                return new MapMarker(id, name, "Spawn.png", protoTransform, null);
                            }
                        );
                    }
                }
            }

        } catch (Exception e) {
            if (logger != null) {
                logger.atWarning().withCause(e).log("Error in ClaimMapOverlayProvider.update");
            }
        }
    }

    /**
     * Clears cached overlay data for a player (call on disconnect).
     */
    public void clearPlayerCache(UUID playerId) {
        sentOverlays.remove(playerId);
    }

    /**
     * Clears all cached overlay data.
     */
    public void clearAllCaches() {
        sentOverlays.clear();
    }

    /**
     * Invalidates cached overlays for specific chunks (call when claims change).
     */
    public void invalidateChunks(String world, int... chunkCoords) {
        for (int i = 0; i < chunkCoords.length; i += 2) {
            String chunkKey = ChunkUtil.chunkKey(chunkCoords[i], chunkCoords[i + 1]);
            String overlayKey = world + ":" + chunkKey;

            // Remove from all player caches so it gets resent
            for (Set<String> cache : sentOverlays.values()) {
                cache.remove(overlayKey);
            }
        }
    }

    /**
     * Invalidates all cached overlays for a world.
     */
    public void invalidateWorld(String world) {
        String prefix = world + ":";
        for (Set<String> cache : sentOverlays.values()) {
            cache.removeIf(key -> key.startsWith(prefix));
        }
    }
}
