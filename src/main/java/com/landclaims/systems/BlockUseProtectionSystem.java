package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.listeners.ClaimProtectionListener;
import com.landclaims.listeners.ClaimProtectionListener.PlayerInteraction;
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

        String blockKey = ClaimProtectionListener.getBlockKey(targetBlock);
        PlayerInteraction interaction = ClaimProtectionListener.getInteraction(blockKey);

        if (interaction != null) {
            System.out.println("[LandClaims] UseBlock: Found tracked player " + interaction.playerId);
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                System.out.println("[LandClaims] CANCELLING UseBlockEvent.Pre");
                event.setCancelled(true);
            }
        } else {
            interaction = ClaimProtectionListener.findNearbyInteraction(targetBlock);
            if (interaction != null) {
                System.out.println("[LandClaims] UseBlock: Found nearby player " + interaction.playerId);
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    System.out.println("[LandClaims] CANCELLING UseBlockEvent.Pre (nearby)");
                    event.setCancelled(true);
                }
            } else {
                String worldName = "default";
                UUID owner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());
                System.out.println("[LandClaims] UseBlock: No player tracked, owner=" + owner);
                if (owner != null) {
                    System.out.println("[LandClaims] CANCELLING UseBlockEvent.Pre (no player, claimed)");
                    event.setCancelled(true);
                }
            }
        }
    }
}
