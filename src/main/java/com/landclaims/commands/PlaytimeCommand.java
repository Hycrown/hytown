package com.landclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.LandClaims;
import com.landclaims.data.PlayerClaims;
import com.landclaims.util.Messages;

import javax.annotation.Nonnull;

/**
 * /playtime - Show your playtime and available claim chunks.
 */
public class PlaytimeCommand extends AbstractPlayerCommand {
    private final LandClaims plugin;

    public PlaytimeCommand(LandClaims plugin) {
        super("playtime", "Show your playtime and available claims");
        this.plugin = plugin;
        requirePermission("landclaims.playtime");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        double hours = plugin.getPlaytimeManager().getTotalHours(playerData.getUuid());
        PlayerClaims claims = plugin.getClaimManager().getPlayerClaims(playerData.getUuid());
        int claimsUsed = claims.getClaimCount();
        int claimsAvailable = plugin.getClaimManager().getMaxClaims(playerData.getUuid());

        playerData.sendMessage(Messages.playtimeInfo(hours, claimsUsed, claimsAvailable));

        double hoursUntilNext = plugin.getClaimManager().getHoursUntilNextClaim(playerData.getUuid());
        if (hoursUntilNext > 0) {
            playerData.sendMessage(Messages.nextClaimIn(hoursUntilNext));
        }
    }
}
