package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.managers.ClaimManager;

import java.util.UUID;

/**
 * ECS System that intercepts block use events (chests, doors, etc.) to protect claimed areas.
 */
public class BlockUseProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final ClaimManager claimManager;

    public BlockUseProtectionSystem(ClaimManager claimManager) {
        super(UseBlockEvent.Pre.class);
        this.claimManager = claimManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, UseBlockEvent.Pre event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        System.out.println("[LandClaims] UseBlockEvent.Pre fired at " + targetBlock);

        // Get player directly from the InteractionContext
        InteractionContext context = event.getContext();
        if (context == null) {
            System.out.println("[LandClaims] UseBlock: No context available");
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null) {
            System.out.println("[LandClaims] UseBlock: No entity ref in context");
            return;
        }

        // Get the Player component from the entity
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            System.out.println("[LandClaims] UseBlock: Entity is not a player");
            return;
        }

        UUID playerId = player.getUuid();
        String worldName = "default"; // TODO: Get actual world name

        System.out.println("[LandClaims] UseBlock: Player " + playerId + " at " + targetBlock);

        if (!claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ())) {
            System.out.println("[LandClaims] CANCELLING UseBlockEvent.Pre for player " + playerId);
            event.setCancelled(true);
        }
    }
}
