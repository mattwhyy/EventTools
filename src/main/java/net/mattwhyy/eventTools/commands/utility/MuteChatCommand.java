package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class MuteChatCommand extends BaseCommand {
    public MuteChatCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        plugin.chatMuted = !plugin.chatMuted;
        plugin.broadcastMessage(plugin.chatMuted ? "&cChat has been muted!" : "&aChat has been unmuted!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}