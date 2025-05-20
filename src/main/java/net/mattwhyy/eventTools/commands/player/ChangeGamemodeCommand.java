package net.mattwhyy.eventTools.commands.player;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangeGamemodeCommand extends BaseCommand {
    public ChangeGamemodeCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /changegamemode <mode> <player|all|alive|eliminated|@team>");
            plugin.sendMessage(sender, "&7Modes: survival, creative, adventure, spectator");
            return true;
        }

        GameMode mode;
        try {
            mode = GameMode.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.sendMessage(sender, "&cInvalid gamemode! Use: survival, creative, adventure, spectator");
            return true;
        }

        List<Player> targets = getFilteredTargets(sender, args[1]);
        if (targets.isEmpty()) {
            handleNoTargets(sender, args[1]);
            return true;
        }

        targets.forEach(player -> {
            player.setGameMode(mode);
            plugin.sendMessage(player, "&aYour gamemode was changed to " + mode.name().toLowerCase());
        });

        String targetName = formatTargetName(args[1], targets.size());
        plugin.sendMessage(sender, String.format("&eChanged gamemode of %s to %s", targetName, mode.name().toLowerCase()));
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

    private void handleNoTargets(CommandSender sender, String target) {
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.sendMessage(sender, "&cYou can't change your own gamemode!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't change that player's gamemode!");
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
            completions.addAll(Arrays.asList("survival", "creative", "adventure", "spectator"));
        } else if (args.length == 2) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        }
        return completions;
    }
}