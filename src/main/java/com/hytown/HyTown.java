package com.hytown;

import com.hytown.api.HyTownAPI;
import com.hytown.events.TownEventBus;
import com.hytown.commands.TownCommand;
import com.hytown.commands.ResidentCommand;
import com.hytown.commands.PlotCommand;
import com.hytown.commands.TownyAdminCommand;
import com.hytown.commands.TownyHelpCommand;
import com.hytown.commands.TownPopCommand;
import com.hytown.commands.WildernessCommand;
import com.hytown.config.BlockGroups;
import com.hytown.config.PluginConfig;
import com.hytown.config.WildernessHarvestConfig;
import com.hytown.data.ClaimStorage;
import com.hytown.data.PlaytimeStorage;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.storage.StorageConfig;
import com.hytown.storage.StorageProvider;
import com.hytown.storage.FlatfileStorage;
import com.hytown.storage.SQLStorage;
import com.hytown.storage.MongoStorage;
import com.hytown.listeners.ClaimProtectionListener;
import com.hytown.managers.ClaimManager;
import com.hytown.managers.PlaytimeManager;
import com.hytown.map.ClaimMapOverlayProvider;
import com.hytown.map.HyTownWorldMapProvider;
import com.hytown.systems.BlockBreakProtectionSystem;
import com.hytown.systems.BlockDamageProtectionSystem;
import com.hytown.systems.BlockPlaceProtectionSystem;
import com.hytown.systems.BlockUseProtectionSystem;
import com.hytown.systems.ClaimTitleSystem;
import com.hytown.systems.TownCreatureDespawnSystem;
import com.hytown.systems.WildernessHarvestSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;

/**
 * HyTown - A chunk-based land claiming plugin with playtime-based limits.
 *
 * Features:
 * - Claim chunks to protect your builds
 * - More playtime = more claim chunks available
 * - Trust other players in your claims
 * - Full protection for claimed areas
 */
public class HyTown extends JavaPlugin {

    private PluginConfig config;
    private BlockGroups blockGroups;
    private WildernessHarvestConfig wildernessHarvestConfig;
    private ClaimStorage claimStorage;
    private PlaytimeStorage playtimeStorage;
    private TownStorage townStorage;
    private ClaimManager claimManager;
    private PlaytimeManager playtimeManager;
    private ClaimProtectionListener protectionListener;
    private ClaimMapOverlayProvider mapOverlayProvider;
    private ClaimTitleSystem claimTitleSystem;
    private com.hytown.managers.UpkeepManager upkeepManager;
    private HyTownAPI api;
    private TownEventBus eventBus;

    // Scheduled task system - efficient thread pool for all periodic tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> activeCountdowns = new ConcurrentHashMap<>();
    private ScheduledFuture<?> upkeepTask;
    private ScheduledFuture<?> autoSaveTask;
    private static final double MOVE_THRESHOLD = 0.5;
    private static final int TELEPORT_COUNTDOWN = 5;
    private static final Color GREEN = new Color(85, 255, 85);

    // Track registered worlds for map provider
    public static final Map<String, World> WORLDS = new HashMap<>();

