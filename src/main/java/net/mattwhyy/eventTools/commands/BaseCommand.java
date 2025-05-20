package net.mattwhyy.eventTools.commands;

import net.mattwhyy.eventTools.EventTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseCommand {
    protected final EventTools plugin;

    public BaseCommand(EventTools plugin) {
        this.plugin = plugin;
    }

    public abstract boolean execute(CommandSender sender, String[] args);

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }

    protected boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cOnly players can use this command!");
            return false;
        }
        return true;
    }

    protected boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            plugin.sendMessage(sender, "&cYou don't have permission to use this command!");
            return false;
        }
        return true;
    }

    protected boolean requireEventActive(CommandSender sender) {
        if (!plugin.eventActive) {
            plugin.sendMessage(sender, "&cNo event is currently running!");
            return false;
        }
        return true;
    }

    protected List<Player> getTargetPlayers(CommandSender sender, String target) {
        return plugin.getTargetPlayers(sender, target);
    }

    protected int parseInt(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}