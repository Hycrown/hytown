package com.easyclaims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.easyclaims.EasyClaims;
import com.easyclaims.config.PluginConfig;

import javax.annotation.Nonnull;
import java.awt.Color;

/**
 * /easyclaims - Admin command to configure the plugin in-game.
 *
 * Usage:
 *   /easyclaims config              - Show current settings
 *   /easyclaims set <key> <value>   - Change a setting
 *   /easyclaims reload              - Reload config from file
 */
public class EasyClaimsCommand extends AbstractPlayerCommand {
    private final EasyClaims plugin;
    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> keyArg;
    private final OptionalArg<Integer> valueArg;

    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color AQUA = new Color(85, 255, 255);

    public EasyClaimsCommand(EasyClaims plugin) {
        super("easyclaims", "Admin commands for EasyClaims plugin");
        this.plugin = plugin;
        this.actionArg = withOptionalArg("action", "config, set, or reload", ArgTypes.STRING);
        this.keyArg = withOptionalArg("key", "Setting name", ArgTypes.STRING);
        this.valueArg = withOptionalArg("value", "New value", ArgTypes.INTEGER);
        requirePermission("easyclaims.admin");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        String action = actionArg.get(ctx);
        PluginConfig config = plugin.getPluginConfig();

        if (action == null || action.equalsIgnoreCase("config")) {
            showConfig(playerData, config);
        } else if (action.equalsIgnoreCase("set")) {
            handleSet(playerData, config, ctx);
        } else if (action.equalsIgnoreCase("reload")) {
            handleReload(playerData, config);
        } else {
            showHelp(playerData);
        }
    }

    private void showConfig(PlayerRef playerData, PluginConfig config) {
        playerData.sendMessage(Message.raw("=== EasyClaims Settings ===").color(GOLD));
        playerData.sendMessage(Message.raw("  startingClaims: " + config.getStartingClaims()).color(AQUA));
        playerData.sendMessage(Message.raw("  claimsPerHour: " + config.getClaimsPerHour()).color(AQUA));
        playerData.sendMessage(Message.raw("  maxClaims: " + config.getMaxClaims()).color(AQUA));
        playerData.sendMessage(Message.raw("  playtimeSaveInterval: " + config.getPlaytimeSaveInterval() + "s").color(GRAY));
        playerData.sendMessage(Message.raw("").color(GRAY));
        playerData.sendMessage(Message.raw("Formula: startingClaims + (hours * claimsPerHour), max maxClaims").color(GRAY));
        playerData.sendMessage(Message.raw("Use /easyclaims set <key> <value> to change").color(GRAY));
    }

    private void handleSet(PlayerRef playerData, PluginConfig config, CommandContext ctx) {
        String key = keyArg.get(ctx);
        Integer value = valueArg.get(ctx);

        if (key == null || value == null) {
            playerData.sendMessage(Message.raw("Usage: /easyclaims set <key> <value>").color(RED));
            playerData.sendMessage(Message.raw("Keys: startingClaims, claimsPerHour, maxClaims").color(GRAY));
            return;
        }

        switch (key.toLowerCase()) {
            case "startingclaims":
                config.setStartingClaims(value);
                playerData.sendMessage(Message.raw("startingClaims set to " + value).color(GREEN));
                break;
            case "claimsperhour":
                config.setClaimsPerHour(value);
                playerData.sendMessage(Message.raw("claimsPerHour set to " + value).color(GREEN));
                break;
            case "maxclaims":
                config.setMaxClaims(value);
                playerData.sendMessage(Message.raw("maxClaims set to " + value).color(GREEN));
                break;
            case "playtimesaveinterval":
                config.setPlaytimeSaveInterval(value);
                playerData.sendMessage(Message.raw("playtimeSaveInterval set to " + value + "s").color(GREEN));
                break;
            default:
                playerData.sendMessage(Message.raw("Unknown setting: " + key).color(RED));
                playerData.sendMessage(Message.raw("Valid keys: startingClaims, claimsPerHour, maxClaims").color(GRAY));
        }
    }

    private void handleReload(PlayerRef playerData, PluginConfig config) {
        config.reload();
        playerData.sendMessage(Message.raw("Configuration reloaded from file!").color(GREEN));
        showConfig(playerData, config);
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("=== EasyClaims Admin Commands ===").color(GOLD));
        playerData.sendMessage(Message.raw("/easyclaims config - Show current settings").color(GRAY));
        playerData.sendMessage(Message.raw("/easyclaims set <key> <value> - Change a setting").color(GRAY));
        playerData.sendMessage(Message.raw("/easyclaims reload - Reload config from file").color(GRAY));
    }
}
