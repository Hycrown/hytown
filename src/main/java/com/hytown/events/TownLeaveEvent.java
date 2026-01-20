package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired when a player leaves a town.
 * This includes voluntary leaving, being kicked, and admin kicks.
 */
public class TownLeaveEvent implements IEvent<Void> {

    public enum LeaveReason {
        /** Player voluntarily left the town */
        VOLUNTARY,
        /** Player was kicked by mayor/assistant */
        KICKED,
        /** Player was kicked by an admin */
        ADMIN_KICKED,
        /** Town was deleted (all members leave) */
        TOWN_DELETED
    }

    private final Town town;
    private final UUID playerId;
    private final String playerName;
    private final LeaveReason reason;
    private final UUID kickedBy;
    private final String kickedByName;

    public TownLeaveEvent(Town town, UUID playerId, String playerName, LeaveReason reason,
                          UUID kickedBy, String kickedByName) {
        this.town = town;
        this.playerId = playerId;
        this.playerName = playerName;
        this.reason = reason;
        this.kickedBy = kickedBy;
        this.kickedByName = kickedByName;
    }

    /**
     * Create a leave event for voluntary leaving.
     */
    public static TownLeaveEvent voluntary(Town town, UUID playerId, String playerName) {
        return new TownLeaveEvent(town, playerId, playerName, LeaveReason.VOLUNTARY, null, null);
    }

    /**
     * Create a leave event for being kicked.
     */
    public static TownLeaveEvent kicked(Town town, UUID playerId, String playerName,
                                         UUID kickedBy, String kickedByName) {
        return new TownLeaveEvent(town, playerId, playerName, LeaveReason.KICKED, kickedBy, kickedByName);
    }

    /**
     * Create a leave event for admin kick.
     */
    public static TownLeaveEvent adminKicked(Town town, UUID playerId, String playerName,
                                              UUID adminId, String adminName) {
        return new TownLeaveEvent(town, playerId, playerName, LeaveReason.ADMIN_KICKED, adminId, adminName);
    }

    /**
     * Create a leave event for town deletion.
     */
    public static TownLeaveEvent townDeleted(Town town, UUID playerId, String playerName) {
        return new TownLeaveEvent(town, playerId, playerName, LeaveReason.TOWN_DELETED, null, null);
    }

    /**
     * Get the town the player left.
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
     * Get the UUID of the player who left.
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Get the username of the player who left.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Get the reason for leaving.
     */
    public LeaveReason getReason() {
        return reason;
    }

    /**
     * Check if this was a voluntary leave.
     */
    public boolean isVoluntary() {
        return reason == LeaveReason.VOLUNTARY;
    }

    /**
     * Check if this was a kick (by mayor/assistant or admin).
     */
    public boolean isKicked() {
        return reason == LeaveReason.KICKED || reason == LeaveReason.ADMIN_KICKED;
    }

    /**
     * Get the UUID of who kicked the player (null if voluntary leave or town deleted).
     */
    public UUID getKickedBy() {
        return kickedBy;
    }

    /**
     * Get the username of who kicked the player (null if voluntary leave or town deleted).
     */
    public String getKickedByName() {
        return kickedByName;
    }
}
