package com.hytown.storage;

import com.hytown.data.Town;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for town data storage backends.
 * Implementations include flatfile (JSON), SQL (MySQL), and MongoDB.
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
     * @param town The town to save
     */
    void saveTown(Town town);

    /**
     * Delete a town from storage.
     * @param townName The town name to delete
     */
    void deleteTown(String townName);

    /**
     * Rename a town in storage (delete old, save new).
     * @param oldName The old town name
     * @param newName The new town name
     * @param town The updated town object
     */
    default void renameTown(String oldName, String newName, Town town) {
        deleteTown(oldName);
        saveTown(town);
    }

    // ==================== INVITE OPERATIONS ====================

    /**
     * Load all pending invites.
     * @return Map of player UUID to set of town names they're invited to
     */
    Map<UUID, Set<String>> loadInvites();

    /**
     * Save all pending invites.
     * @param invites Map of player UUID to set of town names
     */
    void saveInvites(Map<UUID, Set<String>> invites);
}
