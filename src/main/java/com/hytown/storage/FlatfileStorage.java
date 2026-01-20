package com.hytown.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hytown.data.Town;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Flatfile (JSON) storage provider.
 * Stores each town as a separate JSON file.
 */
public class FlatfileStorage implements StorageProvider {

    private final Path townsDirectory;
    private final Path indexFile;
    private final Path corruptedDirectory;
    private final Gson gson;

    public FlatfileStorage(Path dataDirectory) {
        this.townsDirectory = dataDirectory.resolve("towns");
        this.indexFile = townsDirectory.resolve("_index.json");
        this.corruptedDirectory = townsDirectory.resolve("corrupted");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();
    }

    @Override
    public void init() throws Exception {
        Files.createDirectories(townsDirectory);
        Files.createDirectories(corruptedDirectory);
        cleanupTempFiles();
    }

    @Override
    public void shutdown() {
        // Nothing to close for flatfile
    }

    @Override
    public String getName() {
        return "Flatfile (JSON)";
    }

    @Override
    public boolean isConnected() {
        return Files.isDirectory(townsDirectory);
    }

    // ==================== TOWN OPERATIONS ====================

    @Override
    public Town loadTown(String townName) {
        Path file = townsDirectory.resolve(sanitize(townName) + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        try {
            String json = Files.readString(file);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            Town town = gson.fromJson(json, Town.class);
            if (town != null) {
                town.validateAfterLoad();
            }
            return town;
        } catch (Exception e) {
            System.err.println("[FlatfileStorage] Failed to load town: " + townName + " - " + e.getMessage());
            return null;
        }
    }

    @Override
    public Collection<Town> loadAllTowns() {
        List<Town> towns = new ArrayList<>();

        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .filter(p -> !p.getFileName().toString().endsWith(".bak"))
                    .forEach(file -> {
                        Town town = loadTownFile(file);
                        if (town != null) {
                            towns.add(town);
                        }
                    });
        } catch (IOException e) {
            System.err.println("[FlatfileStorage] Error listing town files: " + e.getMessage());
        }

        // Try to recover from backup files
        recoverFromBackups(towns);

        return towns;
    }

    private Town loadTownFile(Path file) {
        try {
            String json = Files.readString(file);

            if (json == null || json.trim().isEmpty()) {
                System.err.println("[FlatfileStorage] Empty file detected: " + file);
                moveToCorrupted(file, "empty");
                return null;
            }

            Town town = gson.fromJson(json, Town.class);
            if (town != null && town.getName() != null) {
                town.validateAfterLoad();
                return town;
            } else {
                moveToCorrupted(file, "invalid_data");
                return null;
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            moveToCorrupted(file, "json_syntax_error");
            return null;
        } catch (Exception e) {
            System.err.println("[FlatfileStorage] Failed to load town from " + file + ": " + e.getMessage());
            return null;
        }
    }

    private void recoverFromBackups(List<Town> loadedTowns) {
        Set<String> loadedNames = new HashSet<>();
        for (Town t : loadedTowns) {
            loadedNames.add(t.getName().toLowerCase());
        }

        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".bak"))
                    .forEach(backupFile -> {
                        String townName = backupFile.getFileName().toString().replace(".json.bak", "");
                        if (!loadedNames.contains(townName.toLowerCase())) {
                            try {
                                String json = Files.readString(backupFile);
                                Town town = gson.fromJson(json, Town.class);
                                if (town != null && town.getName() != null) {
                                    town.validateAfterLoad();
                                    loadedTowns.add(town);
                                    loadedNames.add(town.getName().toLowerCase());
                                    // Restore main file
                                    Path mainFile = townsDirectory.resolve(sanitize(town.getName()) + ".json");
                                    Files.copy(backupFile, mainFile, StandardCopyOption.REPLACE_EXISTING);
                                    System.out.println("[FlatfileStorage] Recovered town from backup: " + town.getName());
                                }
                            } catch (Exception e) {
                                // Failed to recover
                            }
                        }
                    });
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void saveTown(Town town) {
        Path file = townsDirectory.resolve(sanitize(town.getName()) + ".json");
        Path tempFile = townsDirectory.resolve(sanitize(town.getName()) + ".json.tmp");

        try {
            String json = gson.toJson(town);

            if (json == null || json.trim().isEmpty()) {
                System.err.println("[FlatfileStorage] ERROR: Empty JSON generated for town: " + town.getName());
                return;
            }

            // Write to temp file first
            Files.writeString(tempFile, json);

            // Verify temp file
            String verification = Files.readString(tempFile);
            if (!json.equals(verification)) {
                System.err.println("[FlatfileStorage] ERROR: Verification failed for town: " + town.getName());
                Files.deleteIfExists(tempFile);
                return;
            }

            // Backup existing file
            if (Files.exists(file)) {
                Path backupFile = townsDirectory.resolve(sanitize(town.getName()) + ".json.bak");
                try {
                    Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("[FlatfileStorage] Warning: Could not create backup for " + town.getName());
                }
            }

            // Atomic rename
            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Fallback for filesystems without atomic move
                Files.writeString(file, json);
                Files.deleteIfExists(tempFile);
            }

        } catch (IOException e) {
            System.err.println("[FlatfileStorage] ERROR saving town " + town.getName() + ": " + e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void deleteTown(String townName) {
        Path file = townsDirectory.resolve(sanitize(townName) + ".json");
        Path backupFile = townsDirectory.resolve(sanitize(townName) + ".json.bak");

        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(backupFile);
        } catch (IOException e) {
            System.err.println("[FlatfileStorage] Error deleting town files: " + e.getMessage());
        }
    }

    // ==================== INVITE OPERATIONS ====================

    @Override
    public Map<UUID, Set<String>> loadInvites() {
        Map<UUID, Set<String>> invites = new HashMap<>();

        if (Files.exists(indexFile)) {
            try {
                String json = Files.readString(indexFile);
                Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
                Map<String, Set<String>> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    for (Map.Entry<String, Set<String>> entry : loaded.entrySet()) {
                        try {
                            UUID playerId = UUID.fromString(entry.getKey());
                            invites.put(playerId, new HashSet<>(entry.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (IOException e) {
                System.err.println("[FlatfileStorage] Error loading invites: " + e.getMessage());
            }
        }

        return invites;
    }

    @Override
    public void saveInvites(Map<UUID, Set<String>> invites) {
        Path tempFile = townsDirectory.resolve("_index.json.tmp");

        Map<String, Set<String>> toSave = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
            toSave.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            String json = gson.toJson(toSave);
            Files.writeString(tempFile, json);
            Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[FlatfileStorage] ERROR saving invites: " + e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    // ==================== UTILITY ====================

    private void cleanupTempFiles() {
        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".tmp"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        } catch (IOException e) {
            // Ignore
        }
    }

    private void moveToCorrupted(Path file, String reason) {
        try {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newName = file.getFileName().toString().replace(".json", "") +
                    "_" + reason + "_" + timestamp + ".json";
            Path dest = corruptedDirectory.resolve(newName);
            Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[FlatfileStorage] Moved corrupted file to: " + dest);
        } catch (IOException e) {
            System.err.println("[FlatfileStorage] Failed to move corrupted file: " + e.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Get the towns directory path (for backups, etc.)
     */
    public Path getTownsDirectory() {
        return townsDirectory;
    }
}
