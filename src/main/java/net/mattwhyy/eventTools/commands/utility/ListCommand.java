package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

        List<Player> relevantPlayers = getRelevantPlayers(args[0].toLowerCase());

        if (relevantPlayers.isEmpty()) {
            sendEmptyMessage(sender, args[0].toLowerCase());
            return true;
        }

        StringBuilder list = new StringBuilder();
        switch (args[0].toLowerCase()) {
            case "alive":
                list.append("&aAlive Players (").append(relevantPlayers.size()).append("):\n");
                relevantPlayers.forEach(p -> list.append("&8-&r ").append(p.getName()).append("\n"));
                break;
            case "eliminated":
                list.append("&cEliminated Players (").append(relevantPlayers.size()).append("):\n");
                relevantPlayers.forEach(p -> list.append("&8-&r ").append(p.getName()).append("\n"));
                break;
            case "all":
                list.append("&6All Players (").append(relevantPlayers.size()).append("):\n");
                relevantPlayers.forEach(p -> {
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

    private List<Player> getRelevantPlayers(String type) {
        List<Player> players = plugin.getNonBypassPlayers();

        switch (type) {
            case "alive":
                return players.stream()
                        .filter(p -> !plugin.isEliminated(p))
                        .collect(Collectors.toList());
            case "eliminated":
                return players.stream()
                        .filter(plugin::isEliminated)
                        .collect(Collectors.toList());
            case "all":
                return new ArrayList<>(players);
            default:
                return new ArrayList<>();
        }
    }

    private void sendEmptyMessage(CommandSender sender, String type) {
        switch (type) {
            case "alive":
                plugin.sendMessage(sender, "&cThere are currently no alive players.");
                break;
            case "eliminated":
                plugin.sendMessage(sender, "&cThere are currently no eliminated players.");
                break;
            case "all":
                plugin.sendMessage(sender, "&cThere are no players online.");
                break;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("alive", "eliminated", "all");
        }
        return new ArrayList<>();
    }
}