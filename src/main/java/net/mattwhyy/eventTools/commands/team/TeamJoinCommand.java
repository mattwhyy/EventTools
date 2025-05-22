package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TeamJoinCommand extends BaseCommand {
    public TeamJoinCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;

        if (plugin.eventActive) {
            plugin.sendMessage(sender, "&cYou cannot join teams during an active event!");
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /team join <team>");
            return true;
        }

        Player player = (Player) sender;
        if (player.hasPermission("eventtools.bypass")) {
            plugin.sendMessage(sender, "&cYou cannot join teams!");
            return true;
        }

        Team team = plugin.teamManager.getTeam(args[1]);
        if (team == null) {
            plugin.sendMessage(sender, "&cTeam not found!");
            return true;
        }

        Optional<Team> currentTeam = plugin.teamManager.getPlayerTeam(player);
        if (currentTeam.isPresent()) {
            Team oldTeam = currentTeam.get();
            oldTeam.removeMember(player);
        }

        if (plugin.teamManager.addToTeam(player, args[1])) {
            plugin.sendMessage(sender, "&aYou joined team " + team.getColor() + team.getName());
            plugin.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
            return true;
        }

        plugin.sendMessage(sender, "&cCould not join team!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.teamManager.getTeamNames() : new ArrayList<>();
    }
}