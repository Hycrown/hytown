package com.hytown.api;

import com.hytown.data.*;
import com.hytown.managers.ClaimManager;
import com.hytown.util.ChunkUtil;

import java.util.*;

/**
 * Public API for HyTown that other plugins can use to query town and claim information.
 *
 * <p>This API provides methods to check:
 * <ul>
 *   <li>Whether a player is in wilderness, their town, another town, or a personal claim</li>
 *   <li>Town properties like PvP status, explosion settings, size, etc.</li>
 *   <li>Player membership and ranks within towns</li>
 *   <li>Build/destroy/use permissions at locations</li>
 *   <li>Town listings and discovery</li>
 * </ul>
 *
 * <p><strong>Events:</strong> HyTown fires events that other plugins can listen to:
 * <ul>
 *   <li>{@link com.hytown.events.TownCreateEvent} - When a town is created</li>
 *   <li>{@link com.hytown.events.TownDeleteEvent} - When a town is deleted</li>
 *   <li>{@link com.hytown.events.TownRenameEvent} - When a town is renamed</li>
 *   <li>{@link com.hytown.events.TownJoinEvent} - When a player joins a town</li>
 *   <li>{@link com.hytown.events.TownLeaveEvent} - When a player leaves a town (voluntary, kicked, or town deleted)</li>
 *   <li>{@link com.hytown.events.TownClaimEvent} - When a chunk is claimed for a town</li>
 *   <li>{@link com.hytown.events.TownUnclaimEvent} - When a chunk is unclaimed from a town</li>
 *   <li>{@link com.hytown.events.TownMayorChangeEvent} - When a town's mayor changes</li>
 * </ul>
 *
 * <p>To listen for events, use the EventRegistry in your plugin's setup():
 * <pre>{@code
 * // In your plugin's setup() method:
 * getEventRegistry().registerGlobal(TownRenameEvent.class, this::onTownRename);
 * getEventRegistry().registerGlobal(TownDeleteEvent.class, this::onTownDelete);
 *
 * private void onTownRename(TownRenameEvent event) {
 *     String oldName = event.getOldName();
 *     String newName = event.getNewName();
 *     // Update your plugin's references to use the new name
 * }
 *
 * private void onTownDelete(TownDeleteEvent event) {
 *     String townName = event.getTownName();
 *     Set<String> claimKeys = event.getClaimKeys();
 *     // Clean up any references to this town
 * }
 * }</pre>
 *
 * <p>Query API usage example:
 * <pre>{@code
 * HyTownAPI api = hyTownPlugin.getAPI();
 *
 * // Check if PvP is enabled where player is standing
 * boolean pvpOn = api.isPvpEnabledAtPlayer(player.getUuid(), world.getName(), playerX, playerZ);
 *
 * // Check if player is in their own town
 * boolean inOwnTown = api.isPlayerInOwnTown(player.getUuid(), world.getName(), playerX, playerZ);
 *
 * // Get the town at a location
 * String townName = api.getTownNameAt(world.getName(), blockX, blockZ);
 * }</pre>
 */
public class HyTownAPI {

    private final ClaimStorage claimStorage;
    private final TownStorage townStorage;
    private final ClaimManager claimManager;

    /**
     * Creates a new HyTownAPI instance.
     * This should only be called by the HyTown plugin itself.
     */
    public HyTownAPI(ClaimStorage claimStorage, TownStorage townStorage, ClaimManager claimManager) {
        this.claimStorage = claimStorage;
        this.townStorage = townStorage;
        this.claimManager = claimManager;
    }

    // ============================================================================
    // LOCATION TYPE QUERIES - Check what type of land a location is
    // ============================================================================

