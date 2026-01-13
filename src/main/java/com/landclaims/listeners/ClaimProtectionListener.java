package com.landclaims.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import com.landclaims.LandClaims;
import com.landclaims.managers.ClaimManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for block and interaction events to protect claimed areas.
 *
 * Protection strategy:
 * 1. PlayerInteractEvent - Track which player is interacting with which block, cancel if protected
 * 2. UseBlockEvent.Pre - Prevent using blocks (chests, doors, etc.) in protected areas
 * 3. DamageBlockEvent - Prevent block damage in protected areas (blocks mining)
 * 4. BreakBlockEvent - Final safety net to prevent block breaking
 * 5. PlaceBlockEvent - Prevent block placing in protected areas
 */
public class ClaimProtectionListener {
    private final LandClaims plugin;
    private final ClaimManager claimManager;

    // Track player interactions to correlate with ECS events
    // Key: "x,y,z" block position, Value: PlayerInteraction data
    private final Map<String, PlayerInteraction> pendingInteractions = new ConcurrentHashMap<>();

    // Also track by player UUID for cases where block position differs (e.g., placing)
    // Key: player UUID, Value: PlayerInteraction with last interacted position
    private final Map<UUID, PlayerInteraction> playerLastInteraction = new ConcurrentHashMap<>();

    // How long to keep interaction data (ms)
    private static final long INTERACTION_TIMEOUT_MS = 5000;

    public ClaimProtectionListener(LandClaims plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    /**
     * Register all protection event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        // Player interaction - track who is interacting with what and cancel if protected
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);

        // ECS block events - these fire when blocks are actually modified
        eventRegistry.registerGlobal(UseBlockEvent.Pre.class, this::onUseBlock);
        eventRegistry.registerGlobal(DamageBlockEvent.class, this::onDamageBlock);
        eventRegistry.registerGlobal(BreakBlockEvent.class, this::onBreakBlock);
        eventRegistry.registerGlobal(PlaceBlockEvent.class, this::onPlaceBlock);

        // Player join/leave for playtime tracking
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    /**
     * Track player interactions and cancel if in protected area.
     * This fires BEFORE ECS block events.
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        UUID playerId = player.getUuid();
        String worldName = "default";
        InteractionType actionType = event.getActionType();

        // Track this interaction for ECS event correlation
        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction interaction = new PlayerInteraction(playerId, worldName, targetBlock, System.currentTimeMillis());
        pendingInteractions.put(blockKey, interaction);
        playerLastInteraction.put(playerId, interaction);

        // Clean up old interactions periodically
        cleanupOldInteractions();

        // Check if this location is protected
        if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent using blocks (chests, doors, buttons, etc.) in protected areas.
     */
    private void onUseBlock(UseBlockEvent.Pre event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        // Try to get player from interaction context
        InteractionContext context = event.getContext();
        UUID playerId = getPlayerFromContext(context);

        if (playerId != null) {
            String worldName = "default";
            if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
                event.setCancelled(true);
            }
        } else {
            // Fallback: check tracked interactions
            String blockKey = getBlockKey(targetBlock);
            PlayerInteraction interaction = pendingInteractions.get(blockKey);

            if (interaction != null && !interaction.isExpired()) {
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    event.setCancelled(true);
                }
            } else {
                // No player info - block if claimed (conservative)
                String worldName = "default";
                if (isProtectedWithoutAccess(worldName, targetBlock)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Prevent block damage in protected areas.
     */
    private void onDamageBlock(DamageBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction interaction = pendingInteractions.get(blockKey);

        if (interaction != null && !interaction.isExpired()) {
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                event.setCancelled(true);
            }
        } else {
            // Try to find any recent interaction near this block
            interaction = findNearbyInteraction(targetBlock);
            if (interaction != null) {
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    event.setCancelled(true);
                }
            } else {
                // No player info - block if claimed
                String worldName = "default";
                if (isProtectedWithoutAccess(worldName, targetBlock)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Final safety net - prevent block breaking in protected areas.
     */
    private void onBreakBlock(BreakBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction interaction = pendingInteractions.get(blockKey);

        if (interaction != null && !interaction.isExpired()) {
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                event.setCancelled(true);
            }
            pendingInteractions.remove(blockKey);
        } else {
            interaction = findNearbyInteraction(targetBlock);
            if (interaction != null) {
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    event.setCancelled(true);
                }
            } else {
                String worldName = "default";
                if (isProtectedWithoutAccess(worldName, targetBlock)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Prevent block placing in protected areas.
     */
    private void onPlaceBlock(PlaceBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction interaction = pendingInteractions.get(blockKey);

        if (interaction == null || interaction.isExpired()) {
            interaction = findNearbyInteraction(targetBlock);
        }

        if (interaction != null && !interaction.isExpired()) {
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                event.setCancelled(true);
            }
        } else {
            String worldName = "default";
            if (isProtectedWithoutAccess(worldName, targetBlock)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Try to extract player UUID from an InteractionContext.
     */
    private UUID getPlayerFromContext(InteractionContext context) {
        if (context == null) return null;

        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return null;

            // The entity ref might give us access to player data
            // This is a best-effort attempt
            Ref<EntityStore> owningRef = context.getOwningEntity();
            if (owningRef != null) {
                // Try to get player component - this may not work directly
                // but we store the ref for potential future use
            }
        } catch (Exception e) {
            // Context access failed, fall back to tracking
        }

        return null;
    }

    /**
     * Check if a block is in a claimed area.
     * Returns true if it should be blocked (claimed and no known accessor).
     */
    private boolean isProtectedWithoutAccess(String worldName, Vector3i block) {
        UUID owner = claimManager.getOwnerAt(worldName, block.getX(), block.getZ());
        return owner != null;
    }

    /**
     * Find a player interaction that was on or near a block.
     */
    private PlayerInteraction findNearbyInteraction(Vector3i targetBlock) {
        // First check exact match
        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction exact = pendingInteractions.get(blockKey);
        if (exact != null && !exact.isExpired()) {
            return exact;
        }

        // Check all recent player interactions to find one adjacent to this block
        for (PlayerInteraction interaction : playerLastInteraction.values()) {
            if (interaction.isExpired()) continue;
            if (interaction.blockPos == null) continue;

            int dx = Math.abs(targetBlock.getX() - interaction.blockPos.getX());
            int dy = Math.abs(targetBlock.getY() - interaction.blockPos.getY());
            int dz = Math.abs(targetBlock.getZ() - interaction.blockPos.getZ());

            if (dx <= 1 && dy <= 1 && dz <= 1) {
                return interaction;
            }
        }
        return null;
    }

    private String getBlockKey(Vector3i pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void cleanupOldInteractions() {
        long now = System.currentTimeMillis();
        pendingInteractions.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > INTERACTION_TIMEOUT_MS);
        playerLastInteraction.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > INTERACTION_TIMEOUT_MS);
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef != null) {
            plugin.onPlayerJoin(playerRef.getUuid());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef != null) {
            plugin.onPlayerLeave(playerRef.getUuid());
        }
    }

    private static class PlayerInteraction {
        final UUID playerId;
        final String worldName;
        final Vector3i blockPos;
        final long timestamp;

        PlayerInteraction(UUID playerId, String worldName, Vector3i blockPos, long timestamp) {
            this.playerId = playerId;
            this.worldName = worldName;
            this.blockPos = blockPos;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > INTERACTION_TIMEOUT_MS;
        }
    }
}
