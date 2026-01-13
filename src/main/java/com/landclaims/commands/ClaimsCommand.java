package com.landclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.LandClaims;
import com.landclaims.data.Claim;
import com.landclaims.data.PlayerClaims;
import com.landclaims.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * /claims - List all your claims.
 */
public class ClaimsCommand extends AbstractPlayerCommand {
    private final LandClaims plugin;

    public ClaimsCommand(LandClaims plugin) {
        super("claims", "List all your claims");
        this.plugin = plugin;
        requirePermission("landclaims.list");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        PlayerClaims claims = plugin.getClaimManager().getPlayerClaims(playerData.getUuid());
        List<Claim> claimList = claims.getClaims();

        if (claimList.isEmpty()) {
            playerData.sendMessage(Messages.noClaims());
            return;
        }

        int max = plugin.getClaimManager().getMaxClaims(playerData.getUuid());
        playerData.sendMessage(Messages.claimsHeader(claimList.size(), max));

        for (Claim claim : claimList) {
            playerData.sendMessage(Messages.claimEntry(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()));
        }
    }
}
