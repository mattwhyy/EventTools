package net.mattwhyy.eventTools.commands.inventory;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KitCommand extends BaseCommand {
    public KitCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /kit <kitName> <player|all|alive|eliminated|@team>");
            return true;
        }

        String kitName = args[0].toLowerCase();
        ConfigurationSection kitsSection = plugin.getConfig().getConfigurationSection("kits");

        if (kitsSection == null || !kitsSection.contains(kitName)) {
            plugin.sendMessage(sender, "&cKit '" + kitName + "' not found!");
            if (kitsSection != null) {
                plugin.sendMessage(sender, "&7Available kits: " + String.join(", ", kitsSection.getKeys(false)));
            }
            return true;
        }

        List<Player> targets = getFilteredTargets(sender, args[1]);
        if (targets.isEmpty()) {
            handleNoTargets(sender, args[1]);
            return true;
        }

        targets.forEach(player -> {
            plugin.giveKit(player, kitName);
            plugin.sendMessage(player, "&aYou received the " + kitName + " kit!");
        });

        String targetName = formatTargetName(args[1], targets.size());
        plugin.sendMessage(sender, String.format("&eGave %s kit to %s!", kitName, targetName));
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
            plugin.sendMessage(sender, "&cYou can't give a kit to yourself!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't give a kit to that player!");
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
            ConfigurationSection kitsSection = plugin.getConfig().getConfigurationSection("kits");
            if (kitsSection != null) {
                completions.addAll(kitsSection.getKeys(false));
            }
        } else if (args.length == 2) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        }
        return completions;
    }
}