package net.mattwhyy.eventTools.commands.event;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StartEventCommand extends BaseCommand {
    public StartEventCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (plugin.eventActive) {
            plugin.sendMessage(sender, "&cEvent is already running!");
            return true;
        }

        List<Player> players = plugin.getNonBypassPlayers();
        if (players.size() < 2) {
            plugin.sendMessage(sender, "&cYou need at least &e2 &cplayers to start an event!");
            return true;
        }

        plugin.eliminatedPlayers.clear();
        plugin.eliminationOrder.clear();
        plugin.eventStartTime = System.currentTimeMillis();
        plugin.votes.clear();

        plugin.eventTitle = args.length > 0 ? String.join(" ", args) : "Event";

        if (!plugin.teamManager.getTeamNames().isEmpty()) {
            List<Team> activeTeams = plugin.teamManager.getActiveTeams();

            if (activeTeams.isEmpty()) {
                List<String> teamNames = new ArrayList<>(plugin.teamManager.getTeamNames());
                teamNames.forEach(plugin.teamManager::deleteTeam);
                plugin.broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
            }
            else if (activeTeams.size() == 1) {
                Team team = activeTeams.get(0);
                plugin.broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
                plugin.broadcastMessage(team.getColor() + team.getName() +
                        " &7are playing as a group against unassigned players!");

                plugin.eventType = EventTools.EventType.HYBRID_FFA;
            }
            else {
                List<Player> unassignedPlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .filter(p -> !plugin.teamManager.getPlayerTeam(p).isPresent())
                        .collect(Collectors.toList());

                if (!unassignedPlayers.isEmpty()) {
                    plugin.teamManager.balanceTeams();
                    plugin.broadcastMessage("&aBalanced &e" + unassignedPlayers.size() + " &aunassigned players!");
                }

                plugin.broadcastMessage("&6&lEVENT STARTED! &e" + activeTeams.size() + " teams competing!");
                plugin.eventType = EventTools.EventType.TEAM_BATTLE;
            }
        } else {
            plugin.broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
            plugin.eventType = EventTools.EventType.PURE_FFA;
        }

        plugin.eventActive = true;
        plugin.chatMuted = false;
        plugin.numberGuessActive = false;
        plugin.broadcastSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 1);

        plugin.broadcastTitle(
                plugin.getConfig().getString("messages.event-start-title", "§6Event started!"),
                plugin.getConfig().getString("messages.event-start-subtitle", "§eGood luck!")
        );
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && plugin.eventTitle != null && !plugin.eventTitle.equals("Event")) {
            completions.add(plugin.eventTitle);
        }
        completions.add("<title>");
        return completions;
    }
}