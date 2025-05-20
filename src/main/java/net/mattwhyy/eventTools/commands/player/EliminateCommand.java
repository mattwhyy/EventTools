package net.mattwhyy.eventTools.commands.player;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EliminateCommand extends BaseCommand {
    public EliminateCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requireEventActive(sender)) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /eliminate <player|all|@team>");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            int count = plugin.eliminateAllPlayers();
            plugin.sendMessage(sender, count > 0 ? "&aEliminated " + count + " players!" : "&cAll players are already eliminated!");
            return true;
        }

        if (args[0].startsWith("@")) {
            String teamName = args[0].substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            int count = plugin.eliminateTeam(teamName);
            if (count == 0) {
                plugin.sendMessage(sender, "&cAll members of team " + team.getColor() + teamName + " are already eliminated!");
            } else {
                plugin.sendMessage(sender, "&eEliminated " + count + " members of team " + team.getColor() + teamName);
            }
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            plugin.sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        plugin.handleElimination(target);
        plugin.checkForEventEnd();
        return true;
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