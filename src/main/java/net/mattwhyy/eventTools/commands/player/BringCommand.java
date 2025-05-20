package net.mattwhyy.eventTools.commands.player;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BringCommand extends BaseCommand {
    public BringCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /bring <player|all|alive|eliminated|@team>");
            return true;
        }

        Player senderPlayer = (Player) sender;
        List<Player> targets = getTargetPlayers(sender, args[0]);
        targets.removeIf(p -> p.equals(senderPlayer) || p.hasPermission("eventtools.bypass"));

        if (targets.isEmpty()) {
            handleNoTargets(sender, args[0]);
            return true;
        }

        targets.forEach(target -> {
            plugin.safeTeleport(target, senderPlayer.getLocation());
            plugin.sendMessage(target, "&aYou were brought to " + sender.getName());
        });

        String targetName = formatTargetName(args[0], targets.size());
        plugin.sendMessage(sender, String.format("&eBrought %s to you!", targetName));
        return true;
    }

    private void handleNoTargets(CommandSender sender, String target) {
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.sendMessage(sender, "&cYou can't bring yourself!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't bring that player!");
        } else {
            plugin.sendMessage(sender, "&cNo matching players found!");
        }
    }

    private String formatTargetName(String arg, int count) {
        if (arg.startsWith("@")) {
            String teamName = arg.substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            if (team != null) {
                return "team " + team.getColor() + teamName + "&r";
            }
            return "team " + teamName;
        }
        return arg.matches("all|alive|eliminated") ? count + " players" : arg;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        }
        return completions;
    }
}