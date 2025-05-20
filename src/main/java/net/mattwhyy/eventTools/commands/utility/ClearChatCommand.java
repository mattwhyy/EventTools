package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ClearChatCommand extends BaseCommand {
    public ClearChatCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        for (int i = 0; i < 100; i++) {
            plugin.broadcastMessage("");
        }
        plugin.broadcastMessage("&8Chat has been cleared by " + sender.getName());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}