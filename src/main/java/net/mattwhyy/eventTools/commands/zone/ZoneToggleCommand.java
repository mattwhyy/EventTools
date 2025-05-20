package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.zones.EventZone;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ZoneToggleCommand extends BaseCommand {
    public ZoneToggleCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /zone toggle <name>");
            return true;
        }

        EventZone zone = plugin.zoneManager.getZone(args[1]);
        if (zone == null) {
            plugin.sendMessage(sender, "&cZone not found!");
            return true;
        }

        zone.setActive(!zone.isActive());
        plugin.sendMessage(sender, String.format("&eZone '%s' is now %s",
                zone.getName(),
                zone.isActive() ? "&aACTIVE" : "&cINACTIVE"
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.zoneManager.getZoneNames() : new ArrayList<>();
    }
}