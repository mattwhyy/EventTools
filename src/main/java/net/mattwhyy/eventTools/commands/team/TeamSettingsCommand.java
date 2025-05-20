package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TeamSettingsCommand extends BaseCommand {
    public TeamSettingsCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 4) {
            plugin.sendMessage(sender, "&cUsage: /team settings <team|all> <property> <true|false>");
            plugin.sendMessage(sender, "&7Properties: friendlyfire, nametags, collision");
            return true;
        }
        if (!Arrays.asList("friendlyfire", "nametags", "collision").contains(args[2].toLowerCase())) {
            plugin.sendMessage(sender, "&cInvalid property! Use: friendlyfire, nametags, collision");
            return true;
        }

        String teamName = args[1];
        String property = args[2].toLowerCase();
        boolean value = args[3].equalsIgnoreCase("true");

        if (teamName.equalsIgnoreCase("all")) {
            plugin.teamManager.getAllTeams().forEach(team -> updateTeamSetting(team, property, value));
            plugin.sendMessage(sender, "&eUpdated all teams' " + property + " to " + (value ? "&atrue" : "&cfalse"));
        } else {
            Team team = plugin.teamManager.getTeam(teamName);
            if (team == null) {
                plugin.sendMessage(sender, "&cTeam not found!");
                return true;
            }
            updateTeamSetting(team, property, value);
            ChatColor teamColor = team.getColor();
            plugin.sendMessage(sender, "&eUpdated team " + teamColor + teamName +
                    "&e's " + property + " to " + (value ? "&atrue" : "&cfalse"));
        }
        return true;
    }

    private void updateTeamSetting(Team team, String property, boolean value) {
        switch (property) {
            case "friendlyfire": team.setFriendlyFire(value); break;
            case "nametags": team.setNameTagVisibility(value); break;
            case "collision": team.setCollisionEnabled(value); break;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.addAll(plugin.teamManager.getTeamNames());
            completions.add("all");
        } else if (args.length == 3) {
            completions.addAll(Arrays.asList("friendlyfire", "nametags", "collision"));
        } else if (args.length == 4) {
            completions.addAll(Arrays.asList("true", "false"));
        }
        return completions;
    }
}