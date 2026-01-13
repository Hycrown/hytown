package com.landclaims.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.landclaims.util.ChunkUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent storage of claims using JSON files.
 */
public class ClaimStorage {
    private final Path claimsDirectory;
    private final Path indexFile;
    private final Gson gson;
    private final Map<UUID, PlayerClaims> cache;
    private final Map<String, Map<String, UUID>> claimIndex; // world -> (chunkKey -> ownerUUID)

    public ClaimStorage(Path dataDirectory) {
        this.claimsDirectory = dataDirectory.resolve("claims");
        this.indexFile = claimsDirectory.resolve("index.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new ConcurrentHashMap<>();
        this.claimIndex = new ConcurrentHashMap<>();

        try {
            Files.createDirectories(claimsDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        loadIndex();
    }

    private void loadIndex() {
        if (Files.exists(indexFile)) {
            try {
                String json = Files.readString(indexFile);
                Type type = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
                Map<String, Map<String, String>> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    for (Map.Entry<String, Map<String, String>> worldEntry : loaded.entrySet()) {
                        String world = worldEntry.getKey();
                        Map<String, UUID> worldClaims = new ConcurrentHashMap<>();
                        for (Map.Entry<String, String> claimEntry : worldEntry.getValue().entrySet()) {
                            try {
                                worldClaims.put(claimEntry.getKey(), UUID.fromString(claimEntry.getValue()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        claimIndex.put(world, worldClaims);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveIndex() {
        Map<String, Map<String, String>> toSave = new HashMap<>();
        for (Map.Entry<String, Map<String, UUID>> worldEntry : claimIndex.entrySet()) {
            Map<String, String> worldClaims = new HashMap<>();
            for (Map.Entry<String, UUID> claimEntry : worldEntry.getValue().entrySet()) {
                worldClaims.put(claimEntry.getKey(), claimEntry.getValue().toString());
            }
            toSave.put(worldEntry.getKey(), worldClaims);
        }

        try {
            Files.writeString(indexFile, gson.toJson(toSave));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PlayerClaims getPlayerClaims(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadPlayerClaims);
    }

    private PlayerClaims loadPlayerClaims(UUID playerId) {
        Path file = claimsDirectory.resolve(playerId.toString() + ".json");

        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                PlayerClaimsJson data = gson.fromJson(json, PlayerClaimsJson.class);

                PlayerClaims claims = new PlayerClaims(playerId);
                if (data != null) {
                    if (data.claims != null) {
                        for (ClaimJson c : data.claims) {
                            claims.addClaim(new Claim(c.world, c.chunkX, c.chunkZ, c.claimedAt));
                        }
                    }
                    if (data.trustedPlayers != null) {
                        for (String trusted : data.trustedPlayers) {
                            try {
                                claims.addTrustedPlayer(UUID.fromString(trusted));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
                return claims;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new PlayerClaims(playerId);
    }

    public void savePlayerClaims(UUID playerId) {
        PlayerClaims claims = cache.get(playerId);
        if (claims == null) return;

        Path file = claimsDirectory.resolve(playerId.toString() + ".json");

        PlayerClaimsJson data = new PlayerClaimsJson();
        data.claims = new ArrayList<>();
        data.trustedPlayers = new ArrayList<>();

        for (Claim claim : claims.getClaims()) {
            ClaimJson c = new ClaimJson();
            c.world = claim.getWorld();
            c.chunkX = claim.getChunkX();
            c.chunkZ = claim.getChunkZ();
            c.claimedAt = claim.getClaimedAt();
            data.claims.add(c);
        }

        for (UUID trusted : claims.getTrustedPlayers()) {
            data.trustedPlayers.add(trusted.toString());
        }

        try {
            Files.writeString(file, gson.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addClaim(UUID playerId, Claim claim) {
        PlayerClaims claims = getPlayerClaims(playerId);
        claims.addClaim(claim);

        // Update index
        String chunkKey = ChunkUtil.chunkKey(claim.getChunkX(), claim.getChunkZ());
        claimIndex.computeIfAbsent(claim.getWorld(), k -> new ConcurrentHashMap<>()).put(chunkKey, playerId);

        savePlayerClaims(playerId);
        saveIndex();
    }

    public void removeClaim(UUID playerId, String world, int chunkX, int chunkZ) {
        PlayerClaims claims = getPlayerClaims(playerId);
        claims.removeClaim(world, chunkX, chunkZ);

        // Update index
        String chunkKey = ChunkUtil.chunkKey(chunkX, chunkZ);
        Map<String, UUID> worldClaims = claimIndex.get(world);
        if (worldClaims != null) {
            worldClaims.remove(chunkKey);
        }

        savePlayerClaims(playerId);
        saveIndex();
    }

    /**
     * Gets the owner of a chunk, or null if unclaimed.
     */
    public UUID getClaimOwner(String world, int chunkX, int chunkZ) {
        Map<String, UUID> worldClaims = claimIndex.get(world);
        if (worldClaims == null) return null;

        String chunkKey = ChunkUtil.chunkKey(chunkX, chunkZ);
        return worldClaims.get(chunkKey);
    }

    /**
     * Checks if a chunk is claimed.
     */
    public boolean isClaimed(String world, int chunkX, int chunkZ) {
        return getClaimOwner(world, chunkX, chunkZ) != null;
    }

    public void saveAll() {
        for (UUID playerId : cache.keySet()) {
            savePlayerClaims(playerId);
        }
        saveIndex();
    }

    // JSON data classes
    private static class PlayerClaimsJson {
        List<ClaimJson> claims;
        List<String> trustedPlayers;
    }

    private static class ClaimJson {
        String world;
        int chunkX;
        int chunkZ;
        long claimedAt;
    }
}
