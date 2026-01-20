package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Fired when a town is deleted.
 * Contains information about the deleted town and all its claims.
 * This event is fired BEFORE the town data is removed, so listeners can still access town info.
 */
public class TownDeleteEvent implements IEvent<Void> {
    private final Town town;
    private final UUID deletedBy;
    private final String deletedByName;
    private final boolean adminDelete;

    public TownDeleteEvent(Town town, UUID deletedBy, String deletedByName, boolean adminDelete) {
        this.town = town;
        this.deletedBy = deletedBy;
        this.deletedByName = deletedByName;
        this.adminDelete = adminDelete;
    }

    /**
     * Get the town being deleted.
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
     * Get the UUID of the player who deleted the town.
     */
    public UUID getDeletedBy() {
        return deletedBy;
    }

    /**
     * Get the username of the player who deleted the town.
     */
    public String getDeletedByName() {
        return deletedByName;
    }

    /**
     * Check if this was an admin-forced deletion.
     */
    public boolean isAdminDelete() {
        return adminDelete;
    }

    /**
     * Get all claim keys that belonged to this town.
     * Format: "world:chunkX,chunkZ"
     */
    public Set<String> getClaimKeys() {
        return town.getClaimKeys();
    }

    /**
     * Get all resident UUIDs that were in this town.
     */
    public Set<UUID> getResidents() {
        return town.getResidents();
    }
}