    /**
     * Check if a location is wilderness (unclaimed by both towns and personal claims).
     *
     * @param worldName The world name
     * @param worldX The world X coordinate (block coordinate, not chunk)
     * @param worldZ The world Z coordinate (block coordinate, not chunk)
     * @return true if the location is unclaimed wilderness
     */
    public boolean isWilderness(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);
        return isWildernessChunk(worldName, chunkX, chunkZ);
    }

    /**
     * Check if a chunk is wilderness (unclaimed).
     *
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return true if the chunk is unclaimed wilderness
     */
    public boolean isWildernessChunk(String worldName, int chunkX, int chunkZ) {
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if claimed by a town
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            return false;
        }

        // Check if claimed as personal claim
        UUID personalOwner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        return personalOwner == null;
    }

    /**
     * Check if a location is inside any town's territory.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the location is inside a town
     */
    public boolean isInTown(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);
        return isInTownChunk(worldName, chunkX, chunkZ);
    }

    /**
     * Check if a chunk is inside any town's territory.
     */
    public boolean isInTownChunk(String worldName, int chunkX, int chunkZ) {
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        return townStorage.getTownByClaimKey(claimKey) != null;
    }

    /**
     * Check if a location is inside a personal claim (non-town claim).
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the location is in a personal claim
     */
    public boolean isInPersonalClaim(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);
        return isInPersonalClaimChunk(worldName, chunkX, chunkZ);
    }

    /**
     * Check if a chunk is inside a personal claim.
     */
    public boolean isInPersonalClaimChunk(String worldName, int chunkX, int chunkZ) {
        // First check it's not a town claim
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        if (townStorage.getTownByClaimKey(claimKey) != null) {
            return false;
        }

        // Then check if it's a personal claim
        return claimStorage.getClaimOwner(worldName, chunkX, chunkZ) != null;
    }

    // ============================================================================
    // PLAYER LOCATION QUERIES - Check where a player is standing relative to towns
    // ============================================================================

    /**
     * Check if a player is standing in wilderness.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in wilderness
     */
    public boolean isPlayerInWilderness(UUID playerId, String worldName, double worldX, double worldZ) {
        return isWilderness(worldName, worldX, worldZ);
    }

    /**
     * Check if a player is standing inside any town's territory.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in a town
     */
    public boolean isPlayerInAnyTown(UUID playerId, String worldName, double worldX, double worldZ) {
        return isInTown(worldName, worldX, worldZ);
    }

    /**
     * Check if a player is standing inside their OWN town's territory.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in their own town
     */
    public boolean isPlayerInOwnTown(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town townAtLocation = townStorage.getTownByClaimKey(claimKey);

        if (townAtLocation == null) {
            return false;
        }

        // Check if the player is a member of this town
        return townAtLocation.isMember(playerId);
    }

    /**
     * Check if a player is standing inside another player's town (not their own).
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in a foreign town
     */
    public boolean isPlayerInForeignTown(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town townAtLocation = townStorage.getTownByClaimKey(claimKey);

        if (townAtLocation == null) {
            return false;
        }

        // Return true if it's a town but player is NOT a member
        return !townAtLocation.isMember(playerId);
    }

    /**
     * Check if a player is standing in their own personal claim.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in their own personal claim
     */
    public boolean isPlayerInOwnPersonalClaim(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        // Check it's not a town claim
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        if (townStorage.getTownByClaimKey(claimKey) != null) {
            return false;
        }

        // Check if player owns this personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        return playerId.equals(owner);
    }

    /**
     * Check if a player is standing in another player's personal claim.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The player's X coordinate
     * @param worldZ The player's Z coordinate
     * @return true if the player is in a foreign personal claim
     */
    public boolean isPlayerInForeignPersonalClaim(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        // Check it's not a town claim
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        if (townStorage.getTownByClaimKey(claimKey) != null) {
            return false;
        }

        // Check if someone else owns this personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        return owner != null && !playerId.equals(owner);
    }

    // ============================================================================
    // TOWN LOOKUP - Get town information at a location
    // ============================================================================

    /**
     * Get the town at a specific location.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The Town object, or null if not in a town
     */
    public Town getTownAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);
        return getTownAtChunk(worldName, chunkX, chunkZ);
    }

    /**
     * Get the town at a specific chunk.
     */
    public Town getTownAtChunk(String worldName, int chunkX, int chunkZ) {
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        return townStorage.getTownByClaimKey(claimKey);
    }

    /**
     * Get the town name at a specific location.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The town name, or null if not in a town
     */
    public String getTownNameAt(String worldName, double worldX, double worldZ) {
        Town town = getTownAt(worldName, worldX, worldZ);
        return town != null ? town.getName() : null;
    }

    /**
     * Get the town name at a specific chunk.
     */
    public String getTownNameAtChunk(String worldName, int chunkX, int chunkZ) {
        Town town = getTownAtChunk(worldName, chunkX, chunkZ);
        return town != null ? town.getName() : null;
    }

    /**
     * Get the personal claim owner at a location (excludes town claims).
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The owner's UUID, or null if not a personal claim
     */
    public UUID getPersonalClaimOwnerAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        // Exclude town claims
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        if (townStorage.getTownByClaimKey(claimKey) != null) {
            return null;
        }

        return claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
    }

    /**
     * Get the claim owner name at a location.
     * Returns town name for town claims, player name for personal claims.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The owner name, or null if wilderness
     */
    public String getClaimOwnerNameAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        // Check town first
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            return town.getName();
        }

        // Check personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner != null) {
            return claimStorage.getPlayerName(owner);
        }

        return null;
    }

    // ============================================================================
    // TOWN PROPERTY QUERIES - Get town settings at locations
    // ============================================================================

    /**
     * Check if PvP is enabled at a location.
     * Returns true (PvP allowed) for wilderness, checks town/plot settings otherwise.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if PvP is enabled at this location
     */
    public boolean isPvpEnabledAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            // Wilderness or personal claim - PvP is on by default
            // (Personal claims don't have PvP settings)
            return true;
        }

        // Check plot-level override first, then town default
        return town.isPvpEnabledAt(claimKey);
    }

    /**
     * Check if explosions are enabled at a location.
     * Returns true for wilderness, checks town/plot settings otherwise.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if explosions are enabled at this location
     */
    public boolean isExplosionsEnabledAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return true; // Wilderness
        }

        return town.isExplosionsEnabledAt(claimKey);
    }

    /**
     * Check if fire spread is enabled at a location.
     * Returns true for wilderness, checks town/plot settings otherwise.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if fire spread is enabled at this location
     */
    public boolean isFireSpreadEnabledAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return true; // Wilderness
        }

        return town.isFireSpreadEnabledAt(claimKey);
    }

    /**
     * Check if mob spawning is enabled at a location.
     * Returns true for wilderness, checks town/plot settings otherwise.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if mob spawning is enabled at this location
     */
    public boolean isMobSpawningEnabledAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return true; // Wilderness
        }

        return town.isMobSpawningEnabledAt(claimKey);
    }

    // ============================================================================
    // TOWN INFO QUERIES - Get information about specific towns
    // ============================================================================

    /**
     * Get a town by name.
     *
     * @param townName The town name (case-insensitive)
     * @return The Town object, or null if not found
     */
    public Town getTown(String townName) {
        return townStorage.getTown(townName);
    }

    /**
     * Check if a town exists.
     *
     * @param townName The town name
     * @return true if the town exists
     */
    public boolean townExists(String townName) {
        return townStorage.townExists(townName);
    }

    /**
     * Get the size of a town (number of claimed chunks).
     *
     * @param townName The town name
     * @return The number of claims, or 0 if town not found
     */
    public int getTownSize(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getClaimCount() : 0;
    }

    /**
     * Get the number of residents in a town.
     *
     * @param townName The town name
     * @return The number of residents, or 0 if town not found
     */
    public int getTownResidentCount(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getResidentCount() : 0;
    }

    /**
     * Get the town's bank balance.
     *
     * @param townName The town name
     * @return The balance, or 0 if town not found
     */
    public double getTownBalance(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getBalance() : 0.0;
    }

    /**
     * Get the mayor's UUID for a town.
     *
     * @param townName The town name
     * @return The mayor's UUID, or null if town not found
     */
    public UUID getTownMayor(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getMayorId() : null;
    }

    /**
     * Get the mayor's name for a town.
     *
     * @param townName The town name
     * @return The mayor's name, or null if town not found
     */
    public String getTownMayorName(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getMayorName() : null;
    }

    /**
     * Get all residents of a town.
     *
     * @param townName The town name
     * @return Set of resident UUIDs, or empty set if town not found
     */
    public Set<UUID> getTownResidents(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getResidents() : Collections.emptySet();
    }

    /**
     * Get all assistants of a town.
     *
     * @param townName The town name
     * @return Set of assistant UUIDs, or empty set if town not found
     */
    public Set<UUID> getTownAssistants(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getAssistants() : Collections.emptySet();
    }

    /**
     * Get the town settings (PvP, explosions, etc. defaults).
     *
     * @param townName The town name
     * @return The TownSettings object, or null if town not found
     */
    public TownSettings getTownSettings(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getSettings() : null;
    }

    /**
     * Get the town's spawn world.
     *
     * @param townName The town name
     * @return The spawn world name, or null if no spawn set or town not found
     */
    public String getTownSpawnWorld(String townName) {
        Town town = townStorage.getTown(townName);
        return (town != null && town.hasSpawn()) ? town.getSpawnWorld() : null;
    }

    /**
     * Get the town's spawn coordinates.
     *
     * @param townName The town name
     * @return double array [x, y, z], or null if no spawn set or town not found
     */
    public double[] getTownSpawnLocation(String townName) {
        Town town = townStorage.getTown(townName);
        if (town == null || !town.hasSpawn()) {
            return null;
        }
        return new double[]{town.getSpawnX(), town.getSpawnY(), town.getSpawnZ()};
    }

    /**
     * Check if a town's spawn is public (outsiders can use /town spawn).
     *
     * @param townName The town name
     * @return true if spawn is public, false otherwise or if town not found
     */
    public boolean isTownSpawnPublic(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.getSettings().isPublicSpawn();
    }

    /**
     * Check if a town is open (anyone can join without invite).
     *
     * @param townName The town name
     * @return true if town is open, false otherwise or if town not found
     */
    public boolean isTownOpen(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.getSettings().isOpenTown();
    }

    /**
     * Get the nation name for a town.
     *
     * @param townName The town name
     * @return The nation name, or null if not in a nation or town not found
     */
    public String getTownNation(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getNationName() : null;
    }

    /**
     * Get the town's creation timestamp.
     *
     * @param townName The town name
     * @return Timestamp in milliseconds, or 0 if town not found
     */
    public long getTownCreationTime(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getCreatedAt() : 0;
    }

    /**
     * Get all claim keys for a town.
     *
     * @param townName The town name
     * @return Set of claim keys (format: "world:chunkX,chunkZ"), or empty set
     */
    public Set<String> getTownClaimKeys(String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getClaimKeys() : Collections.emptySet();
    }

    // ============================================================================
    // PLAYER MEMBERSHIP QUERIES - Check player's town membership
    // ============================================================================

    /**
     * Get the town a player belongs to.
     *
     * @param playerId The player's UUID
     * @return The Town object, or null if player is not in a town
     */
    public Town getPlayerTown(UUID playerId) {
        return townStorage.getPlayerTown(playerId);
    }

    /**
     * Get the name of the town a player belongs to.
     *
     * @param playerId The player's UUID
     * @return The town name, or null if player is not in a town
     */
    public String getPlayerTownName(UUID playerId) {
        Town town = townStorage.getPlayerTown(playerId);
        return town != null ? town.getName() : null;
    }

    /**
     * Check if a player is a member of any town.
     *
     * @param playerId The player's UUID
     * @return true if the player is in a town
     */
    public boolean isPlayerInATown(UUID playerId) {
        return townStorage.getPlayerTown(playerId) != null;
    }

    /**
     * Check if a player is a member of a specific town.
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return true if the player is a member of the specified town
     */
    public boolean isPlayerMemberOf(UUID playerId, String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.isMember(playerId);
    }

    /**
     * Check if a player is the mayor of a town.
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return true if the player is the mayor
     */
    public boolean isMayor(UUID playerId, String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.isMayor(playerId);
    }

    /**
     * Check if a player is an assistant (or mayor) of a town.
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return true if the player is an assistant or mayor
     */
    public boolean isAssistant(UUID playerId, String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.isAssistant(playerId);
    }

    /**
     * Check if a player is a resident of a town (any rank).
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return true if the player is a resident (member of any rank)
     */
    public boolean isResident(UUID playerId, String townName) {
        Town town = townStorage.getTown(townName);
        return town != null && town.isMember(playerId);
    }

    /**
     * Get a player's rank in a town.
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return The TownRank (MAYOR, ASSISTANT, RESIDENT), or null if not a member
     */
    public Town.TownRank getPlayerRank(UUID playerId, String townName) {
        Town town = townStorage.getTown(townName);
        return town != null ? town.getRank(playerId) : null;
    }

    /**
     * Get a player's rank name in a town.
     *
     * @param playerId The player's UUID
     * @param townName The town name
     * @return The rank name ("Mayor", "Assistant", "Resident"), or null if not a member
     */
    public String getPlayerRankName(UUID playerId, String townName) {
        Town.TownRank rank = getPlayerRank(playerId, townName);
        return rank != null ? rank.getDisplayName() : null;
    }

    // ============================================================================
    // PERMISSION QUERIES - Check if players can perform actions at locations
    // ============================================================================

    /**
     * Check if a player can build (place blocks) at a location.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the player can build at this location
     */
    public boolean canPlayerBuildAt(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check town claim first
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            // Check if player is a member
            if (town.isMember(playerId)) {
                // Mayor/Assistant can always build
                if (town.isAssistant(playerId)) {
                    return true;
                }
                // Regular resident - check resident permissions
                return town.getSettings().canResidentBuild();
            } else {
                // Outsider - check outsider permissions
                return town.getSettings().canOutsiderBuild();
            }
        }

        // Check personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner != null) {
            // Owner can always build
            if (playerId.equals(owner)) {
                return true;
            }
            // Check trust level
            if (claimManager != null) {
                TrustLevel trust = claimManager.getTrustLevel(owner, playerId);
                return trust != null && trust.ordinal() >= TrustLevel.BUILD.ordinal();
            }
            return false;
        }

        // Wilderness - anyone can build
        return true;
    }

    /**
     * Check if a player can destroy (break blocks) at a location.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the player can destroy at this location
     */
    public boolean canPlayerDestroyAt(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check town claim first
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            if (town.isMember(playerId)) {
                if (town.isAssistant(playerId)) {
                    return true;
                }
                return town.getSettings().canResidentDestroy();
            } else {
                return town.getSettings().canOutsiderDestroy();
            }
        }

        // Check personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner != null) {
            if (playerId.equals(owner)) {
                return true;
            }
            if (claimManager != null) {
                TrustLevel trust = claimManager.getTrustLevel(owner, playerId);
                return trust != null && trust.ordinal() >= TrustLevel.BUILD.ordinal();
            }
            return false;
        }

        return true; // Wilderness
    }

    /**
     * Check if a player can use (doors, buttons, levers) at a location.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the player can use switches at this location
     */
    public boolean canPlayerUseAt(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check town claim first
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            if (town.isMember(playerId)) {
                if (town.isAssistant(playerId)) {
                    return true;
                }
                return town.getSettings().canResidentSwitch();
            } else {
                return town.getSettings().canOutsiderSwitch();
            }
        }

        // Check personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner != null) {
            if (playerId.equals(owner)) {
                return true;
            }
            if (claimManager != null) {
                TrustLevel trust = claimManager.getTrustLevel(owner, playerId);
                return trust != null && trust.ordinal() >= TrustLevel.USE.ordinal();
            }
            return false;
        }

        return true; // Wilderness
    }

    /**
     * Check if a player can access containers (chests, furnaces) at a location.
     *
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the player can access containers at this location
     */
    public boolean canPlayerAccessContainersAt(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check town claim first
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town != null) {
            // Use the town's canAccessContainers method which handles plot protection
            return town.canAccessContainers(claimKey, playerId);
        }

        // Check personal claim
        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner != null) {
            if (playerId.equals(owner)) {
                return true;
            }
            if (claimManager != null) {
                TrustLevel trust = claimManager.getTrustLevel(owner, playerId);
                return trust != null && trust.ordinal() >= TrustLevel.CONTAINER.ordinal();
            }
            return false;
        }

        return true; // Wilderness
    }

    // ============================================================================
    // TOWN LISTING - Get lists of all towns
    // ============================================================================

    /**
     * Get all town names.
     *
     * @return List of all town names
     */
    public List<String> getAllTownNames() {
        List<String> names = new ArrayList<>();
        for (Town town : townStorage.getAllTowns()) {
            names.add(town.getName());
        }
        return names;
    }

    /**
     * Get all towns.
     *
     * @return Collection of all Town objects
     */
    public Collection<Town> getAllTowns() {
        return townStorage.getAllTowns();
    }

    /**
     * Get the total number of towns.
     *
     * @return The number of towns
     */
    public int getTownCount() {
        return townStorage.getTownCount();
    }

    /**
     * Get towns sorted by size (number of claims).
     *
     * @param descending true for largest first, false for smallest first
     * @return List of Town objects sorted by size
     */
    public List<Town> getTownsBySize(boolean descending) {
        List<Town> towns = new ArrayList<>(townStorage.getAllTowns());
        if (descending) {
            towns.sort((a, b) -> Integer.compare(b.getClaimCount(), a.getClaimCount()));
        } else {
            towns.sort((a, b) -> Integer.compare(a.getClaimCount(), b.getClaimCount()));
        }
        return towns;
    }

    /**
     * Get towns sorted by resident count.
     *
     * @param descending true for largest first, false for smallest first
     * @return List of Town objects sorted by resident count
     */
    public List<Town> getTownsByPopulation(boolean descending) {
        List<Town> towns = new ArrayList<>(townStorage.getAllTowns());
        if (descending) {
            towns.sort((a, b) -> Integer.compare(b.getResidentCount(), a.getResidentCount()));
        } else {
            towns.sort((a, b) -> Integer.compare(a.getResidentCount(), b.getResidentCount()));
        }
        return towns;
    }

    /**
     * Get towns sorted by balance (wealth).
     *
     * @param descending true for richest first, false for poorest first
     * @return List of Town objects sorted by balance
     */
    public List<Town> getTownsByBalance(boolean descending) {
        List<Town> towns = new ArrayList<>(townStorage.getAllTowns());
        if (descending) {
            towns.sort((a, b) -> Double.compare(b.getBalance(), a.getBalance()));
        } else {
            towns.sort((a, b) -> Double.compare(a.getBalance(), b.getBalance()));
        }
        return towns;
    }

    // ============================================================================
    // PERSONAL CLAIM QUERIES - Get personal claim information
    // ============================================================================

    /**
     * Get the number of personal claims a player has.
     *
     * @param playerId The player's UUID
     * @return The number of personal claims
     */
    public int getPlayerClaimCount(UUID playerId) {
        PlayerClaims claims = claimStorage.getPlayerClaims(playerId);
        return claims != null ? claims.getClaimCount() : 0;
    }

    /**
     * Get all personal claims for a player.
     *
     * @param playerId The player's UUID
     * @return List of Claim objects, or empty list if none
     */
    public List<Claim> getPlayerClaims(UUID playerId) {
        PlayerClaims claims = claimStorage.getPlayerClaims(playerId);
        return claims != null ? claims.getClaims() : Collections.emptyList();
    }

    /**
     * Check if a player is trusted in another player's claim at a location.
     *
     * @param playerId The player whose trust is being checked
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if the player is trusted at this location
     */
    public boolean isPlayerTrustedAt(UUID playerId, String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        // Check it's not a town claim
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        if (townStorage.getTownByClaimKey(claimKey) != null) {
            return false;
        }

        UUID owner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
        if (owner == null || owner.equals(playerId)) {
            return false;
        }

        if (claimManager != null) {
            return claimManager.isTrusted(owner, playerId);
        }

        return false;
    }

    /**
     * Get the trust level a player has in another player's claims.
     *
     * @param ownerId The claim owner's UUID
     * @param playerId The player whose trust level to check
     * @return The TrustLevel, or null if not trusted
     */
    public TrustLevel getPlayerTrustLevel(UUID ownerId, UUID playerId) {
        if (claimManager != null) {
            return claimManager.getTrustLevel(ownerId, playerId);
        }
        return null;
    }

    // ============================================================================
    // PLOT QUERIES - Get plot-specific information within towns
    // ============================================================================

    /**
     * Get the owner of a plot within a town.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The plot owner's UUID, or null if unassigned or not in a town
     */
    public UUID getPlotOwnerAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return null;
        }

        return town.getPlotOwner(claimKey);
    }

    /**
     * Check if a plot has owner protection enabled.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return true if plot has owner protection enabled
     */
    public boolean isPlotProtectedAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return false;
        }

        return town.isPlotProtected(claimKey);
    }

    /**
     * Get the plot settings at a location.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return The PlotSettings, or null if not in a town or no settings
     */
    public PlotSettings getPlotSettingsAt(String worldName, double worldX, double worldZ) {
        int chunkX = ChunkUtil.toChunkX(worldX);
        int chunkZ = ChunkUtil.toChunkZ(worldZ);

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            return null;
        }

        return town.getPlotSettings(claimKey);
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Convert world coordinates to chunk coordinates.
     *
     * @param worldCoord The world coordinate
     * @return The chunk coordinate
     */
    public int worldToChunk(double worldCoord) {
        return (int) Math.floor(worldCoord / ChunkUtil.CHUNK_SIZE);
    }

    /**
     * Get the chunk size used by HyTown.
     *
     * @return The chunk size in blocks (32 for Hytale)
     */
    public int getChunkSize() {
        return ChunkUtil.CHUNK_SIZE;
    }

    /**
     * Create a claim key from coordinates.
     *
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return The claim key in format "world:chunkX,chunkZ"
     */
    public String createClaimKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ":" + chunkX + "," + chunkZ;
    }

    /**
     * Get a summary of location information at a specific point.
     * Useful for debugging or display purposes.
     *
     * @param worldName The world name
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @return A LocationInfo object with all relevant information
     */
    public LocationInfo getLocationInfo(String worldName, double worldX, double worldZ) {
        return new LocationInfo(this, worldName, worldX, worldZ);
    }

    /**
     * Helper class containing all information about a location.
     */
    public static class LocationInfo {
        private final String worldName;
        private final double worldX;
        private final double worldZ;
        private final int chunkX;
        private final int chunkZ;
        private final boolean isWilderness;
        private final boolean isInTown;
        private final boolean isInPersonalClaim;
        private final String townName;
        private final String claimOwnerName;
        private final boolean pvpEnabled;
        private final boolean explosionsEnabled;
        private final boolean fireSpreadEnabled;
        private final boolean mobSpawningEnabled;

        public LocationInfo(HyTownAPI api, String worldName, double worldX, double worldZ) {
            this.worldName = worldName;
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.chunkX = api.worldToChunk(worldX);
            this.chunkZ = api.worldToChunk(worldZ);
            this.isWilderness = api.isWilderness(worldName, worldX, worldZ);
            this.isInTown = api.isInTown(worldName, worldX, worldZ);
            this.isInPersonalClaim = api.isInPersonalClaim(worldName, worldX, worldZ);
            this.townName = api.getTownNameAt(worldName, worldX, worldZ);
            this.claimOwnerName = api.getClaimOwnerNameAt(worldName, worldX, worldZ);
            this.pvpEnabled = api.isPvpEnabledAt(worldName, worldX, worldZ);
            this.explosionsEnabled = api.isExplosionsEnabledAt(worldName, worldX, worldZ);
            this.fireSpreadEnabled = api.isFireSpreadEnabledAt(worldName, worldX, worldZ);
            this.mobSpawningEnabled = api.isMobSpawningEnabledAt(worldName, worldX, worldZ);
        }

        public String getWorldName() { return worldName; }
        public double getWorldX() { return worldX; }
        public double getWorldZ() { return worldZ; }
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public boolean isWilderness() { return isWilderness; }
        public boolean isInTown() { return isInTown; }
        public boolean isInPersonalClaim() { return isInPersonalClaim; }
        public String getTownName() { return townName; }
        public String getClaimOwnerName() { return claimOwnerName; }
        public boolean isPvpEnabled() { return pvpEnabled; }
        public boolean isExplosionsEnabled() { return explosionsEnabled; }
        public boolean isFireSpreadEnabled() { return fireSpreadEnabled; }
        public boolean isMobSpawningEnabled() { return mobSpawningEnabled; }

        /**
         * Get a human-readable description of this location.
         */
        public String getDescription() {
            if (isWilderness) {
                return "Wilderness";
            } else if (isInTown) {
                return "Town: " + townName;
            } else if (isInPersonalClaim) {
                return "Claimed by: " + claimOwnerName;
            }
            return "Unknown";
        }

        @Override
        public String toString() {
            return String.format("LocationInfo{world=%s, x=%.1f, z=%.1f, chunk=%d,%d, %s, pvp=%b, explosions=%b}",
                    worldName, worldX, worldZ, chunkX, chunkZ, getDescription(), pvpEnabled, explosionsEnabled);
        }
    }
}
