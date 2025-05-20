package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TeamAssignCommand extends BaseCommand {
    public TeamAssignCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 3) {
            plugin.sendMessage(sender, "&cUsage: /team assign <player> <team>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        if (target.hasPermission("eventtools.bypass")) {
            plugin.sendMessage(sender, "&cYou can't assign that player to teams!");
            return true;
        }

        if (plugin.teamManager.addToTeam(target, args[2])) {
            String teamName = args[2].substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            plugin.sendMessage(sender, "&eAssigned " + target.getName() + " to " + team.getColor() + args[2]);
            plugin.sendMessage(target, "&aYou've been assigned to team " + team.getColor() + args[2]);
        } else {
            plugin.sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.addAll(plugin.getOnlinePlayerNames());
        } else if (args.length == 3) {
            completions.addAll(plugin.teamManager.getTeamNames());
        }
        return completions;
    }
}