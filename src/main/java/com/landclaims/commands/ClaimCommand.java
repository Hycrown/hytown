package com.landclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.LandClaims;
import com.landclaims.managers.ClaimManager;
import com.landclaims.util.ChunkUtil;
import com.landclaims.util.Messages;

import javax.annotation.Nonnull;

/**
 * /claim - Claim the chunk you're standing in.
 */
public class ClaimCommand extends AbstractPlayerCommand {
    private final LandClaims plugin;

    public ClaimCommand(LandClaims plugin) {
        super("claim", "Claim the chunk you're standing in");
        this.plugin = plugin;
        requirePermission("landclaims.claim");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d position = transform.getPosition();
        String worldName = world.getName();

        ClaimManager.ClaimResult result = plugin.getClaimManager().claimChunk(
                playerData.getUuid(),
                worldName,
                position.getX(),
                position.getZ()
        );

        int chunkX = ChunkUtil.toChunkX(position.getX());
        int chunkZ = ChunkUtil.toChunkZ(position.getZ());

        switch (result) {
            case SUCCESS:
                playerData.sendMessage(Messages.chunkClaimed(chunkX, chunkZ));
                break;
            case ALREADY_OWN:
                playerData.sendMessage(Messages.chunkAlreadyClaimed());
                break;
            case CLAIMED_BY_OTHER:
                playerData.sendMessage(Messages.chunkClaimedByOther("another player"));
                break;
            case LIMIT_REACHED:
                int current = plugin.getClaimManager().getPlayerClaims(playerData.getUuid()).getClaimCount();
                int max = plugin.getClaimManager().getMaxClaims(playerData.getUuid());
                playerData.sendMessage(Messages.claimLimitReached(current, max));
                break;
        }
    }
}
