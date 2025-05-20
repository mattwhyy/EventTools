package net.mattwhyy.eventTools.commands.player;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimedEffectCommand extends BaseCommand {
    public TimedEffectCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 3) {
            plugin.sendMessage(sender, "&cUsage: /timedeffect <effect> <duration> [amplifier] <player|all|alive|eliminated|@team>");
            return true;
        }

        try {
            PotionEffectType type = PotionEffectType.getByName(args[0].toUpperCase());
            if (type == null) {
                plugin.sendMessage(sender, "&cInvalid effect type!");
                return true;
            }

            int durationArgPos = 1;
            int amplifierArgPos = 2;
            int targetArgPos = 2;

            if (args.length >= 4) {
                amplifierArgPos = 2;
                targetArgPos = 3;
            }

            int duration = parseInt(args[durationArgPos], 1) * 20;
            int amplifier = args.length >= 4 ? parseInt(args[amplifierArgPos], 0) : 0;

            List<Player> targets = getFilteredTargets(sender, args[targetArgPos]);
            if (targets.isEmpty()) {
                handleNoTargets(sender, args[targetArgPos]);
                return true;
            }

            targets.forEach(player -> {
                player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                plugin.sendMessage(player, String.format(
                        "&aYou received %s %s for %s seconds!",
                        amplifier > 0 ? "level " + (amplifier + 1) : "",
                        type.getName().toLowerCase().replace("_", " "),
                        duration / 20
                ));
            });

            String targetName = formatTargetName(args[targetArgPos], targets.size());
            plugin.sendMessage(sender, String.format(
                    "&eApplied %s (level %d) to %s for %d seconds!",
                    type.getName(),
                    amplifier + 1,
                    targetName,
                    duration / 20
            ));
        } catch (Exception e) {
            plugin.sendMessage(sender, "&cInvalid effect, duration or amplifier!");
        }
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
            plugin.sendMessage(sender, "&cYou can't apply effects to yourself!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't apply effects to that player!");
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
            Arrays.stream(PotionEffectType.values())
                    .map(e -> e.getName().toLowerCase())
                    .forEach(completions::add);
        } else if (args.length == 2) {
            completions.add("<duration>");
        } else if (args.length == 3) {
            completions.add("<amplifier>");
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        } else if (args.length == 4) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        }
        return completions;
    }
}