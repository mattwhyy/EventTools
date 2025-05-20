package net.mattwhyy.eventTools.commands.event;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SetEventSpawnCommand extends BaseCommand {
    public SetEventSpawnCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;

        plugin.spawnLocation = ((Player) sender).getLocation();
        plugin.sendMessage(sender, "&eSpawn set at your location!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}