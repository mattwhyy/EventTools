package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListCommand extends BaseCommand {
    public ListCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /list <alive|eliminated|all>");
            return true;
        }

        StringBuilder list = new StringBuilder();
        switch (args[0].toLowerCase()) {
            case "alive":
                list.append("&aAlive Players:\n");
                plugin.getNonBypassPlayers().stream()
                        .filter(p -> !plugin.isEliminated(p))
                        .forEach(p -> list.append("&8-&r ").append(p.getName()).append("\n"));
                break;
            case "eliminated":
                list.append("&cEliminated Players:\n");
                plugin.getNonBypassPlayers().stream()
                        .filter(plugin::isEliminated)
                        .forEach(p -> list.append("&8-&r ").append(p.getName()).append("\n"));
                break;
            case "all":
                list.append("&6All Players:\n");
                plugin.getNonBypassPlayers().forEach(p -> {
                    if (plugin.isEliminated(p)) {
                        list.append("&c☠ ").append(p.getName()).append("\n");
                    } else {
                        list.append("&a❤ ").append(p.getName()).append("\n");
                    }
                });
                break;
            default:
                plugin.sendMessage(sender, "&cUsage: /list <alive|eliminated|all>");
                return true;
        }
        plugin.sendMessage(sender, list.toString());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("alive", "eliminated", "all"));
        }
        return completions;
    }
}