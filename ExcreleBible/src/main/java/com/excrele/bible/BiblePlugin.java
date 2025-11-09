package com.excrele.bible;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hey kiddo! This is the main entry point for our ExcreleBible plugin.
 * When the server starts, onEnable() runs to set things up.
 * We register our /bible command here so players can use it.
 * NEW: We also load Bible structure (books/chapters/verses) from API for random picks – async so it doesn't slow startup.
 * Author: excrele
 */
public class BiblePlugin extends JavaPlugin {

    // This runs when the plugin loads (server start)
    @Override
    public void onEnable() {
        // Save default config if none exists – for future API keys if needed
        saveDefaultConfig();

        // NEW: Load metadata async (books, chapters, verses counts) for /random command
        getServer().getScheduler().runTaskAsynchronously(this, BibleFetcher::loadMetadata);

        // Register the command – links /bible to our BibleCommand class
        getCommand("bible").setExecutor(new BibleCommand(this));

        // Tell the console it's ready (fun log!)
        getLogger().info("ExcreleBible loaded! Type /bible John 3 16 or /bible random for God's love (or surprise) verse. 📖");
    }

    // This runs when plugin unloads (server stop) – cleanup if needed
    @Override
    public void onDisable() {
        getLogger().info("ExcreleBible says goodbye! Thanks for sharing Scripture. ✌️");
    }
}