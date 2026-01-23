package com.hytown.data;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a named road owned by a town.
 * Roads are created by /town claimroad and can span thousands of chunks in a direction.
 * Each road has an NPC "controller" that appears as the owner when entering road territory.
 */
public class TownRoad {

    private String name;           // User-given name (e.g., "MainRoad")
    private String direction;      // "north", "south", "east", "west", or "all"
    private int originChunkX;      // Starting chunk X
    private int originChunkZ;      // Starting chunk Z
    private String worldName;      // World the road is in
    private Set<String> claimKeys; // All claim keys belonging to this road
    private long createdTime;      // When the road was created
    private UUID npcControllerId;  // NPC that "controls" this road

    public TownRoad() {
        this.claimKeys = new HashSet<>();
        this.createdTime = System.currentTimeMillis();
    }

    public TownRoad(String name, String direction, String worldName, int originChunkX, int originChunkZ) {
        this();
        this.name = name;
        this.direction = direction;
        this.worldName = worldName;
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        // Generate NPC controller UUID from the display name
        this.npcControllerId = generateNpcId(getDisplayName());
    }

    /**
     * Generates a deterministic NPC UUID for a road name.
     * Uses "ROAD:" prefix to avoid collision with player/town NPCs.
     */
    public static UUID generateNpcId(String displayName) {
        return UUID.nameUUIDFromBytes(("ROAD:" + displayName).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the full display name with direction prefix.
     * Example: "North MainRoad" or "East TradeRoute"
     */
    public String getDisplayName() {
        String dirPrefix = direction.substring(0, 1).toUpperCase() + direction.substring(1).toLowerCase();
        if (direction.equalsIgnoreCase("all")) {
            dirPrefix = "Cross";
        }
        return dirPrefix + " " + name;
    }

    /**
     * Gets the raw name without direction prefix.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public int getOriginChunkX() {
        return originChunkX;
    }

    public void setOriginChunkX(int originChunkX) {
        this.originChunkX = originChunkX;
    }

    public int getOriginChunkZ() {
        return originChunkZ;
    }

    public void setOriginChunkZ(int originChunkZ) {
        this.originChunkZ = originChunkZ;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Set<String> getClaimKeys() {
        return claimKeys;
    }

    public void setClaimKeys(Set<String> claimKeys) {
        this.claimKeys = claimKeys;
    }

    public void addClaimKey(String claimKey) {
        this.claimKeys.add(claimKey);
    }

    public int getChunkCount() {
        return claimKeys.size();
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    /**
     * Checks if a claim key belongs to this road.
     */
    public boolean containsClaim(String claimKey) {
        return claimKeys.contains(claimKey);
    }

    /**
     * Gets the NPC controller UUID for this road.
     */
    public UUID getNpcControllerId() {
        // Regenerate if null (for backwards compatibility)
        if (npcControllerId == null) {
            npcControllerId = generateNpcId(getDisplayName());
        }
        return npcControllerId;
    }

    public void setNpcControllerId(UUID npcControllerId) {
        this.npcControllerId = npcControllerId;
    }

    /**
     * Gets the NPC controller display name (same as road display name).
     * This appears when players enter the road territory.
     */
    public String getNpcControllerName() {
        return getDisplayName();
    }

    /**
     * Updates the NPC controller ID after a name change.
     */
    public void regenerateNpcId() {
        this.npcControllerId = generateNpcId(getDisplayName());
    }
}
