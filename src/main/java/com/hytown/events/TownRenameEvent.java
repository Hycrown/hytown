package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Fired when a town is renamed.
 * Contains the old and new names, useful for updating references in other plugins.
 */
public class TownRenameEvent implements IEvent<Void> {
    private final Town town;
    private final String oldName;
    private final String newName;
    private final UUID renamedBy;
    private final String renamedByName;

    public TownRenameEvent(Town town, String oldName, String newName, UUID renamedBy, String renamedByName) {
        this.town = town;
        this.oldName = oldName;
        this.newName = newName;
        this.renamedBy = renamedBy;
        this.renamedByName = renamedByName;
    }

    /**
     * Get the town (with the new name already applied).
     */
    public Town getTown() {
        return town;
    }

    /**
     * Get the old town name (before rename).
     */
    public String getOldName() {
        return oldName;
    }

    /**
     * Get the new town name (after rename).
     */
    public String getNewName() {
        return newName;
    }

    /**
     * Get the UUID of the player who renamed the town.
     */
    public UUID getRenamedBy() {
        return renamedBy;
    }

    /**
     * Get the username of the player who renamed the town.
     */
    public String getRenamedByName() {
        return renamedByName;
    }

    /**
     * Get all claim keys belonging to this town.
     * Format: "world:chunkX,chunkZ"
     */
    public Set<String> getClaimKeys() {
        return town.getClaimKeys();
    }
}
