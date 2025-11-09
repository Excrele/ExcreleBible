package com.excrele.bible;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Whoa, this class handles when a player types /bible!
 * It checks args: if "random", grabs a surprise verse. Else, needs book/chapter/verse.
 * Then asks BibleFetcher for the full Verse (ref + text).
 * Sends to player in green. Errors? Red help message.
 * FIXED: Removed @NotNull imports – they're for IDE hints, not code magic. Compiles clean now!
 * Author: excrele
 */
public class BibleCommand implements CommandExecutor {

    private final BiblePlugin plugin;

    // Constructor: Save the plugin reference so we can use its tools
    public BibleCommand(BiblePlugin plugin) {
        this.plugin = plugin;
    }

    // This magic method runs every time /bible is typed
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only players can use this (not console) – keeps it fun in-game
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Hey, only players can request verses!");
            return true;
        }

        Player player = (Player) sender;

        // Check for /bible random (1 arg)
        if (args.length == 1 && args[0].equalsIgnoreCase("random")) {
            handleRandom(player);
            return true;
        }

        // Old check: Exactly 3 args for specific verse
        if (args.length != 3) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /bible <book> <chapter> <verse> OR /bible random");
            player.sendMessage(ChatColor.YELLOW + "Examples: /bible John 3 16  or  /bible random");
            return true;
        }

        // Grab args and clean them up
        String book = capitalizeFirst(args[0]);
        int chapter;
        int verse;
        try {
            chapter = Integer.parseInt(args[1]);
            verse = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Chapter and verse must be numbers, dude!");
            return true;
        }

        // Show they're waiting – async fetch takes a sec
        player.sendMessage(ChatColor.GOLD + "Fetching " + book + " " + chapter + ":" + verse + " from NABRE... ⏳");

        // Use plugin's scheduler to fetch async (won't freeze the game)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get full Verse object (ref + text)
            BibleFetcher.Verse fullVerse = BibleFetcher.getVerseFull(book, chapter, verse);

            // Switch back to main thread to chat with player (Bukkit rule)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (fullVerse != null && fullVerse.text != null) {
                    // Success! Send in fancy colors
                    player.sendMessage(ChatColor.DARK_GREEN + "📖 " + fullVerse.reference + " (NABRE)");
                    player.sendMessage(ChatColor.GREEN + fullVerse.text);
                } else {
                    player.sendMessage(ChatColor.RED + "Oops! Couldn't find that verse. Check spelling? (Try 'John' not 'jhon')");
                }
            });
        });

        return true;
    }

    // Helper method for /random – keeps code clean
    private void handleRandom(Player player) {
        player.sendMessage(ChatColor.GOLD + "Rolling for a random NABRE verse... 🎲");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BibleFetcher.Verse fullVerse = BibleFetcher.getRandomVerse();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (fullVerse != null && fullVerse.text != null) {
                    player.sendMessage(ChatColor.DARK_GREEN + "📖 " + fullVerse.reference + " (NABRE – Random Pick!)");
                    player.sendMessage(ChatColor.GREEN + fullVerse.text);
                } else {
                    player.sendMessage(ChatColor.RED + "Couldn't fetch random verse yet – Bible index loading! Try again in 5 secs. ⏳");
                }
            });
        });
    }

    // Helper: Capitalize first letter of book (API likes "John", not "john")
    private String capitalizeFirst(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}