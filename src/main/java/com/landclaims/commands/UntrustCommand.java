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
import com.landclaims.util.Messages;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /untrust <player> - Remove trust from a player.
 * Accepts either a player name or UUID.
 */
public class UntrustCommand extends AbstractPlayerCommand {
    private final LandClaims plugin;
    private final RequiredArg<String> playerArg;

    public UntrustCommand(LandClaims plugin) {
        super("untrust", "Remove trust from a player");
        this.plugin = plugin;
        this.playerArg = withRequiredArg("player", "Player name or UUID to untrust", ArgTypes.STRING);
        requirePermission("landclaims.untrust");
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

        // Try to parse as UUID
        UUID targetId = null;
        try {
            targetId = UUID.fromString(playerInput);
        } catch (IllegalArgumentException e) {
            // Not a UUID - would need server player lookup by name
            playerData.sendMessage(Messages.playerNotFound(playerInput + " (use UUID for offline players)"));
            return;
        }

        boolean removed = plugin.getClaimManager().removeTrust(playerData.getUuid(), targetId);

        if (removed) {
            playerData.sendMessage(Messages.playerUntrusted(playerInput));
        } else {
            playerData.sendMessage(Messages.playerNotTrusted(playerInput));
        }
    }
}
