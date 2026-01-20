package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.events.*;
import com.hytown.gui.TownAdminGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

/**
 * /townadmin command - Admin commands for managing towns.
 */
public class TownyAdminCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color YELLOW = new Color(255, 255, 85);

    public TownyAdminCommand(HyTown plugin) {
        super("townadmin", "[Admin] Manage towns - delete towns, set balances, reload config, wilderness protection. Requires admin permission");
        addAliases("ta", "townyadmin");
        requirePermission("hytown.admin");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && (allArgs[0].equalsIgnoreCase("townadmin") || allArgs[0].equalsIgnoreCase("townyadmin") || allArgs[0].equalsIgnoreCase("ta"))) {
            String[] args = new String[allArgs.length - 1];
            System.arraycopy(allArgs, 1, args, 0, allArgs.length - 1);
            return args;
        }
        return allArgs;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerRef, @Nonnull PlayerRef playerData, @Nonnull World world) {

        String[] args = parseArgs(ctx);
        if (args.length == 0) {
            showHelp(playerData);
            return;
        }

        String action = args[0];
        String arg1 = args.length > 1 ? args[1] : null;
        String arg2 = args.length > 2 ? args[2] : null;
        String arg3 = args.length > 3 ? args[3] : null;

        switch (action.toLowerCase()) {
            case "gui", "menu" -> handleGui(store, playerRef, playerData, world);
            case "reload" -> handleReload(playerData);
            case "town" -> handleTown(playerData, arg1, arg2, arg3);
            case "wild" -> handleWild(playerData, arg1, arg2);
            case "debug" -> handleDebug(playerData);
            case "save" -> handleSave(playerData);
            case "set" -> handleSet(playerData, arg1, arg2);
            case "restore" -> handleRestore(playerData, arg1, arg2);
            case "backups" -> handleBackups(playerData, arg1);
            case "backup" -> handleBackupNow(playerData);
            case "player" -> handlePlayer(playerData, arg1, arg2);
            case "spawn" -> handleSpawn(store, playerRef, playerData, world, arg1);
            case "storage" -> handleStorage(playerData, arg1);
            default -> showHelp(playerData);
        }
    }

    private void handleGui(Store<EntityStore> store, Ref<EntityStore> playerRef,
                           PlayerRef playerData, World world) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            TownAdminGui.openFor(plugin, player, playerRef, store, world);
        }
    }

    private void handleReload(PlayerRef playerData) {
        plugin.getPluginConfig().reload();
        playerData.sendMessage(Message.raw("Configuration reloaded!").color(GREEN));
    }

    private void handleTown(PlayerRef playerData, String townName, String subAction, String arg) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin town <name> [action]").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTown(townName);

        if (town == null) {
            playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
            return;
        }

        if (subAction == null || subAction.isEmpty()) {
            playerData.sendMessage(Message.raw("Town: " + town.getName()).color(GOLD));
            playerData.sendMessage(Message.raw("Mayor: " + town.getMayorName()).color(WHITE));
            playerData.sendMessage(Message.raw("Residents: " + town.getResidentCount()).color(WHITE));
            playerData.sendMessage(Message.raw("Claims: " + town.getClaimCount()).color(WHITE));
            playerData.sendMessage(Message.raw("Balance: $" + String.format("%.2f", town.getBalance())).color(WHITE));
            return;
        }

        switch (subAction.toLowerCase()) {
            case "delete" -> {
                // Fire TownDeleteEvent BEFORE deletion (so listeners can still access town data)
                TownDeleteEvent deleteEvent = new TownDeleteEvent(town, playerData.getUuid(), playerData.getUsername(), true);
                plugin.getEventBus().fire(deleteEvent);

                // Fire TownLeaveEvent for all residents
                for (UUID residentId : town.getResidents()) {
                    String residentName = town.getResidentName(residentId);
                    TownLeaveEvent leaveEvent = TownLeaveEvent.townDeleted(town, residentId, residentName);
                    plugin.getEventBus().fire(leaveEvent);
                }

                townStorage.deleteTown(town.getName());
                playerData.sendMessage(Message.raw("Town '" + town.getName() + "' deleted!").color(GREEN));
            }
            case "kick" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townadmin town " + townName + " kick <player>").color(RED));
                    return;
                }
                UUID targetId = null;
                String targetName = arg;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(arg)) {
                        targetId = entry.getKey();
                        targetName = entry.getValue();
                        break;
                    }
                }
                if (targetId == null) {
                    playerData.sendMessage(Message.raw(arg + " is not in this town!").color(RED));
                    return;
                }

                // Fire TownLeaveEvent BEFORE removing
                TownLeaveEvent leaveEvent = TownLeaveEvent.adminKicked(town, targetId, targetName, playerData.getUuid(), playerData.getUsername());
                plugin.getEventBus().fire(leaveEvent);

                town.removeResident(targetId);
                townStorage.saveTown(town);
                townStorage.unindexPlayer(targetId);
                playerData.sendMessage(Message.raw("Removed " + targetName + " from " + town.getName()).color(GREEN));
            }
            case "setmayor" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townadmin town " + townName + " setmayor <player>").color(RED));
                    return;
                }
                UUID newMayorId = null;
                String newMayorName = arg;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(arg)) {
                        newMayorId = entry.getKey();
                        newMayorName = entry.getValue();
                        break;
                    }
                }
                if (newMayorId == null) {
                    playerData.sendMessage(Message.raw(arg + " is not in this town!").color(RED));
                    return;
                }

                // Store old mayor info for event
                UUID oldMayorId = town.getMayorId();
                String oldMayorName = town.getMayorName();

                town.setMayor(newMayorId, newMayorName);
                townStorage.saveTown(town);

                // Fire TownMayorChangeEvent
                TownMayorChangeEvent mayorEvent = new TownMayorChangeEvent(town, oldMayorId, oldMayorName, newMayorId, newMayorName, true);
                plugin.getEventBus().fire(mayorEvent);

                playerData.sendMessage(Message.raw(newMayorName + " is now mayor of " + town.getName()).color(GREEN));
            }
            case "rename" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townadmin town " + townName + " rename <newname>").color(RED));
                    return;
                }

                String oldName = town.getName();
                String newName = arg;

                // Validate new name
                if (newName.length() < 3 || newName.length() > 24) {
                    playerData.sendMessage(Message.raw("Town name must be 3-24 characters!").color(RED));
                    return;
                }
                if (!newName.matches("^[a-zA-Z0-9_-]+$")) {
                    playerData.sendMessage(Message.raw("Town name can only contain letters, numbers, _ and -").color(RED));
                    return;
                }

                // Check if new name already exists
                if (townStorage.townExists(newName) && !oldName.equalsIgnoreCase(newName)) {
                    playerData.sendMessage(Message.raw("A town with that name already exists!").color(RED));
                    return;
                }

                // Perform the rename
                if (!townStorage.renameTown(oldName, newName)) {
                    playerData.sendMessage(Message.raw("Failed to rename town!").color(RED));
                    return;
                }

                // Get the updated town object
                Town renamedTown = townStorage.getTown(newName);

                // Fire TownRenameEvent
                TownRenameEvent renameEvent = new TownRenameEvent(renamedTown, oldName, newName, playerData.getUuid(), playerData.getUsername());
                plugin.getEventBus().fire(renameEvent);

                playerData.sendMessage(Message.raw("Town renamed from '" + oldName + "' to '" + newName + "'!").color(GREEN));
            }
            case "setbalance" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townadmin town " + townName + " setbalance <amount>").color(RED));
                    return;
                }
                try {
                    double amount = Double.parseDouble(arg);
                    town.setBalance(amount);
                    townStorage.saveTown(town);
                    playerData.sendMessage(Message.raw("Set balance to $" + String.format("%.2f", amount)).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            default -> playerData.sendMessage(Message.raw("Unknown action: " + subAction).color(RED));
        }
    }

    private void handleWild(PlayerRef playerData, String subAction, String arg) {
        if (subAction == null || subAction.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin wild <toggle|sety|info>").color(RED));
            return;
        }

        var config = plugin.getPluginConfig();

        switch (subAction.toLowerCase()) {
            case "toggle" -> {
                boolean current = config.isWildProtectionEnabled();
                config.setWildProtectionEnabled(!current);
                playerData.sendMessage(Message.raw("Wild protection " + (!current ? "ENABLED" : "DISABLED")).color(GREEN));
            }
            case "sety" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townadmin wild sety <y-level>").color(RED));
                    return;
                }
                try {
                    int y = Integer.parseInt(arg);
                    config.setWildProtectionMinY(y);
                    playerData.sendMessage(Message.raw("Wild protection Y-level set to " + y).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid Y level!").color(RED));
                }
            }
            case "info" -> {
                playerData.sendMessage(Message.raw("========== Wild Protection ==========").color(GOLD));
                playerData.sendMessage(Message.raw("Enabled: " + config.isWildProtectionEnabled()).color(WHITE));
                playerData.sendMessage(Message.raw("Min Y-Level: " + config.getWildProtectionMinY()).color(WHITE));
                playerData.sendMessage(Message.raw("Destroy Below: " + config.isWildDestroyBelowAllowed()).color(WHITE));
                playerData.sendMessage(Message.raw("Build Below: " + config.isWildBuildBelowAllowed()).color(WHITE));
            }
            default -> playerData.sendMessage(Message.raw("Unknown action: " + subAction).color(RED));
        }
    }

    private void handleDebug(PlayerRef playerData) {
        TownStorage townStorage = plugin.getTownStorage();

        playerData.sendMessage(Message.raw("========== Towny Debug ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Towns: " + townStorage.getTownCount()).color(WHITE));

        int totalClaims = 0, totalResidents = 0;
        for (Town town : townStorage.getAllTowns()) {
            totalClaims += town.getClaimCount();
            totalResidents += town.getResidentCount();
        }
        playerData.sendMessage(Message.raw("Total Claims (from towns): " + totalClaims).color(WHITE));
        playerData.sendMessage(Message.raw("Claim Index Size: " + townStorage.getClaimIndexSize()).color(WHITE));
        playerData.sendMessage(Message.raw("Total Residents: " + totalResidents).color(WHITE));

        var config = plugin.getPluginConfig();
        playerData.sendMessage(Message.raw("Town Creation Cost: $" + config.getTownCreationCost()).color(GRAY));
        playerData.sendMessage(Message.raw("Wild Protection: " + config.isWildProtectionEnabled() +
                " (Y>" + config.getWildProtectionMinY() + ")").color(GRAY));
    }

    private void handleSave(PlayerRef playerData) {
        plugin.getTownStorage().saveAll();
        plugin.getClaimStorage().saveAll();
        playerData.sendMessage(Message.raw("All data saved!").color(GREEN));
    }

    private void handleSet(PlayerRef playerData, String setting, String value) {
        if (setting == null || setting.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin set <towncost|claimcost|wildminy> <value>").color(RED));
            return;
        }

        var config = plugin.getPluginConfig();

        switch (setting.toLowerCase()) {
            case "towncost" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Town creation cost: $" + config.getTownCreationCost()).color(WHITE));
                    return;
                }
                try {
                    double cost = Double.parseDouble(value);
                    config.setTownCreationCost(cost);
                    playerData.sendMessage(Message.raw("Town creation cost set to $" + cost).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            case "claimcost" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Town claim cost: $" + config.getTownClaimCost()).color(WHITE));
                    return;
                }
                try {
                    double cost = Double.parseDouble(value);
                    config.setTownClaimCost(cost);
                    playerData.sendMessage(Message.raw("Town claim cost set to $" + cost).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            case "wildminy" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Wild protection min Y: " + config.getWildProtectionMinY()).color(WHITE));
                    return;
                }
                try {
                    int y = Integer.parseInt(value);
                    config.setWildProtectionMinY(y);
                    playerData.sendMessage(Message.raw("Wild protection min Y set to " + y).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid Y level!").color(RED));
                }
            }
            default -> playerData.sendMessage(Message.raw("Unknown setting: " + setting).color(RED));
        }
    }

    private void handleRestore(PlayerRef playerData, String townName, String dateStr) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin restore <townname> [date]").color(RED));
            playerData.sendMessage(Message.raw("  Without date: Restores from .bak file").color(GRAY));
            playerData.sendMessage(Message.raw("  With date: Restores from daily backup (e.g., 2026-01-16)").color(GRAY));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();

        if (dateStr != null && !dateStr.isEmpty()) {
            // Restore from daily backup
            if (townStorage.restoreTownFromDailyBackup(townName, dateStr)) {
                playerData.sendMessage(Message.raw("Restored '" + townName + "' from backup " + dateStr + "!").color(GREEN));
            } else {
                playerData.sendMessage(Message.raw("Failed to restore! Check if backup exists.").color(RED));
                playerData.sendMessage(Message.raw("Use /townadmin backups to list available backups.").color(GRAY));
            }
        } else {
            // Restore from .bak file
            if (!townStorage.hasBackup(townName)) {
                playerData.sendMessage(Message.raw("No backup file exists for '" + townName + "'!").color(RED));
                return;
            }

            if (townStorage.restoreTownFromBackup(townName)) {
                playerData.sendMessage(Message.raw("Restored '" + townName + "' from backup file!").color(GREEN));
            } else {
                playerData.sendMessage(Message.raw("Failed to restore from backup!").color(RED));
            }
        }
    }

    private void handleBackups(PlayerRef playerData, String townName) {
        TownStorage townStorage = plugin.getTownStorage();
        java.util.List<String> backups = townStorage.listBackups();

        if (backups.isEmpty()) {
            playerData.sendMessage(Message.raw("No daily backups available.").color(YELLOW));
            return;
        }

        playerData.sendMessage(Message.raw("========== Available Backups ==========").color(GOLD));
        for (String backup : backups) {
            playerData.sendMessage(Message.raw("  " + backup).color(WHITE));
        }
        playerData.sendMessage(Message.raw("Use /townadmin restore <town> <date> to restore").color(GRAY));

        // If town specified, check for .bak file
        if (townName != null && !townName.isEmpty()) {
            if (townStorage.hasBackup(townName)) {
                playerData.sendMessage(Message.raw("Town '" + townName + "' has a .bak backup file.").color(GREEN));
            } else {
                playerData.sendMessage(Message.raw("Town '" + townName + "' has no .bak backup file.").color(YELLOW));
            }
        }
    }

    private void handleBackupNow(PlayerRef playerData) {
        plugin.getTownStorage().createBackup();
        playerData.sendMessage(Message.raw("Backup created!").color(GREEN));
    }

    private void handleStorage(PlayerRef playerData, String action) {
        if (action == null || action.isEmpty()) {
            // Show status
            playerData.sendMessage(Message.raw("=== Storage Status ===").color(GOLD));
            playerData.sendMessage(Message.raw(plugin.getStorageStatus()).color(WHITE));
            playerData.sendMessage(Message.raw("").color(WHITE));
            playerData.sendMessage(Message.raw("Commands:").color(GRAY));
            playerData.sendMessage(Message.raw("  /townadmin storage status - Show current status").color(GRAY));
            playerData.sendMessage(Message.raw("  /townadmin storage migrate - Force save all data to save provider").color(GRAY));
            return;
        }

        switch (action.toLowerCase()) {
            case "status" -> {
                playerData.sendMessage(Message.raw("=== Storage Status ===").color(GOLD));
                playerData.sendMessage(Message.raw(plugin.getStorageStatus()).color(WHITE));

                // Show config values
                var storageConfig = plugin.getPluginConfig().getStorageConfig();
                playerData.sendMessage(Message.raw("").color(WHITE));
                playerData.sendMessage(Message.raw("Config:").color(GRAY));
                playerData.sendMessage(Message.raw("  loadFrom: " + storageConfig.getLoadFrom()).color(GRAY));
                playerData.sendMessage(Message.raw("  saveTo: " + storageConfig.getSaveTo()).color(GRAY));

                if (storageConfig.needsSQL()) {
                    var sql = storageConfig.getSql();
                    playerData.sendMessage(Message.raw("  SQL: " + sql.getHost() + ":" + sql.getPort() + "/" + sql.getDatabase()).color(GRAY));
                }
                if (storageConfig.needsMongoDB()) {
                    var mongo = storageConfig.getMongodb();
                    playerData.sendMessage(Message.raw("  MongoDB: " + mongo.getConnectionString() + " / " + mongo.getDatabase()).color(GRAY));
                }
            }
            case "migrate" -> {
                playerData.sendMessage(Message.raw("Starting storage migration...").color(YELLOW));
                plugin.migrateStorage();
                playerData.sendMessage(Message.raw("Migration complete! All data saved to configured save provider.").color(GREEN));
            }
            default -> {
                playerData.sendMessage(Message.raw("Unknown storage command: " + action).color(RED));
                playerData.sendMessage(Message.raw("Use: /townadmin storage [status|migrate]").color(GRAY));
            }
        }
    }

    private void handlePlayer(PlayerRef playerData, String playerName, String action) {
        if (playerName == null || playerName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin player <name> [clear]").color(RED));
            playerData.sendMessage(Message.raw("  Shows player's town index status").color(GRAY));
            playerData.sendMessage(Message.raw("  'clear' removes player from index (fixes 'already in town' bug)").color(GRAY));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();

        // Find the player by name
        UUID targetId = null;
        String targetName = playerName;
        for (World w : HyTown.WORLDS.values()) {
            for (Player p : w.getPlayers()) {
                if (p.getDisplayName().equalsIgnoreCase(playerName)) {
                    targetId = p.getUuid();
                    targetName = p.getDisplayName();
                    break;
                }
            }
            if (targetId != null) break;
        }

        if (targetId == null) {
            playerData.sendMessage(Message.raw("Player '" + playerName + "' not found online!").color(RED));
            playerData.sendMessage(Message.raw("Player must be online to check/fix their status.").color(GRAY));
            return;
        }

        Town indexedTown = townStorage.getPlayerTown(targetId);
        boolean actuallyInTown = false;
        Town actualTown = null;

        // Check if player is ACTUALLY in any town's residents list
        for (Town town : townStorage.getAllTowns()) {
            if (town.isMember(targetId)) {
                actuallyInTown = true;
                actualTown = town;
                break;
            }
        }

        playerData.sendMessage(Message.raw("========== Player Status: " + targetName + " ==========").color(GOLD));
        playerData.sendMessage(Message.raw("UUID: " + targetId).color(GRAY));
        playerData.sendMessage(Message.raw("Indexed to town: " + (indexedTown != null ? indexedTown.getName() : "NONE")).color(WHITE));
        playerData.sendMessage(Message.raw("Actually in town: " + (actualTown != null ? actualTown.getName() : "NONE")).color(WHITE));

        // Check for inconsistency
        boolean inconsistent = (indexedTown != null && !indexedTown.isMember(targetId)) ||
                               (indexedTown == null && actuallyInTown) ||
                               (indexedTown != null && actualTown != null && !indexedTown.getName().equalsIgnoreCase(actualTown.getName()));

        if (inconsistent) {
            playerData.sendMessage(Message.raw("STATUS: INCONSISTENT! Index doesn't match reality.").color(RED));
        } else {
            playerData.sendMessage(Message.raw("STATUS: OK (index matches reality)").color(GREEN));
        }

        // Handle clear action
        if ("clear".equalsIgnoreCase(action)) {
            if (indexedTown != null) {
                townStorage.unindexPlayer(targetId);
                playerData.sendMessage(Message.raw("Cleared " + targetName + " from town index!").color(GREEN));
                playerData.sendMessage(Message.raw("They can now join a new town.").color(GRAY));
            } else {
                playerData.sendMessage(Message.raw("Player is not indexed to any town.").color(YELLOW));
            }
        } else if (inconsistent) {
            playerData.sendMessage(Message.raw("Use '/townadmin player " + targetName + " clear' to fix").color(YELLOW));
        }
    }

    private void handleSpawn(Store<EntityStore> store, Ref<EntityStore> playerRef,
                              PlayerRef playerData, World world, String townName) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin spawn <townname>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTown(townName);

        if (town == null) {
            playerData.sendMessage(Message.raw("Town not found: " + townName).color(RED));
            return;
        }

        // DEBUG: Show what claims the town has
        playerData.sendMessage(Message.raw("DEBUG - Town claims: " + town.getClaimKeys()).color(GRAY));
        playerData.sendMessage(Message.raw("DEBUG - hasSpawn: " + town.hasSpawn() +
            ", spawnX: " + town.getSpawnX() + ", spawnZ: " + town.getSpawnZ()).color(GRAY));
        playerData.sendMessage(Message.raw("DEBUG - Current world: " + world.getName()).color(GRAY));

        double spawnX, spawnY, spawnZ;
        float spawnYaw, spawnPitch;

        if (town.hasSpawn()) {
            spawnX = town.getSpawnX();
            spawnY = town.getSpawnY();
            spawnZ = town.getSpawnZ();
            spawnYaw = town.getSpawnYaw();
            spawnPitch = town.getSpawnPitch();
        } else {
            // Fallback to first claimed chunk
            String firstClaim = town.getFirstClaimKey();
            if (firstClaim == null) {
                playerData.sendMessage(Message.raw("Town has no claims or spawn set!").color(RED));
                return;
            }

            int[] coords = Town.parseClaimCoords(firstClaim);
            if (coords == null) {
                playerData.sendMessage(Message.raw("Error reading first claim location!").color(RED));
                return;
            }

            // Center of first chunk
            spawnX = coords[0] * 16 + 8;
            spawnZ = coords[1] * 16 + 8;
            spawnY = 64;
            spawnYaw = 0;
            spawnPitch = 0;

            playerData.sendMessage(Message.raw("No spawn set - teleporting to first claim").color(YELLOW));
        }

        // Instant teleport - no countdown for admins
        final double finalX = spawnX;
        final double finalY = spawnY;
        final double finalZ = spawnZ;
        final float finalYaw = spawnYaw;
        final float finalPitch = spawnPitch;

        world.execute(() -> {
            Vector3d pos = new Vector3d(finalX, finalY, finalZ);
            Vector3f rot = new Vector3f(finalYaw, finalPitch, 0);
            Teleport teleport = new Teleport(world, pos, rot);
            store.addComponent(playerRef, Teleport.getComponentType(), teleport);
        });

        playerData.sendMessage(Message.raw("Teleported to " + town.getName() + "!").color(GREEN));
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Towny Admin Commands ==========").color(GOLD));

        // General
        playerData.sendMessage(Message.raw("--- General ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin gui").color(WHITE));
        playerData.sendMessage(Message.raw("  Open admin control panel GUI").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin reload").color(WHITE));
        playerData.sendMessage(Message.raw("  Reload configuration from file").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin save").color(WHITE));
        playerData.sendMessage(Message.raw("  Force save all towns and claims to disk").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin debug").color(WHITE));
        playerData.sendMessage(Message.raw("  Show debug info (town count, claims, residents)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin spawn <townname>").color(WHITE));
        playerData.sendMessage(Message.raw("  Teleport to any town (instant, no cooldown)").color(GRAY));

        // Town Management
        playerData.sendMessage(Message.raw("--- Town Management ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin town <name>").color(WHITE));
        playerData.sendMessage(Message.raw("  View town details (mayor, residents, claims, balance)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> delete").color(WHITE));
        playerData.sendMessage(Message.raw("  Delete a town permanently").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> kick <player>").color(WHITE));
        playerData.sendMessage(Message.raw("  Remove a player from the town").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> setmayor <player>").color(WHITE));
        playerData.sendMessage(Message.raw("  Transfer mayor to another resident").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> rename <newname>").color(WHITE));
        playerData.sendMessage(Message.raw("  Rename a town").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> setbalance <amount>").color(WHITE));
        playerData.sendMessage(Message.raw("  Set the town's bank balance").color(GRAY));

        // Backup & Restore
        playerData.sendMessage(Message.raw("--- Backup & Restore ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin backup").color(WHITE));
        playerData.sendMessage(Message.raw("  Create a backup of all towns now").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin backups [townname]").color(WHITE));
        playerData.sendMessage(Message.raw("  List available daily backups").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin restore <townname>").color(WHITE));
        playerData.sendMessage(Message.raw("  Restore town from .bak file (last save)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin restore <townname> <date>").color(WHITE));
        playerData.sendMessage(Message.raw("  Restore from daily backup (e.g., 2026-01-16)").color(GRAY));

        // Wild Protection
        playerData.sendMessage(Message.raw("--- Wild Protection ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin wild toggle").color(WHITE));
        playerData.sendMessage(Message.raw("  Toggle wild protection on/off").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin wild sety <y-level>").color(WHITE));
        playerData.sendMessage(Message.raw("  Set minimum Y level for protection").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin wild info").color(WHITE));
        playerData.sendMessage(Message.raw("  Show wild protection settings").color(GRAY));

        // Configuration
        playerData.sendMessage(Message.raw("--- Configuration ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin set towncost [amount]").color(WHITE));
        playerData.sendMessage(Message.raw("  View/set town creation cost").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin set claimcost [amount]").color(WHITE));
        playerData.sendMessage(Message.raw("  View/set town claim cost").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin set wildminy [y-level]").color(WHITE));
        playerData.sendMessage(Message.raw("  View/set wild protection min Y").color(GRAY));

        // Player Diagnostics
        playerData.sendMessage(Message.raw("--- Player Diagnostics ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin player <name>").color(WHITE));
        playerData.sendMessage(Message.raw("  Check player's town index status").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin player <name> clear").color(WHITE));
        playerData.sendMessage(Message.raw("  Fix 'already in town' bug by clearing player's index").color(GRAY));

        // Storage
        playerData.sendMessage(Message.raw("--- Storage Backend ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin storage").color(WHITE));
        playerData.sendMessage(Message.raw("  Show current storage backend status").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin storage status").color(WHITE));
        playerData.sendMessage(Message.raw("  Detailed storage status and config").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin storage migrate").color(WHITE));
        playerData.sendMessage(Message.raw("  Force save all data to configured save backend").color(GRAY));

    }
}
