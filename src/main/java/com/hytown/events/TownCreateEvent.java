package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired when a new town is created.
 * Contains information about the newly created town and its founder.
 */
public class TownCreateEvent implements IEvent<Void> {
    private final Town town;
    private final UUID founderId;
    private final String founderName;
    private final String initialClaimKey;

    public TownCreateEvent(Town town, UUID founderId, String founderName, String initialClaimKey) {
        this.town = town;
        this.founderId = founderId;
        this.founderName = founderName;
        this.initialClaimKey = initialClaimKey;
    }

    /**
     * Get the newly created town.
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
     * Get the UUID of the player who founded the town (the mayor).
     */
    public UUID getFounderId() {
        return founderId;
    }

    /**
     * Get the username of the player who founded the town.
     */
    public String getFounderName() {
        return founderName;
    }

    /**
     * Get the initial claim key (the first chunk claimed for the town).
     * Format: "world:chunkX,chunkZ"
     */
    public String getInitialClaimKey() {
        return initialClaimKey;
    }
}
