package com.hytown.events;

import com.hytown.data.Town;
import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired when a player joins a town.
 * This includes both accepting an invite and joining an open town.
 */
public class TownJoinEvent implements IEvent<Void> {
    private final Town town;
    private final UUID playerId;
    private final String playerName;
    private final boolean fromInvite;

    public TownJoinEvent(Town town, UUID playerId, String playerName, boolean fromInvite) {
        this.town = town;
        this.playerId = playerId;
        this.playerName = playerName;
        this.fromInvite = fromInvite;
    }

    /**
     * Get the town the player joined.
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
     * Get the UUID of the player who joined.
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Get the username of the player who joined.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Check if the player joined via an invite (vs. joining an open town).
     */
    public boolean isFromInvite() {
        return fromInvite;
    }
}
