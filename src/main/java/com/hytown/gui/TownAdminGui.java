package com.hytown.gui;

import com.hytown.HyTown;
import com.hytown.config.PluginConfig;
import com.hytown.data.Town;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;

/**
 * Admin GUI for managing HyTown plugin settings.
 * Allows administrators to control costs, upkeep, wild protection, and other settings.
 */
public class TownAdminGui extends InteractiveCustomUIPage<TownAdminGui.AdminData> {

    private final HyTown plugin;
    private final World world;
    private final java.util.UUID playerId;

    private String inputValue = "";
    private String targetTownName = "";
    private String statusMessage = "";
    private boolean statusIsError = false;
    private String currentTab = "economy"; // economy, upkeep, wild, ranks, claim

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);

    public TownAdminGui(@Nonnull PlayerRef playerRef, HyTown plugin, World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerId = playerRef.getUuid();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull AdminData data) {
        super.handleDataEvent(ref, store, data);

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            this.sendUpdate();
            return;
        }

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle text input changes
        boolean textOnly = false;
        if (data.inputValue != null) {
            this.inputValue = data.inputValue;
            textOnly = true;
        }
        if (data.targetTownName != null) {
            this.targetTownName = data.targetTownName;
            textOnly = true;
        }

        // If only text changed, don't rebuild
        if (textOnly && data.action == null && data.tab == null) {
            this.sendUpdate();
            return;
        }

        // Handle tab changes
        if (data.tab != null) {
            this.currentTab = data.tab;
            this.statusMessage = "";
            this.inputValue = "";
        }

        // Handle actions
        if (data.action != null) {
            handleAction(data.action, playerRef);
        }

        // Rebuild and send update
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        this.build(ref, commandBuilder, eventBuilder, store);
        this.sendUpdate(commandBuilder, eventBuilder, true);
    }

    private void handleAction(String action, PlayerRef playerRef) {
        PluginConfig config = plugin.getPluginConfig();

        switch (action) {
            // Economy Settings
            case "set_town_cost" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownCreationCost(value);
                    statusMessage = "Town creation cost set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claim_cost" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownClaimCost(value);
                    statusMessage = "Claim cost set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_max_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setMaxTownClaims(value);
                    statusMessage = "Max town claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Upkeep Settings
            case "set_upkeep_base" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownUpkeepBase(value);
                    statusMessage = "Upkeep base set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_upkeep_per_plot" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownUpkeepPerClaim(value);
                    statusMessage = "Upkeep per plot set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_upkeep_hour" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    if (value < 0 || value > 23) {
                        statusMessage = "Hour must be 0-23!";
                        statusIsError = true;
                    } else {
                        config.setTownUpkeepHour(value);
                        statusMessage = "Upkeep hour set to " + value + ":00";
                        statusIsError = false;
                    }
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
case "set_road_speed" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    if (value < 1.0) {
                        statusMessage = "Speed must be >= 1.0!";
                        statusIsError = true;
                    } else {
                        config.setRoadSpeedMultiplier(value);
                        statusMessage = "Road speed multiplier set to " + value + "x";
                        statusIsError = false;
                    }
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Wild Protection Settings
            case "toggle_wild" -> {
                boolean current = config.isWildProtectionEnabled();
                config.setWildProtectionEnabled(!current);
                statusMessage = "Wild protection " + (!current ? "ENABLED" : "DISABLED");
                statusIsError = false;
            }
            case "set_wild_min_y" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setWildProtectionMinY(value);
                    statusMessage = "Wild min Y set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "toggle_wild_destroy" -> {
                boolean current = config.isWildDestroyBelowAllowed();
                config.setWildDestroyBelowAllowed(!current);
                statusMessage = "Destroy below Y: " + (!current ? "ALLOWED" : "BLOCKED");
                statusIsError = false;
            }
            case "toggle_wild_build" -> {
                boolean current = config.isWildBuildBelowAllowed();
                config.setWildBuildBelowAllowed(!current);
                statusMessage = "Build below Y: " + (!current ? "ALLOWED" : "BLOCKED");
                statusIsError = false;
            }

            // Personal Claims Settings
            case "set_starting_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setStartingClaims(value);
                    statusMessage = "Starting claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claims_per_hour" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setClaimsPerHour(value);
                    statusMessage = "Claims per hour set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_max_personal_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setMaxClaims(value);
                    statusMessage = "Max personal claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claim_buffer" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setClaimBufferSize(value);
                    statusMessage = "Claim buffer set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Data Management
            case "reload" -> {
                config.reload();
                statusMessage = "Configuration reloaded!";
                statusIsError = false;
            }
            case "save_all" -> {
                plugin.getTownStorage().saveAll();
                plugin.getClaimStorage().saveAll();
                statusMessage = "All data saved!";
                statusIsError = false;
            }
            case "backup" -> {
                plugin.getTownStorage().createBackup();
                statusMessage = "Backup created!";
                statusIsError = false;
            }
            case "give_plots" -> {
                if (targetTownName == null || targetTownName.isEmpty()) {
                    statusMessage = "Enter town name!";
                    statusIsError = true;
                } else {
                    Town town = plugin.getTownStorage().getTown(targetTownName);
                    if (town == null) {
                        statusMessage = "Town not found!";
                        statusIsError = true;
                    } else {
                        try {
                            int amount = Integer.parseInt(inputValue);
                            if (amount <= 0) {
                                statusMessage = "Amount must be positive!";
                                statusIsError = true;
                            } else {
                                town.addBonusClaims(amount);
                                plugin.getTownStorage().saveTown(town);
                                statusMessage = "Gave " + amount + " bonus plots to " + town.getName() + " (now has " + town.getBonusClaims() + " bonus)";
                                statusIsError = false;
                            }
                        } catch (NumberFormatException e) {
                            statusMessage = "Invalid amount!";
                            statusIsError = true;
                        }
                    }
                }
            }
            case "take_plots" -> {
                if (targetTownName == null || targetTownName.isEmpty()) {
                    statusMessage = "Enter town name!";
                    statusIsError = true;
                } else {
                    Town town = plugin.getTownStorage().getTown(targetTownName);
                    if (town == null) {
                        statusMessage = "Town not found!";
                        statusIsError = true;
                    } else {
                        try {
                            int amount = Integer.parseInt(inputValue);
                            if (amount <= 0) {
                                statusMessage = "Amount must be positive!";
                                statusIsError = true;
                            } else {
                                town.addBonusClaims(-amount);
                                plugin.getTownStorage().saveTown(town);
                                statusMessage = "Took " + amount + " bonus plots from " + town.getName() + " (now has " + town.getBonusClaims() + " bonus)";
                                statusIsError = false;
                            }
                        } catch (NumberFormatException e) {
                            statusMessage = "Invalid amount!";
                            statusIsError = true;
                        }
                    }
                }
            }

            // Admin Claim/Unclaim Actions
            case "admin_claim" -> {
                // Get town name from input field
                String townName = inputValue.trim();
                if (townName.isEmpty()) {
                    // Try using admin's current town
                    Town myTown = plugin.getTownStorage().getPlayerTown(playerId);
                    if (myTown == null) {
                        statusMessage = "Enter town name in text field!";
                        statusIsError = true;
                    } else {
                        townName = myTown.getName();
                    }
                }

                if (!townName.isEmpty()) {
                    Town town = plugin.getTownStorage().getTown(townName);
                    if (town == null) {
                        statusMessage = "Town '" + townName + "' not found!";
                        statusIsError = true;
                    } else {
                        // Get player position from world
                        var players = world.getPlayers();
                        Player adminPlayer = null;
                        for (var p : players) {
                            if (p.getUuid().equals(playerId)) {
                                adminPlayer = p;
                                break;
                            }
                        }

                        if (adminPlayer == null) {
                            statusMessage = "Could not find your position!";
                            statusIsError = true;
                        } else {
                            var pos = adminPlayer.getTransformComponent().getPosition();
                            int chunkX = com.hytown.util.ChunkUtil.toChunkX(pos.getX());
                            int chunkZ = com.hytown.util.ChunkUtil.toChunkZ(pos.getZ());
                            String worldName = world.getName();
                            String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                            // Check if already claimed
                            if (town.ownsClaim(claimKey)) {
                                statusMessage = "Already claimed by " + town.getName();
                                statusIsError = true;
                            } else {
                                Town existingTown = plugin.getTownStorage().getTownByClaimKey(claimKey);
                                if (existingTown != null) {
                                    statusMessage = "Claimed by: " + existingTown.getName();
                                    statusIsError = true;
                                } else {
                                    // Claim it
                                    town.addClaim(claimKey);
                                    plugin.getTownStorage().indexClaim(claimKey, town.getName());
                                    plugin.getTownStorage().saveTown(town);
                                    plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
                                    statusMessage = "Claimed [" + chunkX + "," + chunkZ + "] for " + town.getName();
                                    statusIsError = false;
                                }
                            }
                        }
                    }
                }
            }
            case "admin_unclaim" -> {
                // Get player position
                var players = world.getPlayers();
                Player adminPlayer = null;
                for (var p : players) {
                    if (p.getUuid().equals(playerId)) {
                        adminPlayer = p;
                        break;
                    }
                }

                if (adminPlayer == null) {
                    statusMessage = "Could not find your position!";
                    statusIsError = true;
                } else {
                    var pos = adminPlayer.getTransformComponent().getPosition();
                    int chunkX = com.hytown.util.ChunkUtil.toChunkX(pos.getX());
                    int chunkZ = com.hytown.util.ChunkUtil.toChunkZ(pos.getZ());
                    String worldName = world.getName();
                    String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                    Town town = plugin.getTownStorage().getTownByClaimKey(claimKey);
                    if (town == null) {
                        statusMessage = "Chunk not claimed by any town";
                        statusIsError = true;
                    } else {
                        String townName = town.getName();
                        boolean wasRoad = town.isRoadClaim(claimKey);
                        if (wasRoad) {
                            town.removeClaimFromRoad(claimKey);
                        }
                        town.removeClaim(claimKey);
                        plugin.getTownStorage().unindexClaim(claimKey);
                        plugin.getTownStorage().saveTown(town);
                        plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
                        statusMessage = "Unclaimed [" + chunkX + "," + chunkZ + "] from " + townName + (wasRoad ? " (road)" : "");
                        statusIsError = false;
                    }
                }
            }
        }

        // Save config after any setting change
        if (!statusIsError && !action.equals("reload") && !action.equals("save_all") && !action.equals("backup")) {
            config.save();
        }

        inputValue = "";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTown_AdminMenu.ui");

        PluginConfig config = plugin.getPluginConfig();

        // Status message
        cmd.set("#StatusMessage.Text", statusMessage);
        cmd.set("#StatusMessage.Style.TextColor", statusIsError ? "#ff5555" : "#55ff55");

        // Title is already set in the .ui file, don't override it

        // Tab buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EconomyTab",
                EventData.of("Tab", "economy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UpkeepTab",
                EventData.of("Tab", "upkeep"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#WildTab",
                EventData.of("Tab", "wild"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PersonalTab",
                EventData.of("Tab", "personal"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DataTab",
                EventData.of("Tab", "data"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimTab",
                EventData.of("Tab", "claim"), false);

        // Tab highlighting not supported on TextButton - panels are shown/hidden instead

        // Input field
        cmd.set("#InputField.Value", inputValue);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputField",
                EventData.of("@InputValue", "#InputField.Value"), false);

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);

        // Show/hide panels based on current tab
        cmd.set("#EconomyPanel.Visible", currentTab.equals("economy"));
        cmd.set("#UpkeepPanel.Visible", currentTab.equals("upkeep"));
        cmd.set("#WildPanel.Visible", currentTab.equals("wild"));
        cmd.set("#PersonalPanel.Visible", currentTab.equals("personal"));
        cmd.set("#DataPanel.Visible", currentTab.equals("data"));
        cmd.set("#ClaimPanel.Visible", currentTab.equals("claim"));

        switch (currentTab) {
            case "economy" -> buildEconomyPanel(cmd, evt, config);
            case "upkeep" -> buildUpkeepPanel(cmd, evt, config);
            case "wild" -> buildWildPanel(cmd, evt, config);
            case "personal" -> buildPersonalPanel(cmd, evt, config);
            case "data" -> buildDataPanel(cmd, evt, config);
            case "claim" -> buildClaimPanel(cmd, evt);
        }
    }

    private void buildEconomyPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#TownCostValue.Text", String.format("$%.0f", config.getTownCreationCost()));
        cmd.set("#ClaimCostValue.Text", String.format("$%.0f", config.getTownClaimCost()));
        cmd.set("#MaxClaimsValue.Text", String.valueOf(config.getMaxTownClaims()));

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetTownCostBtn",
                EventData.of("Action", "set_town_cost"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimCostBtn",
                EventData.of("Action", "set_claim_cost"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetMaxClaimsBtn",
                EventData.of("Action", "set_max_claims"), false);
    }

    private void buildUpkeepPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#UpkeepBaseValue.Text", String.format("$%.0f/day", config.getTownUpkeepBase()));
        cmd.set("#UpkeepPerPlotValue.Text", String.format("$%.0f/plot/day", config.getTownUpkeepPerClaim()));
        cmd.set("#UpkeepHourValue.Text", config.getTownUpkeepHour() + ":00");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepBaseBtn",
                EventData.of("Action", "set_upkeep_base"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepPerPlotBtn",
                EventData.of("Action", "set_upkeep_per_plot"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepHourBtn",
                EventData.of("Action", "set_upkeep_hour"), false);
cmd.set("#RoadSpeedValue.Text", String.format("%.1fx", config.getRoadSpeedMultiplier()));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetRoadSpeedBtn",
                EventData.of("Action", "set_road_speed"), false);
    }

    private void buildWildPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#WildEnabledValue.Text", config.isWildProtectionEnabled() ? "ENABLED" : "DISABLED");
        cmd.set("#WildMinYValue.Text", String.valueOf(config.getWildProtectionMinY()));
        cmd.set("#WildDestroyValue.Text", config.isWildDestroyBelowAllowed() ? "ALLOWED" : "BLOCKED");
        cmd.set("#WildBuildValue.Text", config.isWildBuildBelowAllowed() ? "ALLOWED" : "BLOCKED");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleWildBtn",
                EventData.of("Action", "toggle_wild"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetWildMinYBtn",
                EventData.of("Action", "set_wild_min_y"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleDestroyBtn",
                EventData.of("Action", "toggle_wild_destroy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBuildBtn",
                EventData.of("Action", "toggle_wild_build"), false);
    }

    private void buildPersonalPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#StartingClaimsValue.Text", String.valueOf(config.getStartingClaims()));
        cmd.set("#ClaimsPerHourValue.Text", String.valueOf(config.getClaimsPerHour()));
        cmd.set("#MaxPersonalClaimsValue.Text", String.valueOf(config.getMaxClaims()));
        cmd.set("#ClaimBufferValue.Text", String.valueOf(config.getClaimBufferSize()));

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartingClaimsBtn",
                EventData.of("Action", "set_starting_claims"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimsPerHourBtn",
                EventData.of("Action", "set_claims_per_hour"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetMaxPersonalClaimsBtn",
                EventData.of("Action", "set_max_personal_claims"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimBufferBtn",
                EventData.of("Action", "set_claim_buffer"), false);
    }

    private void buildDataPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        int townCount = plugin.getTownStorage().getTownCount();
        cmd.set("#TownCountValue.Text", String.valueOf(townCount));

        var backups = plugin.getTownStorage().listBackups();
        cmd.set("#BackupCountValue.Text", backups.size() + " backups");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadBtn",
                EventData.of("Action", "reload"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveAllBtn",
                EventData.of("Action", "save_all"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackupBtn",
                EventData.of("Action", "backup"), false);

        // Give/Take plots section
        cmd.set("#TargetTownField.Value", targetTownName);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TargetTownField",
                EventData.of("@TargetTownName", "#TargetTownField.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#GivePlotsBtn",
                EventData.of("Action", "give_plots"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TakePlotsBtn",
                EventData.of("Action", "take_plots"), false);

        // Show current bonus plots for target town
        if (targetTownName != null && !targetTownName.isEmpty()) {
            Town targetTown = plugin.getTownStorage().getTown(targetTownName);
            if (targetTown != null) {
                cmd.set("#TargetTownBonusValue.Text", String.valueOf(targetTown.getBonusClaims()));
            } else {
                cmd.set("#TargetTownBonusValue.Text", "N/A");
            }
        } else {
            cmd.set("#TargetTownBonusValue.Text", "0");
        }
    }

    private void buildClaimPanel(UICommandBuilder cmd, UIEventBuilder evt) {
        // Get current player's town info using stored playerId
        Town town = plugin.getTownStorage().getPlayerTown(playerId);
        if (town != null) {
            cmd.set("#CurrentTownName.Text", town.getName());
            cmd.set("#CurrentTownClaims.Text", String.valueOf(town.getClaimCount()));
            cmd.set("#CurrentTownRoads.Text", town.getRoads().size() + " (" + town.getTotalRoadChunks() + " chunks)");
        } else {
            cmd.set("#CurrentTownName.Text", "None (use /townadmin join)");
            cmd.set("#CurrentTownClaims.Text", "-");
            cmd.set("#CurrentTownRoads.Text", "-");
        }

        // Get current chunk info
        var players = world.getPlayers();
        Player adminPlayer = null;
        for (var p : players) {
            if (p.getUuid().equals(playerId)) {
                adminPlayer = p;
                break;
            }
        }

        if (adminPlayer != null) {
            var pos = adminPlayer.getTransformComponent().getPosition();
            int chunkX = com.hytown.util.ChunkUtil.toChunkX(pos.getX());
            int chunkZ = com.hytown.util.ChunkUtil.toChunkZ(pos.getZ());
            String worldName = world.getName();
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;

            cmd.set("#CurrentChunkInfo.Text", "[" + chunkX + ", " + chunkZ + "]");

            // Check who owns this chunk
            Town chunkOwner = plugin.getTownStorage().getTownByClaimKey(claimKey);
            if (chunkOwner != null) {
                boolean isRoad = chunkOwner.isRoadClaim(claimKey);
                cmd.set("#ChunkOwnerInfo.Text", chunkOwner.getName() + (isRoad ? " (Road)" : ""));
                cmd.set("#ChunkOwnerInfo.Style.TextColor", "#ffaa00");
            } else {
                // Check for personal claim
                java.util.UUID personalOwner = plugin.getClaimManager().getOwnerAt(worldName, pos.getX(), pos.getZ());
                if (personalOwner != null) {
                    String ownerName = plugin.getClaimStorage().getPlayerName(personalOwner);
                    cmd.set("#ChunkOwnerInfo.Text", "Personal: " + ownerName);
                    cmd.set("#ChunkOwnerInfo.Style.TextColor", "#5555ff");
                } else {
                    cmd.set("#ChunkOwnerInfo.Text", "Unclaimed");
                    cmd.set("#ChunkOwnerInfo.Style.TextColor", "#888888");
                }
            }
        } else {
            cmd.set("#CurrentChunkInfo.Text", "Unknown");
            cmd.set("#ChunkOwnerInfo.Text", "Unknown");
        }

        // Bind claim/unclaim buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AdminClaimBtn",
                EventData.of("Action", "admin_claim"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AdminUnclaimBtn",
                EventData.of("Action", "admin_unclaim"), false);
    }

    /**
     * Open the admin GUI for a player.
     */
    public static void openFor(HyTown plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        TownAdminGui gui = new TownAdminGui(playerRef, plugin, world);
        world.execute(() -> {
            player.getPageManager().openCustomPage(ref, store, gui);
        });
    }

    /**
     * Data class for receiving UI events.
     */
    public static class AdminData {
        public String close;
        public String tab;
        public String action;
        public String inputValue;
        public String targetTownName;

        public static final BuilderCodec<AdminData> CODEC = BuilderCodec.<AdminData>builder(AdminData.class, AdminData::new)
                .addField(new KeyedCodec<>("Close", Codec.STRING),
                        (data, s) -> data.close = s,
                        data -> data.close)
                .addField(new KeyedCodec<>("Tab", Codec.STRING),
                        (data, s) -> data.tab = s,
                        data -> data.tab)
                .addField(new KeyedCodec<>("Action", Codec.STRING),
                        (data, s) -> data.action = s,
                        data -> data.action)
                .addField(new KeyedCodec<>("@InputValue", Codec.STRING),
                        (data, s) -> data.inputValue = s,
                        data -> data.inputValue)
                .addField(new KeyedCodec<>("@TargetTownName", Codec.STRING),
                        (data, s) -> data.targetTownName = s,
                        data -> data.targetTownName)
                .build();
    }
}
