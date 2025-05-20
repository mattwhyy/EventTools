package net.mattwhyy.eventTools.commands.player;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FreezeCommand extends BaseCommand {
    public FreezeCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /freeze <player|all|alive|eliminated|@team>");
            return true;
        }

        List<Player> targets = getFilteredTargets(sender, args[0]);
        if (targets.isEmpty()) {
            handleNoTargets(sender, args[0]);
            return true;
        }

        boolean anyFrozen = targets.stream()
                .map(player -> toggleFreeze(player))
                .anyMatch(frozen -> frozen);

        String action = anyFrozen ? "Froze" : "Unfroze";
        String targetName = formatTargetName(args[0], targets.size());
        plugin.sendMessage(sender, String.format("&e%s %s!", action, targetName));
        return true;
    }

    private List<Player> getFilteredTargets(CommandSender sender, String target) {
        List<Player> targets = getTargetPlayers(sender, target);
        if (sender instanceof Player) {
            targets.removeIf(p -> p.equals(sender));
        }
        targets.removeIf(p -> p.hasPermission("eventtools.bypass"));
        return targets;
    }

    private boolean toggleFreeze(Player player) {
        boolean currentlyFrozen = player.getWalkSpeed() == 0;
        plugin.freezePlayer(player, !currentlyFrozen);
        return !currentlyFrozen;
    }

    private void handleNoTargets(CommandSender sender, String target) {
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.sendMessage(sender, "&cYou can't freeze yourself!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't freeze that player!");
        } else {
            plugin.sendMessage(sender, "&cNo matching players found!");
        }
    }

    private String formatTargetName(String arg, int count) {
        if (arg.startsWith("@")) {
            String teamName = arg.substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            if (team != null) {
                return "team " + team.getColor() + teamName + "&r";
            }
            return "team " + teamName;
        }
        return arg.matches("all|alive|eliminated") ? count + " players" : arg;
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