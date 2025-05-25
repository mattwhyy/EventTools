package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.zones.EventZone;
import net.mattwhyy.eventTools.zones.Shape;
import net.mattwhyy.eventTools.zones.ZoneType;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZoneCreateCommand extends BaseCommand {
    public ZoneCreateCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;
        if (args.length < 5) {
            plugin.sendMessage(sender, "&cUsage: /zone create <name> <circle|square> <radius> <type> [args]");
            plugin.sendMessage(sender, "&7Types: effect(<effect:level>,<effect2:level>), damage(<amount>), safe, must_stay, team_only(<team>)");
            return true;
        }

        try {
            String name = args[1];
            Shape shape = Shape.valueOf(args[2].toUpperCase());
            int radius = Math.min(Integer.parseInt(args[3]), 100);
            ZoneType type = ZoneType.valueOf(args[4].toUpperCase());

            List<PotionEffect> effects = new ArrayList<>();
            double damage = 0;
            String teamName = null;

            switch (type) {
                case EFFECT:
                    if (args.length < 6) {
                        plugin.sendMessage(sender, "&cEffect zones require effects! Example: speed:1,jump_boost:2");
                        return true;
                    }
                    for (String effectStr : args[5].split(",")) {
                        String[] parts = effectStr.split(":");
                        PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                        int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        effects.add(new PotionEffect(effectType, Integer.MAX_VALUE, amplifier));
                    }
                    break;

                case DAMAGE:
                    if (args.length < 6) {
                        plugin.sendMessage(sender, "&cDamage zones require damage amount! Example: damage 2.5");
                        return true;
                    }
                    damage = Double.parseDouble(args[5]);
                    break;

                case TEAM_ONLY:
                    if (args.length < 6) {
                        plugin.sendMessage(sender, "&cTeam zones require team name! Example: team_only red");
                        return true;
                    }
                    teamName = args[5];
                    if (plugin.teamManager.getTeam(teamName) == null) {
                        plugin.sendMessage(sender, "&cTeam '" + teamName + "' doesn't exist!");
                        return true;
                    }
                    break;

                default:
                    if (args.length > 5) {
                        plugin.sendMessage(sender, "&cOnly effect, damage, and team_only zones need extra arguments!");
                        return true;
                    }
            }

            EventZone zone = new EventZone(name, ((Player) sender).getLocation(), shape, radius,
                    type, effects, damage, plugin.teamManager);

            if (type == ZoneType.TEAM_ONLY) {
                zone.setAllowedTeam(teamName);
            }

            plugin.zoneManager.addZone(zone);

            String extraInfo = "";
            if (type == ZoneType.TEAM_ONLY) {
                extraInfo = " &7(Team: " + teamName + ")";
            } else if (type == ZoneType.DAMAGE) {
                extraInfo = " &7(Damage: " + damage + " hearts/sec)";
            } else if (type == ZoneType.EFFECT) {
                extraInfo = " &7(Effects: " + args[5] + ")";
            }

            plugin.sendMessage(sender, String.format(
                    "&eCreated %s zone '%s' &8(Radius: %d)%s",
                    type.name().toLowerCase(), name, radius, extraInfo
            ));
            return true;

        } catch (IllegalArgumentException e) {
            plugin.sendMessage(sender, "&cInvalid arguments! Error: " + e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.add("<name>");
        } else if (args.length == 3) {
            completions.addAll(Arrays.asList("circle", "square"));
        } else if (args.length == 4) {
            completions.add("<radius>");
        } else if (args.length == 5) {
            Arrays.stream(ZoneType.values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .forEach(completions::add);
        } else if (args.length == 6) {
            if (args[4].equalsIgnoreCase("effect")) {
                Arrays.stream(PotionEffectType.values())
                        .map(effect -> effect.getName().toLowerCase())
                        .forEach(completions::add);
            } else if (args[4].equalsIgnoreCase("team_only")) {
                completions.addAll(plugin.teamManager.getTeamNames());
            } else if (args[4].equalsIgnoreCase("damage")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            }
        }
        return completions;
    }
}