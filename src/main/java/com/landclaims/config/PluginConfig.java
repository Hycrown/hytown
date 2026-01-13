package com.landclaims.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration manager for LandClaims plugin.
 */
public class PluginConfig {
    private final Path configFile;
    private final Gson gson;
    private ConfigData config;

    public PluginConfig(Path dataDirectory) {
        this.configFile = dataDirectory.resolve("config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.config = new ConfigData();
        load();
    }

    public void load() {
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                ConfigData loaded = gson.fromJson(json, ConfigData.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, gson.toJson(config));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Configuration getters
    public int getChunksPerHour() {
        return config.chunksPerHour;
    }

    public int getStartingChunks() {
        return config.startingChunks;
    }

    public int getMaxClaimsPerPlayer() {
        return config.maxClaimsPerPlayer;
    }

    public int getPlaytimeUpdateIntervalSeconds() {
        return config.playtimeUpdateIntervalSeconds;
    }

    /**
     * Calculate how many chunks a player can claim based on their playtime.
     */
    public int calculateMaxClaims(double playtimeHours) {
        int fromPlaytime = (int) (playtimeHours * config.chunksPerHour);
        int total = config.startingChunks + fromPlaytime;
        return Math.min(total, config.maxClaimsPerPlayer);
    }

    /**
     * Calculate hours needed to unlock the next claim.
     */
    public double hoursUntilNextClaim(double currentHours, int currentClaims) {
        if (currentClaims >= config.maxClaimsPerPlayer) {
            return -1; // Already at max
        }

        int fromPlaytime = currentClaims - config.startingChunks;
        if (fromPlaytime < 0) {
            return 0; // Still have starting claims available
        }

        double hoursNeeded = (double) (fromPlaytime + 1) / config.chunksPerHour;
        return Math.max(0, hoursNeeded - currentHours);
    }

    /**
     * Configuration data structure for JSON serialization.
     */
    private static class ConfigData {
        int chunksPerHour = 2;
        int startingChunks = 4;
        int maxClaimsPerPlayer = 50;
        int playtimeUpdateIntervalSeconds = 60;
    }
}
