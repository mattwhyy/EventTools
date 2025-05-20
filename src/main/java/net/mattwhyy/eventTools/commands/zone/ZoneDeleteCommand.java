package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ZoneDeleteCommand extends BaseCommand {
    public ZoneDeleteCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 2) {
            plugin.sendMessage(sender, "&cUsage: /zone delete <name>");
            return true;
        }

        if (plugin.zoneManager.removeZone(args[1])) {
            plugin.sendMessage(sender, "&eDeleted zone '" + args[1] + "'");
        } else {
            plugin.sendMessage(sender, "&cZone not found!");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? plugin.zoneManager.getZoneNames() : new ArrayList<>();
    }
}