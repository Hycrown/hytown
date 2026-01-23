package com.hytown.managers;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages town and chunk border visualization toggles for players.
 *
 * This manager tracks which players have border visualization enabled.
 * The actual rendering is handled by TownBorderRenderSystem.
 */
public class TownBorderManager {

    // Players who have town border visualization enabled
    private final Set<UUID> townBorderEnabled = ConcurrentHashMap.newKeySet();

    // Players who have chunk border visualization enabled
    private final Set<UUID> chunkBorderEnabled = ConcurrentHashMap.newKeySet();

    /**
     * Toggles town border visualization for a player.
     * @return true if now enabled, false if disabled
     */
    public boolean toggleTownBorder(UUID playerId) {
        if (townBorderEnabled.contains(playerId)) {
            townBorderEnabled.remove(playerId);
            return false;
        } else {
            townBorderEnabled.add(playerId);
            return true;
        }
    }

    /**
     * Toggles chunk border visualization for a player.
     * @return true if now enabled, false if disabled
     */
    public boolean toggleChunkBorders(UUID playerId) {
        if (chunkBorderEnabled.contains(playerId)) {
            chunkBorderEnabled.remove(playerId);
            return false;
        } else {
            chunkBorderEnabled.add(playerId);
            return true;
        }
    }

    /**
     * Checks if town borders are enabled for a player.
     */
    public boolean isTownBorderEnabled(UUID playerId) {
        return townBorderEnabled.contains(playerId);
    }

    /**
     * Checks if chunk borders are enabled for a player.
     */
    public boolean isChunkBorderEnabled(UUID playerId) {
        return chunkBorderEnabled.contains(playerId);
    }

    /**
     * Enables town border for a player.
     */
    public void enableTownBorder(UUID playerId) {
        townBorderEnabled.add(playerId);
    }

    /**
     * Disables town border for a player.
     */
    public void disableTownBorder(UUID playerId) {
        townBorderEnabled.remove(playerId);
    }

    /**
     * Enables chunk border for a player.
     */
    public void enableChunkBorder(UUID playerId) {
        chunkBorderEnabled.add(playerId);
    }

    /**
     * Disables chunk border for a player.
     */
    public void disableChunkBorder(UUID playerId) {
        chunkBorderEnabled.remove(playerId);
    }

    /**
     * Disables all borders for a player (called on disconnect).
     */
    public void disableAll(UUID playerId) {
        townBorderEnabled.remove(playerId);
        chunkBorderEnabled.remove(playerId);
    }

    /**
     * Gets all players with town borders enabled.
     */
    public Set<UUID> getPlayersWithTownBorders() {
        return Set.copyOf(townBorderEnabled);
    }

    /**
     * Gets all players with chunk borders enabled.
     */
    public Set<UUID> getPlayersWithChunkBorders() {
        return Set.copyOf(chunkBorderEnabled);
    }
}
