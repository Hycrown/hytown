package com.landclaims.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Holds all claims and trusted players for a single player.
 */
public class PlayerClaims {
    private final UUID owner;
    private final List<Claim> claims;
    private final Set<UUID> trustedPlayers;

    public PlayerClaims(UUID owner) {
        this.owner = owner;
        this.claims = new ArrayList<>();
        this.trustedPlayers = new HashSet<>();
    }

    public UUID getOwner() {
        return owner;
    }

    public List<Claim> getClaims() {
        return new ArrayList<>(claims);
    }

    public int getClaimCount() {
        return claims.size();
    }

    public void addClaim(Claim claim) {
        if (!hasClaim(claim.getWorld(), claim.getChunkX(), claim.getChunkZ())) {
            claims.add(claim);
        }
    }

    public boolean removeClaim(String world, int chunkX, int chunkZ) {
        return claims.removeIf(c -> c.getWorld().equals(world) && c.getChunkX() == chunkX && c.getChunkZ() == chunkZ);
    }

    public boolean hasClaim(String world, int chunkX, int chunkZ) {
        return claims.stream().anyMatch(c -> c.getWorld().equals(world) && c.getChunkX() == chunkX && c.getChunkZ() == chunkZ);
    }

    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    public void addTrustedPlayer(UUID playerId) {
        trustedPlayers.add(playerId);
    }

    public boolean removeTrustedPlayer(UUID playerId) {
        return trustedPlayers.remove(playerId);
    }

    public boolean isTrusted(UUID playerId) {
        return trustedPlayers.contains(playerId);
    }
}
