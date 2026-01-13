package com.landclaims.util;

import com.hypixel.hytale.server.core.Message;

import java.awt.Color;

/**
 * Centralized message formatting for LandClaims.
 */
public class Messages {
    // Colors
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color AQUA = new Color(85, 255, 255);
    private static final Color WHITE = new Color(255, 255, 255);

    // Claim messages
    public static Message chunkClaimed(int chunkX, int chunkZ) {
        return Message.raw("Claimed chunk at [" + chunkX + ", " + chunkZ + "]!").color(GREEN);
    }

    public static Message chunkUnclaimed(int chunkX, int chunkZ) {
        return Message.raw("Unclaimed chunk at [" + chunkX + ", " + chunkZ + "]!").color(GREEN);
    }

    public static Message chunkAlreadyClaimed() {
        return Message.raw("This chunk is already claimed!").color(RED);
    }

    public static Message chunkClaimedByOther(String ownerName) {
        return Message.raw("This chunk is claimed by " + ownerName + "!").color(RED);
    }

    public static Message notYourClaim() {
        return Message.raw("This chunk is not claimed by you!").color(RED);
    }

    public static Message notInClaim() {
        return Message.raw("You are not standing in a claimed chunk!").color(RED);
    }

    public static Message claimLimitReached(int current, int max) {
        return Message.raw("You've reached your claim limit (" + current + "/" + max + ")!").color(RED);
    }

    public static Message notEnoughPlaytime(double hoursNeeded) {
        return Message.raw("You need " + String.format("%.1f", hoursNeeded) + " more hours of playtime to claim another chunk!").color(RED);
    }

    // Claims list
    public static Message claimsHeader(int count, int max) {
        return Message.raw("Your claims (" + count + "/" + max + "):").color(GOLD);
    }

    public static Message claimEntry(String world, int chunkX, int chunkZ) {
        return Message.raw("- " + world + " [" + chunkX + ", " + chunkZ + "]").color(GRAY);
    }

    public static Message noClaims() {
        return Message.raw("You don't have any claims. Use /claim to claim the chunk you're standing in!").color(YELLOW);
    }

    // Trust messages
    public static Message playerTrusted(String playerName) {
        return Message.raw("Trusted " + playerName + " in all your claims!").color(GREEN);
    }

    public static Message playerUntrusted(String playerName) {
        return Message.raw("Removed trust from " + playerName + "!").color(GREEN);
    }

    public static Message playerNotTrusted(String playerName) {
        return Message.raw(playerName + " is not trusted in your claims!").color(RED);
    }

    public static Message playerAlreadyTrusted(String playerName) {
        return Message.raw(playerName + " is already trusted in your claims!").color(YELLOW);
    }

    public static Message cannotTrustSelf() {
        return Message.raw("You cannot trust yourself!").color(RED);
    }

    public static Message trustedPlayersHeader(int count) {
        return Message.raw("Trusted players (" + count + "):").color(GOLD);
    }

    public static Message trustedPlayerEntry(String playerName) {
        return Message.raw("- " + playerName).color(GRAY);
    }

    public static Message noTrustedPlayers() {
        return Message.raw("You haven't trusted anyone. Use /trust <player> to trust someone!").color(YELLOW);
    }

    // Playtime messages
    public static Message playtimeInfo(double hours, int claimsUsed, int claimsAvailable) {
        return Message.raw("Playtime: " + String.format("%.1f", hours) + " hours | Claims: " + claimsUsed + "/" + claimsAvailable).color(AQUA);
    }

    public static Message nextClaimIn(double hoursUntilNext) {
        return Message.raw("Next claim available in " + String.format("%.1f", hoursUntilNext) + " hours").color(GRAY);
    }

    // Player lookup
    public static Message playerNotFound(String playerName) {
        return Message.raw("Player '" + playerName + "' not found!").color(RED);
    }

    // Help messages
    public static Message helpHeader() {
        return Message.raw("=== LandClaims Help ===").color(GOLD);
    }

    public static Message helpEntry(String command, String description) {
        return Message.raw("/" + command + " - " + description).color(GRAY);
    }

    // Protection messages
    public static Message cannotBuildHere() {
        return Message.raw("You cannot build in this claimed area!").color(RED);
    }

    public static Message cannotInteractHere() {
        return Message.raw("You cannot interact in this claimed area!").color(RED);
    }
}
