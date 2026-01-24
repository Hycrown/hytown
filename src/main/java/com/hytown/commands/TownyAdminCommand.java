package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownRoad;
import com.hytown.data.TownStorage;
import com.hytown.events.*;
import com.hytown.gui.TownAdminGui;
import com.hytown.util.ChunkUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
import java.nio.charset.StandardCharsets;
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
            case "town" -> handleTown(store, playerRef, playerData, world, arg1, arg2, arg3);
            case "wild" -> handleWild(playerData, arg1, arg2);
            case "debug" -> handleDebug(playerData);
            case "save" -> handleSave(playerData);
            case "set" -> handleSet(store, playerRef, playerData, world, arg1, arg2);
            case "restore" -> handleRestore(playerData, arg1, arg2);
            case "backups" -> handleBackups(playerData, arg1);
            case "backup" -> handleBackupNow(playerData);
            case "player" -> handlePlayer(playerData, arg1, arg2);
            case "spawn" -> handleSpawn(store, playerRef, playerData, world, arg1);
            case "storage" -> handleStorage(playerData, arg1);
            case "join" -> handleAdminJoin(playerData, playerData.getUuid(), playerData.getUsername(), arg1);
            case "setmayor" -> handleAdminSetMayor(playerData, playerData.getUuid(), playerData.getUsername(), arg1, arg2);
            case "leave" -> handleAdminLeave(playerData, playerData.getUuid());
            case "claimroad" -> handleClaimRoad(store, playerRef, playerData, playerData.getUuid(), world, arg1, arg2);
            case "claimrect" -> handleClaimRect(store, playerRef, playerData, playerData.getUuid(), world, arg1);
            case "claim" -> handleAdminClaim(store, playerRef, playerData, world, arg1);
            case "unclaim" -> handleAdminUnclaim(store, playerRef, playerData, world);
            case "roadclaim" -> handleAdminRoadClaim(store, playerRef, playerData, world, arg1);
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

    private void handleTown(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerData, World world, String townName, String subAction, String arg) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin town <name> [action]").color(RED));
            playerData.sendMessage(Message.raw("       /townadmin town new <name> - Create NPC-owned town").color(GRAY));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();

        // Handle "new" command: /townadmin town new <townname>
        if (townName.equalsIgnoreCase("new")) {
            if (subAction == null || subAction.isEmpty()) {
                playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin town new <townname>").color(YELLOW));
                playerData.sendMessage(Message.raw("  Creates an NPC-owned town (no real player as mayor)").color(GRAY));
                return;
            }
            String newTownName = subAction;

            // Validate name
            if (newTownName.length() < 3 || newTownName.length() > 24) {
                playerData.sendMessage(Message.raw("[ADMIN] Town name must be 3-24 characters!").color(RED));
                return;
            }
            if (!newTownName.matches("^[a-zA-Z0-9_-]+$")) {
                playerData.sendMessage(Message.raw("[ADMIN] Town name can only contain letters, numbers, _ and -").color(RED));
                return;
            }

            // Check if town already exists
            if (townStorage.townExists(newTownName)) {
                playerData.sendMessage(Message.raw("[ADMIN] Town '" + newTownName + "' already exists!").color(RED));
                return;
            }

            // Get player position for spawn chunk
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            Vector3d pos = transform.getPosition();
            String worldName = world.getName();
            int chunkX = ChunkUtil.toChunkX(pos.getX());
            int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;

            // Check if chunk is already claimed
            Town existingClaimTown = townStorage.getTownByClaimKey(claimKey);
            if (existingClaimTown != null) {
                playerData.sendMessage(Message.raw("[ADMIN] This chunk is already claimed by: " + existingClaimTown.getName()).color(RED));
                return;
            }

            // Create NPC-owned town
            UUID npcMayorId = UUID.nameUUIDFromBytes(("NPC:" + newTownName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Town newTown = new Town(newTownName, npcMayorId, newTownName);
            
            // Claim the spawn chunk
            newTown.addClaim(claimKey);
            townStorage.saveTown(newTown);
            townStorage.indexClaim(claimKey, newTown.getName());
            
            // Refresh map
            plugin.refreshWorldMap(worldName);

            playerData.sendMessage(Message.raw("[ADMIN] Created NPC-owned town: " + newTownName).color(GREEN));
            playerData.sendMessage(Message.raw("  NPC Mayor: " + newTownName).color(GRAY));
            playerData.sendMessage(Message.raw("  Spawn chunk: [" + chunkX + ", " + chunkZ + "]").color(GRAY));
            playerData.sendMessage(Message.raw("  Use /townadmin town " + newTownName + " to manage").color(GRAY));
            return;
        }

        Town town = townStorage.getTown(townName);

        if (town == null) {
            playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
            playerData.sendMessage(Message.raw("Use /townadmin town new " + townName + " to create it").color(GRAY));
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
            case "setnpcmayor" -> {
                if (arg == null || arg.isEmpty()) {
                    playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin town " + townName + " setnpcmayor <npcname>").color(YELLOW));
                    playerData.sendMessage(Message.raw("  Creates a fake NPC player as mayor").color(GRAY));
                    playerData.sendMessage(Message.raw("  Frees you to create/manage other towns").color(GRAY));
                    return;
                }
                String npcName = arg;
                // Generate deterministic UUID from NPC name (prefixed to avoid collision with real players)
                UUID npcId = UUID.nameUUIDFromBytes(("NPC:" + npcName).getBytes(StandardCharsets.UTF_8));

                // Store old mayor info for event
                UUID oldMayorId = town.getMayorId();
                String oldMayorName = town.getMayorName();

                // Ensure NPC is added as resident first
                if (!town.isMember(npcId)) {
                    town.addResident(npcId, npcName);
                }

                // Set the NPC as mayor (old mayor becomes assistant automatically via setMayor)
                town.setMayor(npcId, npcName);
                townStorage.saveTown(town);

                // Unindex the old mayor from this town so they can join/create other towns
                townStorage.unindexPlayer(oldMayorId);

                // Fire TownMayorChangeEvent
                TownMayorChangeEvent mayorEvent = new TownMayorChangeEvent(town, oldMayorId, oldMayorName, npcId, npcName, true);
                plugin.getEventBus().fire(mayorEvent);

                playerData.sendMessage(Message.raw("[ADMIN] Set NPC mayor for " + town.getName()).color(GOLD));
                playerData.sendMessage(Message.raw("  NPC Name: " + npcName).color(GREEN));
                playerData.sendMessage(Message.raw("  NPC UUID: " + npcId).color(GRAY));
                playerData.sendMessage(Message.raw("  Old Mayor: " + oldMayorName + " -> demoted to assistant").color(GRAY));
                playerData.sendMessage(Message.raw("  Town upkeep: EXEMPT (NPC-owned)").color(YELLOW));
                playerData.sendMessage(Message.raw("You can now create or join other towns!").color(GREEN));
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

    private void handleSet(Store<EntityStore> store, Ref<EntityStore> playerRef,
                       PlayerRef playerData, World world, String setting, String value) {
        if (setting == null || setting.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townadmin set <towncost|claimcost|wildminy|pvp> <value>").color(RED));
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
            case "pvp" -> {
                // Get player's current position to find the town
                TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
                if (transform == null) {
                    playerData.sendMessage(Message.raw("Could not get position!").color(RED));
                    return;
                }
                double posX = transform.getPosition().getX();
                double posZ = transform.getPosition().getZ();
                int chunkX = ChunkUtil.toChunkX(posX);
                int chunkZ = ChunkUtil.toChunkZ(posZ);
                String worldName = world.getName();
                String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                TownStorage townStorage = plugin.getTownStorage();
                Town town = townStorage.getTownByClaimKey(claimKey);
                if (town == null) {
                    playerData.sendMessage(Message.raw("You are not standing in a town claim!").color(RED));
                    return;
                }

                if (value == null || value.isEmpty()) {
                    // Show current PVP state
                    boolean pvpEnabled = town.getSettings().isPvpEnabled();
                    playerData.sendMessage(Message.raw("PvP in " + town.getName() + ": " + (pvpEnabled ? "ON" : "OFF")).color(WHITE));
                    playerData.sendMessage(Message.raw("Usage: /townadmin set pvp <on|off>").color(GRAY));
                    return;
                }

                boolean enable = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || value.equals("1");
                boolean disable = value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false") || value.equals("0");

                if (!enable && !disable) {
                    playerData.sendMessage(Message.raw("Invalid value! Use: on/off").color(RED));
                    return;
                }

                town.getSettings().setPvpEnabled(enable);
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw("[ADMIN] PvP in " + town.getName() + " set to " + (enable ? "ON" : "OFF")).color(GOLD));
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

        // Admin Town Membership
        playerData.sendMessage(Message.raw("--- Admin Town Membership ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin join <town>").color(WHITE));
        playerData.sendMessage(Message.raw("  Join any town as resident (bypasses invite)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin setmayor [town] [player]").color(WHITE));
        playerData.sendMessage(Message.raw("  Set yourself or another player as mayor").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin leave").color(WHITE));
        playerData.sendMessage(Message.raw("  Leave your current town").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin town <name> setnpcmayor <npcname>").color(WHITE));
        playerData.sendMessage(Message.raw("  Set an NPC as mayor (frees you to manage other towns)").color(GRAY));

        // Admin Claiming
        playerData.sendMessage(Message.raw("--- Admin Claiming ---").color(GOLD));
        playerData.sendMessage(Message.raw("/townadmin claim [townname]").color(WHITE));
        playerData.sendMessage(Message.raw("  Claim current chunk for town (free, no limits)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin unclaim").color(WHITE));
        playerData.sendMessage(Message.raw("  Force unclaim current chunk from any town").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin roadclaim <townname>").color(WHITE));
        playerData.sendMessage(Message.raw("  Claim current chunk as ROAD for town (gets speed boost)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin claimroad <dir> <name>").color(WHITE));
        playerData.sendMessage(Message.raw("  Claim road 50k blocks in direction (n/s/e/w/all)").color(GRAY));
        playerData.sendMessage(Message.raw("/townadmin claimrect <size>").color(WHITE));
        playerData.sendMessage(Message.raw("  Claim NxN rectangle from current position").color(GRAY));
    }

    // ==================== ADMIN TOWN MEMBERSHIP COMMANDS ====================

    /**
     * Admin joins any town as resident (bypasses invite system).
     */
    private void handleAdminJoin(PlayerRef playerData, UUID playerId, String playerName, String townName) {
        TownStorage townStorage = plugin.getTownStorage();

        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin join <townname>").color(YELLOW));
            playerData.sendMessage(Message.raw("  Joins any town as resident (bypasses invite)").color(GRAY));
            return;
        }

        // Check if already in a town
        Town currentTown = townStorage.getPlayerTown(playerId);
        if (currentTown != null) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Already in town '" + currentTown.getName() + "'").color(RED));
            playerData.sendMessage(Message.raw("  Use /townadmin leave first").color(GRAY));
            return;
        }

        // Get target town
        Town town = townStorage.getTown(townName);
        if (town == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Town '" + townName + "' not found").color(RED));
            return;
        }

        // Add admin as resident (bypassing invite system)
        town.addResident(playerId, playerName);
        townStorage.saveTown(town);
        townStorage.indexPlayer(playerId, town.getName());

        // Fire TownJoinEvent
        TownJoinEvent joinEvent = new TownJoinEvent(town, playerId, playerName, false);
        plugin.getEventBus().fire(joinEvent);

        playerData.sendMessage(Message.raw("[ADMIN] Joined town as resident").color(GOLD));
        playerData.sendMessage(Message.raw("  Town: " + town.getName()).color(GREEN));
        playerData.sendMessage(Message.raw("  Rank: Resident (invite bypassed)").color(GRAY));
        playerData.sendMessage(Message.raw("  Use /townadmin setmayor to become mayor").color(GRAY));
    }

    /**
     * Admin sets self or another player as mayor of a town.
     * Usage: /townadmin setmayor [townname] [playername]
     * - No args: sets self as mayor of current town
     * - townname only: sets self as mayor of specified town
     * - townname + playername: sets specified player as mayor
     */
    private void handleAdminSetMayor(PlayerRef playerData, UUID playerId, String playerName, String arg1, String arg2) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town;
        UUID newMayorId;
        String newMayorName;

        if (arg1 == null || arg1.isEmpty()) {
            // No args - use current town, set self as mayor
            town = townStorage.getPlayerTown(playerId);
            if (town == null) {
                playerData.sendMessage(Message.raw("[ADMIN] Error: You are not in a town").color(RED));
                playerData.sendMessage(Message.raw("  Usage: /townadmin setmayor <townname> [player]").color(GRAY));
                return;
            }
            newMayorId = playerId;
            newMayorName = playerName;
        } else if (arg2 == null || arg2.isEmpty()) {
            // One arg - could be town name (set self as mayor) or player name (current town)
            town = townStorage.getTown(arg1);
            if (town != null) {
                // arg1 is a town name, set self as mayor
                newMayorId = playerId;
                newMayorName = playerName;
            } else {
                // arg1 might be a player name for current town
                town = townStorage.getPlayerTown(playerId);
                if (town == null) {
                    playerData.sendMessage(Message.raw("[ADMIN] Error: Town '" + arg1 + "' not found").color(RED));
                    return;
                }
                // Find the player in the town
                newMayorId = null;
                newMayorName = arg1;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(arg1)) {
                        newMayorId = entry.getKey();
                        newMayorName = entry.getValue();
                        break;
                    }
                }
                if (newMayorId == null) {
                    playerData.sendMessage(Message.raw("[ADMIN] Error: " + arg1 + " is not in your town").color(RED));
                    return;
                }
            }
        } else {
            // Two args - town name and player name
            town = townStorage.getTown(arg1);
            if (town == null) {
                playerData.sendMessage(Message.raw("[ADMIN] Error: Town '" + arg1 + "' not found").color(RED));
                return;
            }
            // Find the player
            newMayorId = null;
            newMayorName = arg2;
            for (var entry : town.getResidentNames().entrySet()) {
                if (entry.getValue().equalsIgnoreCase(arg2)) {
                    newMayorId = entry.getKey();
                    newMayorName = entry.getValue();
                    break;
                }
            }
            if (newMayorId == null) {
                // Player not in town yet - if it's the admin, add them first
                if (arg2.equalsIgnoreCase(playerName)) {
                    town.addResident(playerId, playerName);
                    newMayorId = playerId;
                    newMayorName = playerName;
                    townStorage.indexPlayer(playerId, town.getName());
                } else {
                    playerData.sendMessage(Message.raw("[ADMIN] Error: " + arg2 + " is not in " + town.getName()).color(RED));
                    playerData.sendMessage(Message.raw("  They must be a resident first").color(GRAY));
                    return;
                }
            }
        }

        // Store old mayor info for event
        UUID oldMayorId = town.getMayorId();
        String oldMayorName = town.getMayorName();

        // If new mayor is not in the town yet (admin joining + becoming mayor), add them
        if (!town.isMember(newMayorId)) {
            town.addResident(newMayorId, newMayorName);
            townStorage.indexPlayer(newMayorId, town.getName());
        }

        // Set new mayor (old mayor automatically becomes assistant)
        town.setMayor(newMayorId, newMayorName);
        townStorage.saveTown(town);

        // Fire TownMayorChangeEvent
        TownMayorChangeEvent mayorEvent = new TownMayorChangeEvent(town, oldMayorId, oldMayorName, newMayorId, newMayorName, true);
        plugin.getEventBus().fire(mayorEvent);

        playerData.sendMessage(Message.raw("[ADMIN] Mayor changed for " + town.getName()).color(GOLD));
        playerData.sendMessage(Message.raw("  New Mayor: " + newMayorName).color(GREEN));
        if (!oldMayorId.equals(newMayorId)) {
            playerData.sendMessage(Message.raw("  Old Mayor: " + oldMayorName + " -> demoted to assistant").color(GRAY));
        }
    }

    /**
     * Admin leaves their current town.
     * If admin is mayor, town is NOT deleted - they're just removed.
     */
    private void handleAdminLeave(PlayerRef playerData, UUID playerId) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: You are not in a town").color(RED));
            return;
        }

        String townName = town.getName();
        boolean wasMayor = town.isMayor(playerId);
        String previousRank = wasMayor ? "Mayor" : (town.isAssistant(playerId) ? "Assistant" : "Resident");

        // Fire TownLeaveEvent
        TownLeaveEvent leaveEvent = TownLeaveEvent.voluntary(town, playerId, playerData.getUsername());
        plugin.getEventBus().fire(leaveEvent);

        // Remove from town
        town.removeResident(playerId);
        townStorage.saveTown(town);
        townStorage.unindexPlayer(playerId);

        playerData.sendMessage(Message.raw("[ADMIN] Left town").color(GOLD));
        playerData.sendMessage(Message.raw("  Town: " + townName).color(GREEN));
        playerData.sendMessage(Message.raw("  Previous Rank: " + previousRank).color(GRAY));
        if (wasMayor) {
            playerData.sendMessage(Message.raw("  Warning: Town needs new mayor!").color(YELLOW));
            playerData.sendMessage(Message.raw("  Use /townadmin town " + townName + " setmayor <player>").color(GRAY));
            if (town.getResidentCount() == 0) {
                playerData.sendMessage(Message.raw("  Warning: Town has no residents left!").color(RED));
            }
        }
    }

    // ==================== ADMIN CLAIMING COMMANDS ====================

    /**
     * Claims chunks in cardinal directions for up to 50,000 blocks (~1,562 chunks per direction).
     * Creates a new NPC-owned town with the road name and claims for that town.
     * Admin-only: Free and ignores all limits.
     *
     * Usage: /townadmin claimroad <direction> <name>
     * Example: /townadmin claimroad north Kings -> Creates NPC town "Kings" with road "North Kings"
     */
    private void handleClaimRoad(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                  PlayerRef playerData, UUID playerId, World world,
                                  String direction, String roadName) {
        TownStorage townStorage = plugin.getTownStorage();

        // Require both direction and name
        if (direction == null || direction.isEmpty()) {
            playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin claimroad <direction> <name>").color(YELLOW));
            playerData.sendMessage(Message.raw("  Directions: north, south, east, west, all").color(GRAY));
            playerData.sendMessage(Message.raw("  Example: /townadmin claimroad north Kings").color(GRAY));
            playerData.sendMessage(Message.raw("  Creates NPC-owned town with road claims").color(GRAY));
            playerData.sendMessage(Message.raw("  Town name = road name, PvP enabled by default").color(GRAY));
            return;
        }

        if (roadName == null || roadName.isEmpty()) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Road/town name required").color(RED));
            playerData.sendMessage(Message.raw("  Example: /townadmin claimroad " + direction + " Kings").color(GRAY));
            return;
        }

        // Get or create the NPC-owned town with this road name
        Town town = townStorage.getTown(roadName);
        boolean createdNewTown = false;
        if (town == null) {
            // Create new NPC-owned town for this road
            UUID npcMayorId = UUID.nameUUIDFromBytes(("NPC:" + roadName).getBytes(StandardCharsets.UTF_8));
            town = new Town(roadName, npcMayorId, roadName); // Town name = road name, NPC mayor name = road name
            town.getSettings().setPvpEnabled(true); // PvP ON by default for road towns
            townStorage.saveTown(town);
            createdNewTown = true;
            playerData.sendMessage(Message.raw("[ADMIN] Created new NPC-owned town: " + roadName).color(GREEN));
            playerData.sendMessage(Message.raw("  NPC Mayor: " + roadName).color(GRAY));
            playerData.sendMessage(Message.raw("  PvP: ENABLED (default for roads)").color(YELLOW));
        } else {
            playerData.sendMessage(Message.raw("[ADMIN] Using existing town: " + roadName).color(YELLOW));
        }

        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();
        int startChunkX = ChunkUtil.toChunkX(pos.getX());
        int startChunkZ = ChunkUtil.toChunkZ(pos.getZ());

        // Parse direction(s)
        java.util.List<String> directions = new java.util.ArrayList<>();
        if (direction.equalsIgnoreCase("all")) {
            directions.addAll(java.util.List.of("north", "south", "east", "west"));
        } else {
            directions.add(direction.toLowerCase());
        }

        // Validate directions
        for (String dir : directions) {
            if (!dir.equals("north") && !dir.equals("south") && !dir.equals("east") && !dir.equals("west")) {
                playerData.sendMessage(Message.raw("[ADMIN] Invalid direction: " + dir).color(RED));
                playerData.sendMessage(Message.raw("  Valid: north, south, east, west, all").color(GRAY));
                return;
            }
        }

        // 50,000 blocks / 32 blocks per chunk = 1,562 chunks max per direction
        int maxChunks = 1562;
        int totalClaimed = 0;
        int totalSkipped = 0;

        playerData.sendMessage(Message.raw("[ADMIN] Creating road '" + roadName + "' for " + town.getName()).color(GOLD));
        playerData.sendMessage(Message.raw("  Origin: chunk [" + startChunkX + ", " + startChunkZ + "]").color(GRAY));
        playerData.sendMessage(Message.raw("  Max distance: 50,000 blocks per direction").color(GRAY));

        for (String dir : directions) {
            // Create road object for this direction
            String dirDisplay = direction.equalsIgnoreCase("all") ? dir : direction;
            TownRoad road = new TownRoad(roadName, dirDisplay, worldName, startChunkX, startChunkZ);
            String roadId = Town.generateRoadId(dirDisplay, roadName);

            // Check if road with this ID already exists
            if (town.getRoad(roadId) != null) {
                playerData.sendMessage(Message.raw("  Road '" + road.getDisplayName() + "' already exists!").color(RED));
                continue;
            }

            int dx = 0, dz = 0;
            switch (dir) {
                case "north" -> dz = -1;
                case "south" -> dz = 1;
                case "east" -> dx = 1;
                case "west" -> dx = -1;
            }

            int claimed = 0, skipped = 0;
            for (int i = 1; i <= maxChunks; i++) {
                int chunkX = startChunkX + (dx * i);
                int chunkZ = startChunkZ + (dz * i);
                String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                // Check if already claimed by any town
                Town existingTown = townStorage.getTownByClaimKey(claimKey);
                if (existingTown != null) {
                    skipped++;
                    continue; // Skip, don't stop - continue claiming beyond
                }

                // Check if claimed by personal claims
                UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, chunkX * 32, chunkZ * 32);
                if (personalOwner != null) {
                    skipped++;
                    continue;
                }

                // Claim it for the town and add to road
                town.addClaim(claimKey);
                townStorage.indexClaim(claimKey, town.getName());
                road.addClaimKey(claimKey);
                claimed++;

                // Progress report every 500 chunks
                if (claimed % 500 == 0) {
                    playerData.sendMessage(Message.raw("  " + dir.toUpperCase() + ": " + claimed + " chunks claimed so far...").color(GRAY));
                }
            }

            // Add road to town if it has any claims
            if (road.getChunkCount() > 0) {
                town.addRoad(roadId, road);
                playerData.sendMessage(Message.raw("  " + road.getDisplayName() + ": " + claimed + " claimed, " + skipped + " skipped").color(GREEN));
                playerData.sendMessage(Message.raw("    NPC Controller: " + road.getNpcControllerName()).color(GRAY));
            } else {
                playerData.sendMessage(Message.raw("  " + dir.toUpperCase() + ": No chunks available to claim").color(YELLOW));
            }

            totalClaimed += claimed;
            totalSkipped += skipped;
        }

        townStorage.saveTown(town);
        plugin.refreshWorldMap(worldName);

        playerData.sendMessage(Message.raw("[ADMIN] ========== Claim Road Complete ==========").color(GOLD));
        playerData.sendMessage(Message.raw("  Total Claimed: " + totalClaimed + " chunks").color(GREEN));
        playerData.sendMessage(Message.raw("  Total Skipped: " + totalSkipped + " chunks (existing claims)").color(YELLOW));
        playerData.sendMessage(Message.raw("  Town Claims: " + town.getClaimCount() + " total").color(WHITE));
        playerData.sendMessage(Message.raw("  Roads: " + town.getRoads().size() + " | Road Chunks: " + town.getTotalRoadChunks()).color(WHITE));
        playerData.sendMessage(Message.raw("  Cost: FREE (admin bypass)").color(GRAY));
    }

    /**
     * Claims a rectangle of chunks centered on player position.
     * Size N = claims N chunks in each direction = (2N+1)^2 area.
     * Must be used from within existing town claims (uses BFS expansion).
     * Admin-only: Free and ignores all limits.
     */
    private void handleClaimRect(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                  PlayerRef playerData, UUID playerId, World world, String sizeStr) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: You must be in a town to use this command").color(RED));
            playerData.sendMessage(Message.raw("  Use /townadmin join <town> first").color(GRAY));
            return;
        }

        // Parse size
        int size;
        if (sizeStr == null || sizeStr.isEmpty()) {
            playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin claimrect <size>").color(YELLOW));
            playerData.sendMessage(Message.raw("  Size 5 = 5 chunks in each direction = 11x11 area").color(GRAY));
            playerData.sendMessage(Message.raw("  Must stand in existing town claim").color(GRAY));
            playerData.sendMessage(Message.raw("  Can claim over road claims from other towns").color(GRAY));
            return;
        }

        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Invalid size - use a number").color(RED));
            return;
        }

        if (size < 1 || size > 50) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Size must be 1-50").color(RED));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();
        int centerX = ChunkUtil.toChunkX(pos.getX());
        int centerZ = ChunkUtil.toChunkZ(pos.getZ());

        // Calculate bounds
        int minX = centerX - size;
        int maxX = centerX + size;
        int minZ = centerZ - size;
        int maxZ = centerZ + size;
        int totalArea = (2 * size + 1) * (2 * size + 1);

        // Check that current chunk is owned by the town
        String currentClaimKey = worldName + ":" + centerX + "," + centerZ;
        if (!town.ownsClaim(currentClaimKey)) {
            playerData.sendMessage(Message.raw("[ADMIN] Error: Must stand in town territory").color(RED));
            playerData.sendMessage(Message.raw("  This command expands from existing claims").color(GRAY));
            return;
        }

        playerData.sendMessage(Message.raw("[ADMIN] Claiming rectangle for " + town.getName()).color(GOLD));
        playerData.sendMessage(Message.raw("  Size: " + (2*size+1) + "x" + (2*size+1) + " (" + totalArea + " chunks max)").color(GRAY));
        playerData.sendMessage(Message.raw("  Center: chunk [" + centerX + ", " + centerZ + "]").color(GRAY));

        // Use BFS to expand from existing claims ensuring connectivity
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<int[]> queue = new java.util.LinkedList<>();
        int claimed = 0, skipped = 0;

        // Seed with all existing town claims in the rectangle
        for (String claimKey : town.getClaimKeys()) {
            if (!claimKey.startsWith(worldName + ":")) continue;
            int[] coords = Town.parseClaimCoords(claimKey);
            if (coords == null) continue;
            int cx = coords[0], cz = coords[1];
            if (cx >= minX && cx <= maxX && cz >= minZ && cz <= maxZ) {
                queue.add(new int[]{cx, cz});
                visited.add(cx + "," + cz);
            }
        }

        if (queue.isEmpty()) {
            // Should not happen since we checked current chunk is owned
            queue.add(new int[]{centerX, centerZ});
            visited.add(centerX + "," + centerZ);
        }

        // BFS expansion
        int[][] deltas = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // N, S, W, E
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cz = current[1];

            for (int[] d : deltas) {
                int nx = cx + d[0], nz = cz + d[1];
                String key = nx + "," + nz;

                // Check bounds
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                if (visited.contains(key)) continue;
                visited.add(key);

                String claimKey = worldName + ":" + nx + "," + nz;

                // Already owned by this town - just add to queue for expansion
                if (town.ownsClaim(claimKey)) {
                    queue.add(new int[]{nx, nz});
                    continue;
                }

                // Claimed by other town - check if it's a road claim (admins can take road claims)
                Town existingTown = townStorage.getTownByClaimKey(claimKey);
                if (existingTown != null) {
                    // Check if it's a road claim - admins can claim over roads
                    if (existingTown.isRoadClaim(claimKey)) {
                        // Remove claim from the other town's road and regular claims
                        existingTown.removeClaimFromRoad(claimKey);
                        existingTown.removeClaim(claimKey);
                        townStorage.unindexClaim(claimKey);
                        townStorage.saveTown(existingTown);
                        // Continue to claim it below
                    } else {
                        // Not a road claim - skip
                        skipped++;
                        continue;
                    }
                }

                // Personal claim - skip
                UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, nx * 32, nz * 32);
                if (personalOwner != null) {
                    skipped++;
                    continue;
                }

                // Claim it (admin bypasses all limits - direct add, no ClaimManager)
                town.addClaim(claimKey);
                townStorage.indexClaim(claimKey, town.getName());
                claimed++;
                queue.add(new int[]{nx, nz});
            }
        }

        townStorage.saveTown(town);
        plugin.refreshWorldMap(worldName);

        playerData.sendMessage(Message.raw("[ADMIN] ========== Claim Rect Complete ==========").color(GOLD));
        playerData.sendMessage(Message.raw("  New Claims: " + claimed + " chunks").color(GREEN));
        playerData.sendMessage(Message.raw("  Skipped: " + skipped + " chunks (other town claims)").color(YELLOW));
        playerData.sendMessage(Message.raw("  Town Claims: " + town.getClaimCount() + " total").color(WHITE));
        playerData.sendMessage(Message.raw("  Cost: FREE (admin bypass)").color(GRAY));
    }

    // ==================== ADMIN CLAIM/UNCLAIM COMMANDS ====================

    /**
     * Admin claims the current chunk for a specified town.
     * Usage: /townadmin claim [townname]
     * If no town specified, uses admin's current town.
     * Bypasses all costs and limits.
     */
    private void handleAdminClaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                   PlayerRef playerData, World world, String townName) {
        TownStorage townStorage = plugin.getTownStorage();

        // Get target town
        Town town;
        if (townName == null || townName.isEmpty()) {
            // Use admin's current town
            town = townStorage.getPlayerTown(playerData.getUuid());
            if (town == null) {
                playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin claim <townname>").color(YELLOW));
                playerData.sendMessage(Message.raw("  Or join a town first with /townadmin join <town>").color(GRAY));
                return;
            }
        } else {
            town = townStorage.getTown(townName);
            if (town == null) {
                playerData.sendMessage(Message.raw("[ADMIN] Town '" + townName + "' not found!").color(RED));
                return;
            }
        }

        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Could not get position!").color(RED));
            return;
        }
        double posX = transform.getPosition().getX();
        double posZ = transform.getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = world.getName();
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if already claimed by this town
        if (town.ownsClaim(claimKey)) {
            playerData.sendMessage(Message.raw("[ADMIN] This chunk is already claimed by " + town.getName()).color(YELLOW));
            return;
        }

        // Check if claimed by another town
        Town existingTown = townStorage.getTownByClaimKey(claimKey);
        if (existingTown != null) {
            playerData.sendMessage(Message.raw("[ADMIN] This chunk is claimed by: " + existingTown.getName()).color(RED));
            playerData.sendMessage(Message.raw("  Use /townadmin unclaim first to remove it").color(GRAY));
            return;
        }

        // Check if personal claim
        UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, posX, posZ);
        if (personalOwner != null) {
            String ownerName = plugin.getClaimStorage().getPlayerName(personalOwner);
            playerData.sendMessage(Message.raw("[ADMIN] This chunk is a personal claim by: " + ownerName).color(RED));
            playerData.sendMessage(Message.raw("  Personal claims must be removed by the owner").color(GRAY));
            return;
        }

        // Claim it for the town (admin bypasses costs and limits)
        town.addClaim(claimKey);
        townStorage.indexClaim(claimKey, town.getName());
        townStorage.saveTown(town);

        // Refresh map
        plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);

        playerData.sendMessage(Message.raw("[ADMIN] Claimed chunk [" + chunkX + ", " + chunkZ + "] for " + town.getName()).color(GREEN));
        playerData.sendMessage(Message.raw("  Town now has " + town.getClaimCount() + " claims").color(GRAY));
    }

    /**
     * Admin unclaims the current chunk from whatever town owns it.
     * Usage: /townadmin unclaim
     * Works on any town's claims (not personal claims).
     */
    private void handleAdminUnclaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                     PlayerRef playerData, World world) {
        TownStorage townStorage = plugin.getTownStorage();

        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Could not get position!").color(RED));
            return;
        }
        double posX = transform.getPosition().getX();
        double posZ = transform.getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = world.getName();
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if claimed by a town
        Town town = townStorage.getTownByClaimKey(claimKey);
        if (town == null) {
            // Check if personal claim
            UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, posX, posZ);
            if (personalOwner != null) {
                String ownerName = plugin.getClaimStorage().getPlayerName(personalOwner);
                playerData.sendMessage(Message.raw("[ADMIN] This is a personal claim by: " + ownerName).color(RED));
                playerData.sendMessage(Message.raw("  Use /townadmin player " + ownerName + " to manage").color(GRAY));
            } else {
                playerData.sendMessage(Message.raw("[ADMIN] This chunk is not claimed by any town").color(YELLOW));
            }
            return;
        }

        String townName = town.getName();

        // Check if it's a road claim
        boolean wasRoad = town.isRoadClaim(claimKey);
        if (wasRoad) {
            town.removeClaimFromRoad(claimKey);
        }

        // Remove the claim
        town.removeClaim(claimKey);
        townStorage.unindexClaim(claimKey);
        townStorage.saveTown(town);

        // Fire TownUnclaimEvent
        com.hytown.events.TownUnclaimEvent unclaimEvent = new com.hytown.events.TownUnclaimEvent(
                town, claimKey, worldName, chunkX, chunkZ, playerData.getUuid(), playerData.getUsername());
        plugin.getEventBus().fire(unclaimEvent);

        // Refresh map
        plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);

        playerData.sendMessage(Message.raw("[ADMIN] Unclaimed chunk [" + chunkX + ", " + chunkZ + "] from " + townName).color(GREEN));
        if (wasRoad) {
            playerData.sendMessage(Message.raw("  (Was a road claim)").color(GRAY));
        }
        playerData.sendMessage(Message.raw("  Town now has " + town.getClaimCount() + " claims").color(GRAY));
    }

    /**
     * Admin claims the current chunk as a ROAD claim for a town.
     * Usage: /townadmin roadclaim [townname]
     * - If townname specified, claims for that town
     * - If not specified, uses admin's current town
     * Road claims get speed boost applied.
     */
    private void handleAdminRoadClaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                       PlayerRef playerData, World world, String townName) {
        TownStorage townStorage = plugin.getTownStorage();

        // Require town name
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("[ADMIN] Usage: /townadmin roadclaim <townname>").color(YELLOW));
            playerData.sendMessage(Message.raw("  Claims current chunk as ROAD for the specified town").color(GRAY));
            playerData.sendMessage(Message.raw("  Road claims get speed boost applied").color(GRAY));
            return;
        }

        Town town = townStorage.getTown(townName);
        if (town == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Town '" + townName + "' not found!").color(RED));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerData.sendMessage(Message.raw("[ADMIN] Could not get position!").color(RED));
            return;
        }
        double posX = transform.getPosition().getX();
        double posZ = transform.getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = world.getName();
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if already claimed by another town
        Town existingTown = townStorage.getTownByClaimKey(claimKey);
        if (existingTown != null && !existingTown.getName().equals(town.getName())) {
            playerData.sendMessage(Message.raw("[ADMIN] Chunk claimed by: " + existingTown.getName()).color(RED));
            playerData.sendMessage(Message.raw("  Use /townadmin unclaim first").color(GRAY));
            return;
        }

        // Check if personal claim
        UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, posX, posZ);
        if (personalOwner != null) {
            String ownerName = plugin.getClaimStorage().getPlayerName(personalOwner);
            playerData.sendMessage(Message.raw("[ADMIN] This is a personal claim by: " + ownerName).color(RED));
            return;
        }

        // Use town name as road name
        String roadName = town.getName() + "Road";

        // Get or create road
        String roadId = Town.generateRoadId("misc", roadName);
        TownRoad road = town.getRoad(roadId);
        boolean newRoad = false;

        if (road == null) {
            // Create new road
            road = new TownRoad(roadName, "misc", worldName, chunkX, chunkZ);
            newRoad = true;
        }

        // Add claim to town if not already owned
        boolean alreadyOwned = town.ownsClaim(claimKey);
        if (!alreadyOwned) {
            town.addClaim(claimKey);
            townStorage.indexClaim(claimKey, town.getName());
        }

        // Add claim to road
        if (!road.containsClaim(claimKey)) {
            road.addClaimKey(claimKey);
        }

        // Add road to town if new
        if (newRoad) {
            town.addRoad(roadId, road);
        }

        townStorage.saveTown(town);
        plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);

        if (alreadyOwned) {
            playerData.sendMessage(Message.raw("[ADMIN] Converted chunk [" + chunkX + ", " + chunkZ + "] to road claim").color(GREEN));
        } else {
            playerData.sendMessage(Message.raw("[ADMIN] Claimed chunk [" + chunkX + ", " + chunkZ + "] as road").color(GREEN));
        }
        playerData.sendMessage(Message.raw("  Road: " + road.getDisplayName() + " (" + road.getChunkCount() + " chunks)").color(GRAY));
        playerData.sendMessage(Message.raw("  Speed boost will apply on this chunk").color(GRAY));
    }
}
