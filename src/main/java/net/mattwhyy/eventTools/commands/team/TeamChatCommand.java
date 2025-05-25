package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class TeamChatCommand extends BaseCommand {
    public TeamChatCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cOnly players can use team chat!");
            return true;
        }

        Player player = (Player) sender;
        Optional<Team> team = plugin.teamManager.getPlayerTeam(player);

        if (team.isEmpty()) {
            plugin.sendMessage(player, "&cYou're not in a team!");
            return true;
        }

        team.get().toggleTeamChat(player.getUniqueId());
        boolean nowEnabled = team.get().hasTeamChatToggled(player.getUniqueId());

        plugin.sendMessage(player, "&aTeam chat " + (nowEnabled ? "&aenabled" : "&cdisabled"));
        return true;
    }
}