package com.hytown.storage;

import com.hytown.data.Town;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for town data storage backends.
 * Implementations include flatfile (JSON), SQL (MySQL), and MongoDB.
 *
 * All implementations should:
 * - Be thread-safe for concurrent access
 * - Support atomic operations where possible
 * - Handle connection failures gracefully
 * - Validate data on load
 */
public interface StorageProvider {

    /**
     * Initialize the storage provider (connect to database, create tables, etc.)
     * @throws Exception if initialization fails
     */
    void init() throws Exception;

    /**
     * Shutdown the storage provider (close connections, cleanup).
     */
    void shutdown();

    /**
     * Get the name of this storage provider (for logging/status).
     */
    String getName();

    /**
     * Check if this provider is connected and ready.
     */
    boolean isConnected();

    // ==================== TOWN OPERATIONS ====================

    /**
     * Load a specific town by name.
     * @param townName The town name (case-insensitive)
     * @return The Town object, or null if not found
     */
    Town loadTown(String townName);

    /**
     * Load all towns from storage.
     * @return Collection of all towns
     */
    Collection<Town> loadAllTowns();

    /**
     * Save a town to storage.
     * Should throw RuntimeException on failure so caller knows save failed.
     * @param town The town to save
     * @throws RuntimeException if save fails
     */
    void saveTown(Town town);

    /**
     * Delete a town from storage.
     * @param townName The town name to delete
     */
    void deleteTown(String townName);

    /**
     * Rename a town in storage.
     * Default implementation uses delete + save, but implementations
     * should override this for atomic rename support (especially with transactions).
     *
     * @param oldName The old town name
     * @param newName The new town name
     * @param town The updated town object (with new name already set)
     * @throws RuntimeException if rename fails
     */
    default void renameTown(String oldName, String newName, Town town) {
        // Default implementation: save new first, then delete old
        // This ensures data isn't lost if something goes wrong
        saveTown(town);
        if (!oldName.equalsIgnoreCase(newName)) {
            deleteTown(oldName);
        }
    }

    // ==================== INVITE OPERATIONS ====================

    /**
     * Load all pending invites.
     * @return Map of player UUID to set of town names they're invited to
     */
    Map<UUID, Set<String>> loadInvites();

    /**
     * Save all pending invites.
     * Should throw RuntimeException on failure so caller knows save failed.
     * @param invites Map of player UUID to set of town names
     * @throws RuntimeException if save fails
     */
    void saveInvites(Map<UUID, Set<String>> invites);

    // ==================== OPTIONAL ENHANCED OPERATIONS ====================

    /**
     * Add a single invite without replacing all invites.
     * Default implementation does nothing - override in implementations
     * that support incremental updates.
     *
     * @param playerId The player UUID
     * @param townName The town name
     */
    default void addInvite(UUID playerId, String townName) {
        // Default: no-op, use saveInvites() instead
    }

    /**
     * Remove a single invite.
     * Default implementation does nothing - override in implementations
     * that support incremental updates.
     *
     * @param playerId The player UUID
     * @param townName The town name
     */
    default void removeInvite(UUID playerId, String townName) {
        // Default: no-op, use saveInvites() instead
    }

    /**
     * Clear all invites for a player.
     * Default implementation does nothing - override in implementations
     * that support this operation directly.
     *
     * @param playerId The player UUID
     */
    default void clearPlayerInvites(UUID playerId) {
        // Default: no-op, use saveInvites() instead
    }

    /**
     * Clear all invites for a town (e.g., when town is deleted).
     * Default implementation does nothing - override in implementations
     * that support this operation directly.
     *
     * @param townName The town name
     */
    default void clearTownInvites(String townName) {
        // Default: no-op, use saveInvites() instead
    }

    /**
     * Check if this provider supports transactions.
     * Transactions ensure atomic operations for multi-step changes.
     *
     * @return true if transactions are supported
     */
    default boolean supportsTransactions() {
        return false;
    }

    /**
     * Get connection/status info for display.
     * @return Human-readable status string
     */
    default String getConnectionInfo() {
        return getName() + " (" + (isConnected() ? "connected" : "disconnected") + ")";
    }
}
