package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.zones.EventZone;
import net.mattwhyy.eventTools.zones.ZoneType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ZoneMoveHereCommand extends BaseCommand {
    public ZoneMoveHereCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /zone movehere <name>");
            return true;
        }

        String zoneName = args[1];
        EventZone zone = plugin.zoneManager.getZone(zoneName);
        if (zone == null) {
            plugin.sendMessage(sender, "&cZone '" + zoneName + "' not found!");
            return true;
        }

        EventZone newZone = new EventZone(
                zone.getName(),
                ((Player) sender).getLocation(),
                zone.getShape(),
                zone.getRadius(),
                zone.getType(),
                zone.getEffects(),
                zone.getDamage(),
                plugin.teamManager
        );

        if (zone.getType() == ZoneType.TEAM_ONLY) {
            newZone.setAllowedTeam(zone.getAllowedTeam());
        }

        plugin.zoneManager.removeZone(zone.getName());
        plugin.zoneManager.addZone(newZone);

        plugin.sendMessage(sender, String.format(
                "&eMoved zone '%s' to your current location!",
                zoneName
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.zoneManager.getZoneNames() : new ArrayList<>();
    }
}