    public HyTown(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        getLogger().atSevere().log("========== HYTOWN PLUGIN STARTING ==========");

        // Initialize configuration
        config = new PluginConfig(getDataDirectory());
        blockGroups = new BlockGroups(getDataDirectory());
        wildernessHarvestConfig = new WildernessHarvestConfig(getDataDirectory(), getLogger());
        wildernessHarvestConfig.load();

        // Initialize storage
        claimStorage = new ClaimStorage(getDataDirectory());
        playtimeStorage = new PlaytimeStorage(getDataDirectory());

        // Initialize town storage with configurable providers
        StorageProvider loadProvider = createStorageProvider(config.getStorageConfig().getLoadFrom());
        StorageProvider saveProvider = createStorageProvider(config.getStorageConfig().getSaveTo());
        townStorage = new TownStorage(getDataDirectory(), loadProvider, saveProvider);

        // Log storage configuration
        getLogger().atInfo().log("Town storage: load from %s, save to %s",
                loadProvider != null ? loadProvider.getName() : "flatfile",
                saveProvider != null ? saveProvider.getName() : "flatfile");

        // Initialize static accessor for map system
        HyTownAccess.init(claimStorage, townStorage);

        // Initialize managers
        claimManager = new ClaimManager(claimStorage, playtimeStorage, config, blockGroups);
        playtimeManager = new PlaytimeManager(playtimeStorage, config);
        upkeepManager = new com.hytown.managers.UpkeepManager(config, townStorage, getLogger());

        // Initialize the event bus for other plugins to listen to town events
        eventBus = new TownEventBus();
        getLogger().atInfo().log("HyTown EventBus initialized - other plugins can register event listeners");

        // Initialize the public API for other plugins
        api = new HyTownAPI(claimStorage, townStorage, claimManager);
        getLogger().atInfo().log("HyTown API initialized - other plugins can now access town data");

        // Register Towny-style commands (/town with /t and /claim aliases)
        getCommandRegistry().registerCommand(new TownCommand(this));
        getCommandRegistry().registerCommand(new ResidentCommand(this));
        getCommandRegistry().registerCommand(new PlotCommand(this));
        getCommandRegistry().registerCommand(new TownyAdminCommand(this));
        getCommandRegistry().registerCommand(new TownyHelpCommand(this));
        getCommandRegistry().registerCommand(new TownPopCommand(this));
        getCommandRegistry().registerCommand(new WildernessCommand(this));
        getLogger().atInfo().log("Registered town commands: /town, /resident, /plot, /townadmin, /townhelp, /townpop");

        // Register protection event listeners (for PlayerInteractEvent)
        protectionListener = new ClaimProtectionListener(this);
        protectionListener.register(getEventRegistry());

        // Register player connect/disconnect events for name tracking
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Register world map provider codec
        try {
            IWorldMapProvider.CODEC.register(HyTownWorldMapProvider.ID,
                    HyTownWorldMapProvider.class, HyTownWorldMapProvider.CODEC);
            getLogger().atInfo().log("Registered HyTownWorldMapProvider codec");
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Failed to register world map provider codec");
        }

        // Register world events for map provider setup
        getEventRegistry().registerGlobal(AddWorldEvent.class, this::onWorldAdd);
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemove);

        // Initialize map overlay provider (for markers, kept for compatibility)
        mapOverlayProvider = new ClaimMapOverlayProvider(claimStorage, townStorage, getLogger());

