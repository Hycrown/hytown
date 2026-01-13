package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.managers.ClaimManager;

import java.util.UUID;

/**
 * ECS System that intercepts block place events to protect claimed areas.
 */
public class BlockPlaceProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final ClaimManager claimManager;

    public BlockPlaceProtectionSystem(ClaimManager claimManager) {
        super(PlaceBlockEvent.class);
        this.claimManager = claimManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, PlaceBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        System.out.println("[LandClaims] PlaceBlockEvent fired at " + targetBlock);

        // Get the entity that triggered this event
        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
        if (entityRef == null) {
            System.out.println("[LandClaims] PlaceBlock: No entity ref");
            return;
        }

        // Get the Player component from the entity
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            System.out.println("[LandClaims] PlaceBlock: Entity is not a player");
            return;
        }

        UUID playerId = player.getUuid();
        String worldName = "default"; // TODO: Get actual world name

        System.out.println("[LandClaims] PlaceBlock: Player " + playerId + " at " + targetBlock);

        if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
            System.out.println("[LandClaims] CANCELLING PlaceBlockEvent for player " + playerId);
            event.setCancelled(true);
        }
    }
}
