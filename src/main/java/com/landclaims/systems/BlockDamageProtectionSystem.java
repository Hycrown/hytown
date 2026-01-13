package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

        // Get the entity that triggered this event
        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
        if (entityRef == null) {
            return;
        }

        // Get the Player component from the entity
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        String worldName = "default"; // TODO: Get actual world name

        if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
            // Only log on first damage attempt to reduce spam
            if (event.getCurrentDamage() < 0.1f) {
                System.out.println("[LandClaims] CANCELLING DamageBlockEvent for player " + playerId + " at " + targetBlock);
            }
            event.setCancelled(true);
        }
    }
}