        // Register ECS block protection systems
        getLogger().atSevere().log("[DEBUG] Registering ECS block protection systems...");
        try {
            getLogger().atSevere().log("[DEBUG] Registering BlockDamageProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockDamageProtectionSystem(claimManager, townStorage, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering WildernessHarvestSystem (must run before BlockBreakProtectionSystem)...");
            getEntityStoreRegistry().registerSystem(new WildernessHarvestSystem(claimManager, config, wildernessHarvestConfig, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockBreakProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockBreakProtectionSystem(claimManager, config, townStorage, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockPlaceProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockPlaceProtectionSystem(claimManager, config, townStorage, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockUseProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockUseProtectionSystem(claimManager, townStorage, getLogger()));

            // Register claim title system (shows banner when entering/leaving claims)
            getLogger().atSevere().log("[DEBUG] Creating ClaimTitleSystem...");
            claimTitleSystem = new ClaimTitleSystem(claimStorage, townStorage, config);
            getLogger().atSevere().log("[DEBUG] Registering ClaimTitleSystem...");
            getEntityStoreRegistry().registerSystem(claimTitleSystem);

            // Register creature despawn system (prevents mob spawns in towns)
            getLogger().atSevere().log("[DEBUG] Registering TownCreatureDespawnSystem...");
            getEntityStoreRegistry().registerSystem(new TownCreatureDespawnSystem(claimManager, getLogger()));

            getLogger().atSevere().log("[DEBUG] All ECS systems registered successfully!");
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[DEBUG] ERROR registering ECS systems");
        }
    }

    /**
     * Called when a world is added - set up our custom map provider.
     */
    private void onWorldAdd(AddWorldEvent event) {
        World world = event.getWorld();
        WORLDS.put(world.getName(), world);
        getLogger().atWarning().log("[Map] World added: %s (deleteOnRemove=%s)", world.getName(), world.getWorldConfig().isDeleteOnRemove());

        // Set our custom world map provider for persistent worlds
        try {
            if (!world.getWorldConfig().isDeleteOnRemove()) {
                world.getWorldConfig().setWorldMapProvider(new HyTownWorldMapProvider());
                getLogger().atWarning().log("[Map] Set HyTownWorldMapProvider for world: %s", world.getName());
            }
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[Map] Failed to set map provider for world: %s", world.getName());
        }
    }

    /**
     * Called when a world is removed.
     */
    private void onWorldRemove(RemoveWorldEvent event) {
        WORLDS.remove(event.getWorld().getName());
    }

    @Override
    public void start() {
        getLogger().atSevere().log("========== HYTOWN PLUGIN STARTED ==========");
        getLogger().atWarning().log("[Map] Known worlds: %s", WORLDS.keySet());

        // Check upkeep on startup (will only collect if it's the right time of day)
        if (upkeepManager != null) {
            upkeepManager.checkUpkeep();
            getLogger().atInfo().log("[Upkeep] Upkeep check on startup complete");
        }

        // Start background upkeep checker thread
        startUpkeepChecker();
    }

    /**
     * Schedule periodic upkeep checks using efficient ScheduledExecutorService.
     */
    private void startUpkeepChecker() {
        // Schedule upkeep check every 5 minutes (initial delay 5 min, then repeat)
        upkeepTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (upkeepManager != null) {
                    upkeepManager.checkUpkeep();
                }
            } catch (Exception e) {
                getLogger().atWarning().withCause(e).log("[Upkeep] Error in upkeep check");
            }
        }, 5, 5, TimeUnit.MINUTES);
        getLogger().atInfo().log("[Upkeep] Scheduled upkeep checker (every 5 minutes)");

