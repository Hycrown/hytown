package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.listeners.ClaimProtectionListener;
import com.landclaims.listeners.ClaimProtectionListener.PlayerInteraction;
import com.landclaims.managers.ClaimManager;

import java.util.UUID;

/**
 * ECS System that intercepts block break events to protect claimed areas.
 */
public class BlockBreakProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final ClaimManager claimManager;

    public BlockBreakProtectionSystem(ClaimManager claimManager) {
        super(BreakBlockEvent.class);
        this.claimManager = claimManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        System.out.println("[LandClaims] BreakBlockEvent fired at " + targetBlock);

        String blockKey = ClaimProtectionListener.getBlockKey(targetBlock);
        PlayerInteraction interaction = ClaimProtectionListener.getInteraction(blockKey);

        if (interaction != null) {
            System.out.println("[LandClaims] BreakBlock: Found tracked player " + interaction.playerId);
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                System.out.println("[LandClaims] CANCELLING BreakBlockEvent (tracked player)");
                event.setCancelled(true);
            }
            ClaimProtectionListener.removeInteraction(blockKey);
        } else {
            interaction = ClaimProtectionListener.findNearbyInteraction(targetBlock);
            if (interaction != null) {
                System.out.println("[LandClaims] BreakBlock: Found nearby player " + interaction.playerId);
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    System.out.println("[LandClaims] CANCELLING BreakBlockEvent (nearby player)");
                    event.setCancelled(true);
                }
            } else {
                String worldName = "default";
                UUID owner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());
                System.out.println("[LandClaims] BreakBlock: No player tracked, owner=" + owner);
                if (owner != null) {
                    System.out.println("[LandClaims] CANCELLING BreakBlockEvent (no player, claimed chunk)");
                    event.setCancelled(true);
                }
            }
        }
    }
}
