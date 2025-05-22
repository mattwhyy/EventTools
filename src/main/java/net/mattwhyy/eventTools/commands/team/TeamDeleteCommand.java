package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.ChatColor;
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

        String cleanTeamName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', args[1]));
        Team team = plugin.teamManager.getTeam(cleanTeamName);

        if (team == null) {
            plugin.sendMessage(sender, "&cTeam not found!");
            return true;
        }

        ChatColor color = team.getColor();
        String name = team.getName();
        int remainingTeams = plugin.teamManager.getTeamNames().size();

        if (plugin.teamManager.deleteTeam(cleanTeamName)) {
            plugin.sendMessage(sender, "&eDeleted team " + color + name);

            if (remainingTeams == 1) {
                plugin.sendMessage(sender, "&aAll teams have been deleted");
            }
        } else {
            plugin.sendMessage(sender, "&cFailed to delete team!");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.teamManager.getTeamNames() : new ArrayList<>();
    }
}