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

public class TeamLeaveCommand extends BaseCommand {
    public TeamLeaveCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;

        if (plugin.eventActive) {
            plugin.sendMessage(sender, "&cYou cannot leave teams during an active event!");
            return true;
        }

        Player player = (Player) sender;
        if (player.hasPermission("eventtools.bypass")) {
            plugin.sendMessage(sender, "&cYou cannot leave teams!");
            return true;
        }

        Optional<Team> currentTeam = plugin.teamManager.getPlayerTeam(player);
        if (!currentTeam.isPresent()) {
            plugin.sendMessage(sender, "&cYou are not in any team!");
            return true;
        }

        Team team = currentTeam.get();
        team.removeMember(player);
        plugin.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
        plugin.sendMessage(sender, "&aYou have left team " + team.getColor() + team.getName());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}