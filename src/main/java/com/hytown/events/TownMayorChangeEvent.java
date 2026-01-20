package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired when a town's mayor changes.
 * This includes voluntary transfers and admin-forced changes.
 */
public class TownMayorChangeEvent implements IEvent<Void> {
    private final Town town;
    private final UUID oldMayorId;
    private final String oldMayorName;
    private final UUID newMayorId;
    private final String newMayorName;
    private final boolean adminForced;

    public TownMayorChangeEvent(Town town, UUID oldMayorId, String oldMayorName,
                                 UUID newMayorId, String newMayorName, boolean adminForced) {
        this.town = town;
        this.oldMayorId = oldMayorId;
        this.oldMayorName = oldMayorName;
        this.newMayorId = newMayorId;
        this.newMayorName = newMayorName;
        this.adminForced = adminForced;
    }

    /**
     * Get the town whose mayor changed.
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
     * Get the UUID of the old mayor.
     */
    public UUID getOldMayorId() {
        return oldMayorId;
    }

    /**
     * Get the username of the old mayor.
     */
    public String getOldMayorName() {
        return oldMayorName;
    }

    /**
     * Get the UUID of the new mayor.
     */
    public UUID getNewMayorId() {
        return newMayorId;
    }

    /**
     * Get the username of the new mayor.
     */
    public String getNewMayorName() {
        return newMayorName;
    }

    /**
     * Check if this was an admin-forced mayor change.
     */
    public boolean isAdminForced() {
        return adminForced;
    }
}
