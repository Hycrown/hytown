package com.easyclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.easyclaims.EasyClaims;
import com.easyclaims.data.Claim;
import com.easyclaims.data.PlayerClaims;
import com.easyclaims.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * /claims - List all your claims.
 */
public class ClaimsCommand extends AbstractPlayerCommand {
    private final EasyClaims plugin;

    public ClaimsCommand(EasyClaims plugin) {
        super("claims", "List all your claims");
        this.plugin = plugin;
        requirePermission("easyclaims.list");
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
            playerData.sendMessage(Messages.claimEntryWithCoords(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()));
        }
    }
}
