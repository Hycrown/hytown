package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.listeners.ClaimProtectionListener;
import com.landclaims.listeners.ClaimProtectionListener.PlayerInteraction;
import com.landclaims.managers.ClaimManager;

import java.util.UUID;

/**
 * ECS System that intercepts block damage events to protect claimed areas.
 * This prevents mining progress on protected blocks.
 */
public class BlockDamageProtectionSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private final ClaimManager claimManager;

    public BlockDamageProtectionSystem(ClaimManager claimManager) {
        super(DamageBlockEvent.class);
        this.claimManager = claimManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, DamageBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        // Only log occasionally to avoid spam (damage events fire frequently)
        if (event.getCurrentDamage() < 0.1f) {
            System.out.println("[LandClaims] DamageBlockEvent at " + targetBlock + " damage=" + event.getDamage());
        }

        String blockKey = ClaimProtectionListener.getBlockKey(targetBlock);
        PlayerInteraction interaction = ClaimProtectionListener.getInteraction(blockKey);

        if (interaction != null) {
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                System.out.println("[LandClaims] CANCELLING DamageBlockEvent (tracked player)");
                event.setCancelled(true);
            }
        } else {
            interaction = ClaimProtectionListener.findNearbyInteraction(targetBlock);
            if (interaction != null) {
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    System.out.println("[LandClaims] CANCELLING DamageBlockEvent (nearby player)");
                    event.setCancelled(true);
                }
            } else {
                String worldName = "default";
                UUID owner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());
                if (owner != null) {
                    System.out.println("[LandClaims] CANCELLING DamageBlockEvent (no player, claimed)");
                    event.setCancelled(true);
                }
            }
        }
    }
}
