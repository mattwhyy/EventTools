package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import net.mattwhyy.eventTools.zones.EventZone;
import net.mattwhyy.eventTools.zones.ZoneType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ZoneInfoCommand extends BaseCommand {
    public ZoneInfoCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;

        List<String> zones = plugin.zoneManager.getZoneNames();
        if (zones.isEmpty()) {
            plugin.sendMessage(sender, "&7No zones available. Use &e/zone create &7to create some!");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Zones:\n");
        for (String zoneName : zones) {
            EventZone zone = plugin.zoneManager.getZone(zoneName);

            message.append("&e")
                    .append(zone.getName())
                    .append(" &7(")
                    .append(zone.getType());

            switch (zone.getType()) {
                case TEAM_ONLY:
                    if (zone.getAllowedTeam() != null) {
                        Team team = plugin.teamManager.getTeam(zone.getAllowedTeam());
                        ChatColor teamColor = team != null ? team.getColor() : ChatColor.WHITE;
                        message.append(" - ").append(teamColor).append(zone.getAllowedTeam());
                    }
                    break;
                case DAMAGE:
                    message.append(" - &c").append(zone.getDamage()).append(" damage/s");
                    break;
                case EFFECT:
                    if (!zone.getEffects().isEmpty()) {
                        message.append(" - &b");
                        message.append(zone.getEffects().stream()
                                .map(effect -> {
                                    String effectName = effect.getType().getName();
                                    int amplifier = effect.getAmplifier() + 1;
                                    return effectName + " " + amplifier + " (" + (effect.getDuration()/20) + "s)";
                                })
                                .collect(Collectors.joining("&7, ")));
                    }
                    break;
            }

            message.append("&7)");

            message.append("\n&8&l>&r &7Status: ")
                    .append(zone.isActive() ? "&aActive" : "&cInactive")
                    .append("&7, Radius: ")
                    .append(zone.getRadius())
                    .append("&7, Shape: ")
                    .append(zone.getShape());

            Location center = zone.getCenter();
            message.append("\n&8&l>&r &7Location: ")
                    .append(center.getWorld().getName())
                    .append(" &8(")
                    .append(center.getBlockX())
                    .append(", ")
                    .append(center.getBlockY())
                    .append(", ")
                    .append(center.getBlockZ())
                    .append(")");

            Set<Player> playersInZone = zone.getPlayersInside();
            message.append("\n&8&l>&r &7Players: ");
            if (playersInZone.isEmpty()) {
                message.append("&7None");
            } else {
                message.append(playersInZone.stream()
                        .map(Player::getName)
                        .collect(Collectors.joining("&7, ")));
            }

            message.append("\n");
        }

        plugin.sendMessage(sender, message.toString());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}