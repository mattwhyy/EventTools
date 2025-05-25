package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TeamAssignCommand extends BaseCommand {
    public TeamAssignCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length != 3) {
            plugin.sendMessage(sender, "&cUsage: /team assign <player|all|alive|eliminated|@team> <team>");
            return true;
        }

        String targetArg = args[1];
        String teamName = args[2];
        String cleanTeamName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', teamName));
        Team team = plugin.teamManager.getTeam(cleanTeamName);

        if (team == null) {
            plugin.sendMessage(sender, "&cTeam not found!");
            return true;
        }

        int successCount = 0;

        if (targetArg.equalsIgnoreCase("all")) {
            successCount = assignAllPlayers(team);
            plugin.sendMessage(sender, "&eAssigned " + successCount + " players to " + team.getColor() + team.getName());
            return true;
        } else if (targetArg.equalsIgnoreCase("alive")) {
            successCount = assignAlivePlayers(team);
            plugin.sendMessage(sender, "&eAssigned " + successCount + " alive players to " + team.getColor() + team.getName());
            return true;
        } else if (targetArg.equalsIgnoreCase("eliminated")) {
            successCount = assignEliminatedPlayers(team);
            plugin.sendMessage(sender, "&eAssigned " + successCount + " eliminated players to " + team.getColor() + team.getName());
            return true;
        } else if (targetArg.startsWith("@")) {
            String sourceTeamName = targetArg.substring(1);
            Team sourceTeam = plugin.teamManager.getTeam(sourceTeamName);
            if (sourceTeam == null) {
                plugin.sendMessage(sender, "&cSource team not found!");
                return true;
            }
            successCount = assignTeamPlayers(sourceTeam, team);
            plugin.sendMessage(sender, "&eAssigned " + successCount + " players from " +
                    sourceTeam.getColor() + sourceTeam.getName() +
                    " &eto " + team.getColor() + team.getName());
            return true;
        }

        Player target = plugin.getServer().getPlayer(targetArg);
        if (target == null) {
            plugin.sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        if (target.hasPermission("eventtools.bypass")) {
            plugin.sendMessage(sender, "&cYou can't assign that player to teams!");
            return true;
        }

        if (plugin.teamManager.addToTeam(target, teamName)) {
            plugin.sendMessage(sender, "&eAssigned " + target.getName() + " to " + team.getColor() + team.getName());
            plugin.sendMessage(target, "&aYou've been assigned to team " + team.getColor() + team.getName());
            successCount = 1;
        } else {
            plugin.sendMessage(sender, "&cFailed to assign player to team!");
        }

        return true;
    }

    private int assignAllPlayers(Team team) {
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("eventtools.bypass") && plugin.teamManager.addToTeam(player, team.getName())) {
                plugin.sendMessage(player, "&aYou've been assigned to team " + team.getColor() + team.getName());
                count++;
            }
        }
        return count;
    }

    private int assignAlivePlayers(Team team) {
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("eventtools.bypass") &&
                    !plugin.isEliminated(player) &&
                    plugin.teamManager.addToTeam(player, team.getName())) {
                plugin.sendMessage(player, "&aYou've been assigned to team " + team.getColor() + team.getName());
                count++;
            }
        }
        return count;
    }

    private int assignEliminatedPlayers(Team team) {
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("eventtools.bypass") &&
                    plugin.isEliminated(player) &&
                    plugin.teamManager.addToTeam(player, team.getName())) {
                plugin.sendMessage(player, "&aYou've been assigned to team " + team.getColor() + team.getName());
                count++;
            }
        }
        return count;
    }

    private int assignTeamPlayers(Team sourceTeam, Team targetTeam) {
        int count = 0;
        for (UUID playerId : sourceTeam.getMembers()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && !player.hasPermission("eventtools.bypass")) {
                if (plugin.teamManager.addToTeam(player, targetTeam.getName())) {
                    plugin.sendMessage(player, "&aYou've been moved to team " + targetTeam.getColor() + targetTeam.getName());
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        } else if (args.length == 2) {
            completions.addAll(plugin.teamManager.getTeamNames());
        }
        return completions;
    }
}