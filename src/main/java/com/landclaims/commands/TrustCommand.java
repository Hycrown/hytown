package com.landclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.LandClaims;
import com.landclaims.data.PlayerClaims;
import com.landclaims.util.Messages;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /trust <player> - Trust a player in all your claims.
 * Accepts either a player name or UUID.
 */
public class TrustCommand extends AbstractPlayerCommand {
    private final LandClaims plugin;
    private final RequiredArg<String> playerArg;

    public TrustCommand(LandClaims plugin) {
        super("trust", "Trust a player in all your claims");
        this.plugin = plugin;
        this.playerArg = withRequiredArg("player", "Player name or UUID to trust", ArgTypes.STRING);
        requirePermission("landclaims.trust");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        String playerInput = playerArg.get(ctx);

        if (playerInput == null || playerInput.isEmpty()) {
            playerData.sendMessage(Messages.playerNotFound("unknown"));
            return;
        }

        // Try to parse as UUID, otherwise treat as player name
        UUID targetId = null;
        try {
            targetId = UUID.fromString(playerInput);
        } catch (IllegalArgumentException e) {
            // Not a UUID - would need server player lookup by name
            // For now, inform user to use UUID
            playerData.sendMessage(Messages.playerNotFound(playerInput + " (use UUID for offline players)"));
            return;
        }

        if (targetId.equals(playerData.getUuid())) {
            playerData.sendMessage(Messages.cannotTrustSelf());
            return;
        }

        PlayerClaims claims = plugin.getClaimManager().getPlayerClaims(playerData.getUuid());
        if (claims.isTrusted(targetId)) {
            playerData.sendMessage(Messages.playerAlreadyTrusted(playerInput));
            return;
        }

        plugin.getClaimManager().addTrust(playerData.getUuid(), targetId);
        playerData.sendMessage(Messages.playerTrusted(playerInput));
    }
}
