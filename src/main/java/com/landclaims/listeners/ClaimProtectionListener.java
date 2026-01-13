package com.landclaims.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.landclaims.LandClaims;
import com.landclaims.managers.ClaimManager;
import com.landclaims.util.ChunkUtil;

import java.util.UUID;

/**
 * Listens for block and interaction events to protect claimed areas.
 */
public class ClaimProtectionListener {
    private final LandClaims plugin;
    private final ClaimManager claimManager;

    public ClaimProtectionListener(LandClaims plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    /**
     * Register all protection event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        // Player interaction - use registerGlobal for keyed events
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);

        // Player join/leave for playtime tracking
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    /**
     * Main protection handler - PlayerInteractEvent has full player context.
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        UUID playerId = player.getUuid();
        String worldName = "default"; // TODO: Get actual world name from context

        // Check if this location is protected
        if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
            // Player cannot interact here - cancel the event
            event.setCancelled(true);
        }
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
}
