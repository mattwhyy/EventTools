package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BroadcastCommand extends BaseCommand {
    public BroadcastCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;

        if (args.length < 1) {
            plugin.sendMessage(sender, "&cUsage: /broadcast <message>");
            return true;
        }

        String fullMessage = String.join(" ", args);
        String formattedMessage = "&b&lBROADCAST &8&l>&r " + ChatColor.translateAlternateColorCodes('&', fullMessage);

        plugin.broadcastMessage(formattedMessage);
        plugin.broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        plugin.broadcastSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
        plugin.sendMessage(sender, "&eBroadcasted message to all players!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("<message>");
        }
        return completions;
    }
}