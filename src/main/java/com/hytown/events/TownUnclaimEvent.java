package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired when a chunk is unclaimed from a town.
 */
public class TownUnclaimEvent implements IEvent<Void> {
    private final Town town;
    private final String claimKey;
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private final UUID unclaimedBy;
    private final String unclaimedByName;

    public TownUnclaimEvent(Town town, String claimKey, String worldName, int chunkX, int chunkZ,
                            UUID unclaimedBy, String unclaimedByName) {
        this.town = town;
        this.claimKey = claimKey;
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.unclaimedBy = unclaimedBy;
        this.unclaimedByName = unclaimedByName;
    }

    /**
     * Get the town that unclaimed the chunk.
     */
    public Town getTown() {
        return town;
    }

    /**
     * Get the town name.
     */
    public String getTownName() {
        return town.getName();
    }

    /**
     * Get the claim key.
     * Format: "world:chunkX,chunkZ"
     */
    public String getClaimKey() {
        return claimKey;
    }

    /**
     * Get the world name where the chunk was unclaimed.
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Get the chunk X coordinate.
     */
    public int getChunkX() {
        return chunkX;
    }

    /**
     * Get the chunk Z coordinate.
     */
    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * Get the center block X coordinate (center of chunk).
     */
    public int getBlockX() {
        return chunkX * 16 + 8;
    }

    /**
     * Get the center block Z coordinate (center of chunk).
     */
    public int getBlockZ() {
        return chunkZ * 16 + 8;
    }

    /**
     * Get the UUID of the player who unclaimed the chunk.
     */
    public UUID getUnclaimedBy() {
        return unclaimedBy;
    }

    /**
     * Get the username of the player who unclaimed the chunk.
     */
    public String getUnclaimedByName() {
        return unclaimedByName;
    }
}