        // Schedule auto-save
        startAutoSave();
    }

    /**
     * Schedule periodic auto-save using efficient ScheduledExecutorService.
     * Saves every 30 minutes to ensure data is preserved even if server crashes.
     */
    private void startAutoSave() {
        // Schedule auto-save every 30 minutes (initial delay 30 min, then repeat)
        autoSaveTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                autoSaveTowns();
            } catch (Exception e) {
                getLogger().atWarning().withCause(e).log("[AutoSave] Error during auto-save");
            }
        }, 30, 30, TimeUnit.MINUTES);
        getLogger().atInfo().log("[AutoSave] Scheduled auto-save (every 30 minutes)");
    }

    /**
     * Perform auto-save of all town data.
     */
    private void autoSaveTowns() {
        try {
            if (townStorage != null) {
                getLogger().atInfo().log("[AutoSave] Auto-saving town data...");
                townStorage.saveAll();
                // Also create a backup during auto-save
                townStorage.createBackup();
                getLogger().atInfo().log("[AutoSave] Auto-save complete");
            }
            if (claimStorage != null) {
                claimStorage.saveAll();
            }
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[AutoSave] CRITICAL: Auto-save failed!");
        }
    }

    /**
     * Refreshes the entire world map to show updated claims.
     * Clears both server and client caches to force regeneration.
     * Called after claiming/unclaiming chunks.
     */
    public void refreshWorldMap(String worldName) {
        World world = WORLDS.get(worldName);
        if (world == null) {
            getLogger().atWarning().log("[Map] Cannot refresh map - world not found: %s", worldName);
            return;
        }

        try {
            // 1. Set the generator (in case it changed)
            var worldMap = world.getWorldConfig().getWorldMapProvider().getGenerator(world);
            world.getWorldMapManager().setGenerator(worldMap);

            // 2. Clear server-side cached map images
            world.getWorldMapManager().clearImages();

            // 3. Clear each player's client-side cache to force re-request
            for (Player player : world.getPlayers()) {
                try {
                    player.getWorldMapTracker().clear();
                } catch (Exception e) {
                    getLogger().atFine().withCause(e).log("[Map] Error clearing map for player");
                }
            }

            getLogger().atInfo().log("[Map] Refreshed map for world: %s (%d players notified)",
                    worldName, world.getPlayers().size());
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("[Map] Error refreshing map for world: %s", worldName);
        }
    }

    /**
     * Refreshes specific chunks on the world map.
     * More efficient than refreshing the entire map when only a few chunks changed.
     *
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    public void refreshWorldMapChunk(String worldName, int chunkX, int chunkZ) {
        World world = WORLDS.get(worldName);
        if (world == null) {
            return;
        }

        try {
            // Create a set with this chunk and its neighbors (for border updates)
            LongSet chunksToRefresh = new LongOpenHashSet();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    chunksToRefresh.add(ChunkUtil.indexChunk(chunkX + dx, chunkZ + dz));
                }
            }

            // Clear server-side cached images for these chunks
            world.getWorldMapManager().clearImagesInChunks(chunksToRefresh);

            // Clear each player's client-side cache for these chunks
            for (Player player : world.getPlayers()) {
                try {
                    player.getWorldMapTracker().clearChunks(chunksToRefresh);
                } catch (Exception e) {
                    getLogger().atFine().withCause(e).log("[Map] Error clearing chunks for player");
                }
            }

            getLogger().atFine().log("[Map] Refreshed chunk %d,%d in world %s", chunkX, chunkZ, worldName);
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("[Map] Error refreshing chunk %d,%d", chunkX, chunkZ);
        }
    }

    @Override
    public void shutdown() {
        getLogger().atInfo().log("[Shutdown] HyTown shutting down...");

        // Cancel scheduled tasks
        if (upkeepTask != null) upkeepTask.cancel(false);
        if (autoSaveTask != null) autoSaveTask.cancel(false);

        // Shutdown scheduler (waits briefly for current tasks)
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("[Shutdown] Error stopping scheduler");
        }

        // Shutdown playtime manager (saves all sessions)
        if (playtimeManager != null) {
            try {
                getLogger().atInfo().log("[Shutdown] Saving playtime data...");
                playtimeManager.shutdown();
            } catch (Exception e) {
                getLogger().atSevere().withCause(e).log("[Shutdown] ERROR saving playtime data!");
            }
        }

        // Save all claim data
        if (claimStorage != null) {
            try {
                getLogger().atInfo().log("[Shutdown] Saving claim data...");
                claimStorage.saveAll();
            } catch (Exception e) {
                getLogger().atSevere().withCause(e).log("[Shutdown] ERROR saving claim data!");
            }
        }

        // Save all town data (most important!)
        if (townStorage != null) {
            try {
                getLogger().atInfo().log("[Shutdown] Saving town data...");
                townStorage.saveAll();
                // Create a final backup on shutdown
                townStorage.createBackup();
                getLogger().atInfo().log("[Shutdown] Town data saved successfully. Stats: " + townStorage.getStats());
            } catch (Exception e) {
                getLogger().atSevere().withCause(e).log("[Shutdown] CRITICAL ERROR saving town data!");
            }
        }

        getLogger().atInfo().log("[Shutdown] HyTown shutdown complete!");
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    /**
     * Gets the event bus for other plugins to listen to town events.
     * Available events:
     * - TownCreateEvent: When a town is created
     * - TownDeleteEvent: When a town is deleted
     * - TownRenameEvent: When a town is renamed
     * - TownJoinEvent: When a player joins a town
     * - TownLeaveEvent: When a player leaves a town
     * - TownClaimEvent: When a chunk is claimed
     * - TownUnclaimEvent: When a chunk is unclaimed
     * - TownMayorChangeEvent: When the mayor changes
     *
     * @return The TownEventBus instance
     */
    public TownEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the public API for other plugins to use.
     * The API provides methods to query town and claim information such as:
     * - Whether a location is in wilderness, a town, or a personal claim
     * - PvP and other settings at a location
     * - Player membership and ranks within towns
     * - Build/destroy permissions at locations
     * - Town listings and discovery
     *
     * @return The HyTownAPI instance
     */
    public HyTownAPI getAPI() {
        return api;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    /**
     * Called when a player joins - start tracking their playtime.
     * Note: This should be hooked into the server's player join event.
     */
    public void onPlayerJoin(java.util.UUID playerId) {
        playtimeManager.onPlayerJoin(playerId);
    }

    /**
     * Called when a player leaves - save their playtime.
     * Note: This should be hooked into the server's player leave event.
     */
    public void onPlayerLeave(java.util.UUID playerId) {
        playtimeManager.onPlayerLeave(playerId);
    }

    private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
    private static final java.awt.Color WHITE = new java.awt.Color(255, 255, 255);

    /**
     * Handles player connect event - register username and start playtime.
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        getLogger().atSevere().log("[DEBUG] onPlayerConnect START");
        try {
            var playerRef = event.getPlayerRef();
            getLogger().atSevere().log("[DEBUG] Got playerRef: %s", playerRef != null);
            if (playerRef != null) {
                java.util.UUID playerId = playerRef.getUuid();
                String username = playerRef.getUsername();
                getLogger().atSevere().log("[DEBUG] Player: %s (%s)", username, playerId);

                // Store player name for map display
                getLogger().atSevere().log("[DEBUG] Setting player name...");
                claimStorage.setPlayerName(playerId, username);

                // Start playtime tracking
                getLogger().atSevere().log("[DEBUG] Starting playtime tracking...");
                playtimeManager.onPlayerJoin(playerId);

                // Warn if player's town is overdue on upkeep
                getLogger().atSevere().log("[DEBUG] Checking upkeep warning...");
                if (upkeepManager != null) {
                    upkeepManager.warnIfOverdue(playerId);
                }

                getLogger().atSevere().log("[DEBUG] onPlayerConnect completed for: %s", username);
            }
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[DEBUG] ERROR in onPlayerConnect");
        }
    }

    /**
     * Handles player disconnect event - save playtime and clear map cache.
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            if (playerRef != null) {
                java.util.UUID playerId = playerRef.getUuid();

                // Save playtime
                playtimeManager.onPlayerLeave(playerId);

                // Clear map overlay cache for this player
                if (mapOverlayProvider != null) {
                    mapOverlayProvider.clearPlayerCache(playerId);
                }

                // Clear title tracking for this player
                if (claimTitleSystem != null) {
                    claimTitleSystem.removePlayer(playerId);
                }

                getLogger().atFine().log("Player disconnected: %s", playerId);
            }
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Error handling player disconnect");
        }
    }

    /**
     * Refreshes all claim chunks for a specific player.
     * Called when trust is added/removed to update the trusted player names on the map.
     *
     * @param playerId The UUID of the claim owner
     */
    public void refreshPlayerClaimChunks(java.util.UUID playerId) {
        var playerClaims = claimStorage.getPlayerClaims(playerId);
        if (playerClaims == null) {
            return;
        }

        // Group claims by world for efficient refresh
        Map<String, java.util.List<int[]>> claimsByWorld = new HashMap<>();
        for (var claim : playerClaims.getClaims()) {
            claimsByWorld.computeIfAbsent(claim.getWorld(), k -> new java.util.ArrayList<>())
                    .add(new int[]{claim.getChunkX(), claim.getChunkZ()});
        }

        // Refresh each world's chunks
        for (var entry : claimsByWorld.entrySet()) {
            String worldName = entry.getKey();
            World world = WORLDS.get(worldName);
            if (world == null) {
                continue;
            }

            try {
                LongSet chunksToRefresh = new LongOpenHashSet();
                for (int[] coords : entry.getValue()) {
                    // Add the claim chunk and its neighbors for border updates
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            chunksToRefresh.add(ChunkUtil.indexChunk(coords[0] + dx, coords[1] + dz));
                        }
                    }
                }

                // Clear server-side cached images
                world.getWorldMapManager().clearImagesInChunks(chunksToRefresh);

                // Clear each player's client-side cache
                for (Player player : world.getPlayers()) {
                    try {
                        player.getWorldMapTracker().clearChunks(chunksToRefresh);
                    } catch (Exception e) {
                        getLogger().atFine().withCause(e).log("[Map] Error clearing chunks for player");
                    }
                }

                getLogger().atFine().log("[Map] Refreshed %d claim chunks for player %s in world %s",
                        entry.getValue().size(), playerId, worldName);
            } catch (Exception e) {
                getLogger().atWarning().withCause(e).log("[Map] Error refreshing claims for player %s", playerId);
            }
        }
    }

    /**
     * Gets the map overlay provider for external access.
     */
    public ClaimMapOverlayProvider getMapOverlayProvider() {
        return mapOverlayProvider;
    }

    /**
     * Gets the claim storage for direct access (e.g., for name updates).
     */
    public ClaimStorage getClaimStorage() {
        return claimStorage;
    }

    /**
     * Gets the town storage for direct access.
     */
    public TownStorage getTownStorage() {
        return townStorage;
    }

    // ==================== STORAGE PROVIDER FACTORY ====================

    /**
     * Create a storage provider based on the type string.
     * @param type "flatfile", "sql", or "mongodb"
     * @return The storage provider, or null if type is unknown/flatfile (flatfile is default)
     */
    private StorageProvider createStorageProvider(String type) {
        if (type == null) {
            return null; // Will use default flatfile
        }

        StorageConfig storageConfig = config.getStorageConfig();

        switch (type.toLowerCase()) {
            case "sql":
            case "mysql":
                try {
                    SQLStorage sqlStorage = new SQLStorage(storageConfig.getSql());
                    sqlStorage.init();
                    getLogger().atInfo().log("SQL storage provider initialized: %s",
                            sqlStorage.getConnectionInfo());
                    return sqlStorage;
                } catch (Exception e) {
                    getLogger().atSevere().log("Failed to initialize SQL storage: %s", e.getMessage());
                    e.printStackTrace();
                    return null;
                }

            case "mongodb":
            case "mongo":
                try {
                    MongoStorage mongoStorage = new MongoStorage(storageConfig.getMongodb());
                    mongoStorage.init();
                    getLogger().atInfo().log("MongoDB storage provider initialized: %s",
                            mongoStorage.getConnectionInfo());
                    return mongoStorage;
                } catch (Exception e) {
                    getLogger().atSevere().log("Failed to initialize MongoDB storage: %s", e.getMessage());
                    e.printStackTrace();
                    return null;
                }

            case "flatfile":
            case "json":
            default:
                // Return null to use default flatfile storage in TownStorage
                return null;
        }
    }

    /**
     * Get the storage status for admin display.
     */
    public String getStorageStatus() {
        StorageProvider load = townStorage.getLoadProvider();
        StorageProvider save = townStorage.getSaveProvider();

        String loadStatus = load != null ?
                load.getName() + " (" + (load.isConnected() ? "connected" : "disconnected") + ")" :
                "flatfile";
        String saveStatus = save != null ?
                save.getName() + " (" + (save.isConnected() ? "connected" : "disconnected") + ")" :
                "flatfile";

        return "Load: " + loadStatus + ", Save: " + saveStatus;
    }

    /**
     * Force migrate all data to the save provider.
     * Useful for admin commands to trigger immediate migration.
     */
    public void migrateStorage() {
        getLogger().atInfo().log("Starting storage migration...");
        townStorage.saveAll();
        getLogger().atInfo().log("Storage migration complete - all data saved to %s",
                townStorage.getSaveProvider() != null ? townStorage.getSaveProvider().getName() : "flatfile");
    }

    /**
     * Gets the upkeep manager for calculating upkeep costs.
     */
    public com.hytown.managers.UpkeepManager getUpkeepManager() {
        return upkeepManager;
    }

    /**
     * Reloads the wilderness harvest configuration from file.
     */
    public void reloadWildernessHarvestConfig() {
        wildernessHarvestConfig.load();
        getLogger().atInfo().log("[HyTown] Wilderness harvest config reloaded");
    }

    /**
     * Start a countdown teleport to town spawn.
     */
    public void startTownSpawnCountdown(Player player, Store<EntityStore> store, Ref<EntityStore> playerRef,
                                         World world, String townName, double destX, double destY, double destZ,
                                         float destYaw, float destPitch) {
        String playerName = player.getDisplayName();

        // Cancel any existing countdown for this player
        cancelCountdown(playerName);

        // Store starting position
        final double startX, startY, startZ;
        try {
            Vector3d pos = player.getTransformComponent().getPosition();
            startX = pos.getX();
            startY = pos.getY();
            startZ = pos.getZ();
        } catch (Exception e) {
            player.sendMessage(Message.raw("Teleport failed - could not get position!"));
            return;
        }

        final int[] remaining = {TELEPORT_COUNTDOWN};
        final boolean[] cancelled = {false};

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (cancelled[0]) return;

            try {
                // Get current position
                Vector3d currentPos = player.getTransformComponent().getPosition();
                double dx = currentPos.getX() - startX;
                double dy = currentPos.getY() - startY;
                double dz = currentPos.getZ() - startZ;
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

                if (dist > MOVE_THRESHOLD) {
                    cancelled[0] = true;
                    player.sendMessage(Message.raw("TELEPORT CANCELLED - You moved!"));
                    player.sendMessage(Message.raw("You must REMAIN PERFECTLY STILL. Try /town spawn again."));
                    cancelCountdown(playerName);
                    return;
                }

                if (remaining[0] > 0) {
                    player.sendMessage(Message.raw("Teleporting to " + townName + " spawn in " + remaining[0] + "... DON'T MOVE!"));
                    remaining[0]--;
                } else {
                    cancelled[0] = true;
                    world.execute(() -> {
                        Vector3d pos = new Vector3d(destX, destY, destZ);
                        Vector3f rot = new Vector3f(destYaw, destPitch, 0);
                        Teleport teleport = new Teleport(world, pos, rot);
                        store.addComponent(playerRef, Teleport.getComponentType(), teleport);
                    });
                    player.sendMessage(Message.raw("Teleported to " + townName + " spawn!").color(GREEN));
                    cancelCountdown(playerName);
                }
            } catch (Exception e) {
                cancelled[0] = true;
                try { player.sendMessage(Message.raw("Teleport failed: " + e.getMessage())); } catch (Exception ignored) {}
                cancelCountdown(playerName);
            }
        }, 0, 1, TimeUnit.SECONDS);

        activeCountdowns.put(playerName, task);
    }

    private void cancelCountdown(String playerName) {
        ScheduledFuture<?> task = activeCountdowns.remove(playerName);
        if (task != null) task.cancel(false);
    }

    // ==================== TOWN CREATION (shared by command and GUI) ====================

    /**
     * Result of a town creation attempt.
     */
    public record TownCreationResult(boolean success, String message, Town town) {
        public static TownCreationResult error(String message) {
            return new TownCreationResult(false, message, null);
        }
        public static TownCreationResult success(String message, Town town) {
            return new TownCreationResult(true, message, town);
        }
    }

    /**
     * Creates a new town with the given name, claiming the chunk at the player's position.
     * This method is used by both the command and GUI to ensure consistent behavior.
     *
     * @param townName     The name for the new town
     * @param playerId     The UUID of the player creating the town
     * @param playerName   The name of the player creating the town
     * @param worldName    The world name
     * @param blockX       The X block coordinate (for chunk calculation)
     * @param blockZ       The Z block coordinate (for chunk calculation)
     * @param chargeCost   Whether to charge the creation cost
     * @return TownCreationResult indicating success or failure
     */
    public TownCreationResult createTown(String townName, UUID playerId, String playerName,
                                          String worldName, double blockX, double blockZ, boolean chargeCost) {
        // Validate name
        if (townName == null || townName.isEmpty()) {
            return TownCreationResult.error("Town name is required!");
        }
        if (townName.length() < 3 || townName.length() > 24) {
            return TownCreationResult.error("Town name must be 3-24 characters!");
        }
        if (!townName.matches("^[a-zA-Z0-9_-]+$")) {
            return TownCreationResult.error("Town name can only contain letters, numbers, _ and -");
        }

        // Check if player already in a town
        Town existingTown = townStorage.getPlayerTown(playerId);
        if (existingTown != null) {
            return TownCreationResult.error("You are already in town: " + existingTown.getName());
        }

        // Check if town name exists
        if (townStorage.townExists(townName)) {
            return TownCreationResult.error("A town with that name already exists!");
        }

        // Calculate chunk
        int chunkX = com.hytown.util.ChunkUtil.toChunkX(blockX);
        int chunkZ = com.hytown.util.ChunkUtil.toChunkZ(blockZ);
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if chunk is available
        Town existingClaimTown = townStorage.getTownByClaimKey(claimKey);
        if (existingClaimTown != null) {
            return TownCreationResult.error("Chunk is claimed by: " + existingClaimTown.getName());
        }

        UUID existingOwner = claimManager.getOwnerAt(worldName, blockX, blockZ);
        if (existingOwner != null && !existingOwner.equals(playerId)) {
            return TownCreationResult.error("Chunk is already claimed!");
        }

        // Handle cost
        double cost = chargeCost ? config.getTownCreationCost() : 0;
        if (cost > 0) {
            if (!com.hycrown.hyconomy.HyConomy.has(playerName, cost)) {
                return TownCreationResult.error("You need " + com.hycrown.hyconomy.HyConomy.format(cost) + " to create a town!");
            }
            if (!com.hycrown.hyconomy.HyConomy.withdraw(playerName, cost)) {
                return TownCreationResult.error("Failed to withdraw funds!");
            }
        }

        // Claim the chunk
        ClaimManager.ClaimResult claimResult = claimManager.claimChunk(playerId, worldName, blockX, blockZ);
        if (claimResult != ClaimManager.ClaimResult.SUCCESS) {
            // Refund if claim failed
            if (cost > 0) {
                com.hycrown.hyconomy.HyConomy.deposit(playerName, cost);
            }
            String reason = switch (claimResult) {
                case ALREADY_OWN -> "You already own this chunk personally";
                case CLAIMED_BY_OTHER -> "Chunk is claimed by someone else";
                case LIMIT_REACHED -> "Claim limit reached";
                case TOO_CLOSE_TO_OTHER_CLAIM -> "Too close to another claim";
                default -> "Unknown error";
            };
            return TownCreationResult.error("Failed to claim chunk: " + reason);
        }

        // Create the town
        Town town = new Town(townName, playerId, playerName);
        town.addClaim(claimKey);
        townStorage.saveTown(town);
        townStorage.indexPlayer(playerId, town.getName());

        // Fire event
        eventBus.fire(new com.hytown.events.TownCreateEvent(town, playerId, playerName, claimKey));

        // Refresh map
        refreshWorldMapChunk(worldName, chunkX, chunkZ);

        String successMsg = "Town '" + townName + "' created!";
        if (cost > 0) {
            successMsg += " (Cost: " + com.hycrown.hyconomy.HyConomy.format(cost) + ")";
        }
        return TownCreationResult.success(successMsg, town);
    }
}
