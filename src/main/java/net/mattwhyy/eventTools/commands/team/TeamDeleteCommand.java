package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class TeamDeleteCommand extends BaseCommand {
    public TeamDeleteCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /team delete <name>");
            return true;
        }

        if (plugin.teamManager.deleteTeam(args[1])) {
            String teamName = args[1].substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            plugin.sendMessage(sender, "&eDeleted team " + team.getColor() + args[1]);

            if (plugin.teamManager.getTeamNames().size() == 1) {
                String lastTeamName = plugin.teamManager.getTeamNames().get(0);
                Team oldTeam = plugin.teamManager.getTeam(lastTeamName);
                if (plugin.teamManager.deleteTeam(lastTeamName)) {
                    plugin.sendMessage(sender, "&aAutomatically deleted last remaining team: &e" + oldTeam.getColor() + lastTeamName);
                }
            }
        } else {
            plugin.sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.teamManager.getTeamNames() : new ArrayList<>();
    }
}