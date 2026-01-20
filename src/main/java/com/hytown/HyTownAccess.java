package com.hytown;

import com.hytown.data.ClaimStorage;
import com.hytown.data.PlayerClaims;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.data.TrustedPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Static accessor for claim data used by the map system.
 * This is needed because the map image builder runs asynchronously
 * and needs access to claim information.
 */
public class HyTownAccess {
    private static ClaimStorage claimStorage;
    private static TownStorage townStorage;

    /**
     * Initializes the accessor with the claim and town storage instances.
     * Called during plugin startup.
     */
    public static void init(ClaimStorage storage, TownStorage towns) {
        claimStorage = storage;
        townStorage = towns;
    }

    /**
     * Gets the owner of a chunk, or null if unclaimed.
     * Checks BOTH town claims and personal claims.
     * For town claims, returns the town owner's UUID.
     * Used by ClaimImageBuilder to determine claim colors.
     */
    public static UUID getClaimOwner(String worldName, int chunkX, int chunkZ) {
        // Check if this chunk belongs to a town FIRST
        if (townStorage != null) {
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;
            Town town = townStorage.getTownByClaimKey(claimKey);
            if (town != null) {
                return town.getMayorId();
            }
        }

        if (claimStorage == null) {
            return null;
        }
        return claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
    }

    /**
     * Gets the name of a player by their UUID.
     */
    public static String getPlayerName(UUID playerId) {
        if (claimStorage == null || playerId == null) {
            return "Unknown";
        }
        return claimStorage.getPlayerName(playerId);
    }

    /**
     * Gets the display name for a claimed chunk.
     * Returns the town name if this chunk belongs to a town, otherwise the player name.
     * Checks BOTH town claims and personal claims.
     */
    public static String getOwnerName(String worldName, int chunkX, int chunkZ) {
        // Check if this chunk belongs to a town FIRST (town claims take priority)
        if (townStorage != null) {
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;
            Town town = townStorage.getTownByClaimKey(claimKey);
            if (town != null) {
                // Return the town name
                return town.getName();
            }
        }

        // Not a town claim, check personal claims
        UUID owner = getClaimOwner(worldName, chunkX, chunkZ);
        if (owner == null) {
            return null;
        }

        // Return the player name for personal claims
        return getPlayerName(owner);
    }

    /**
     * Gets the list of trusted player names for a claimed chunk.
     * Returns an empty list if unclaimed or no trusted players.
     */
    public static List<String> getTrustedPlayerNames(String worldName, int chunkX, int chunkZ) {
        List<String> names = new ArrayList<>();
        if (claimStorage == null) {
            return names;
        }

        UUID owner = getClaimOwner(worldName, chunkX, chunkZ);
        if (owner == null) {
            return names;
        }

        PlayerClaims playerClaims = claimStorage.getPlayerClaims(owner);
        if (playerClaims == null) {
            return names;
        }

        Map<UUID, TrustedPlayer> trustedMap = playerClaims.getTrustedPlayersMap();
        for (TrustedPlayer trusted : trustedMap.values()) {
            names.add(trusted.getName());
        }

        return names;
    }
}
