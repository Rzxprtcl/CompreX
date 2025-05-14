package org.examplez.autocompressor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AutoCompressorCommand implements CommandExecutor {

    private final AutoCompressor plugin;

    public AutoCompressorCommand(AutoCompressor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("autocompressor.use")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        plugin.openMainMenu(player);
        return true;
    }
}