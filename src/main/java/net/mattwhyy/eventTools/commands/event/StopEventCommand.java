package net.mattwhyy.eventTools.commands.event;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class StopEventCommand extends BaseCommand {
    public StopEventCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!plugin.eventActive) {
            plugin.sendMessage(sender, "&cNo event is currently running!");
            return true;
        }

        plugin.broadcastTitle(
                plugin.getConfig().getString("messages.event-end-title", "§aEvent ended!"),
                plugin.getConfig().getString("messages.event-end-subtitle", "§7Thanks for playing!")
        );
        plugin.resetEvent();
        plugin.eventStartTime = 0;
        plugin.broadcastMessage("&a&lEVENT ENDED!");

        if (plugin.getDiscordManager().isEnabled()) {
            String hex = plugin.getConfig().getString("discord.colors.event-end", "#FF0000");
            plugin.getDiscordManager().broadcastAnnouncement(plugin.eventTitle, plugin.getConfig().getString(
                    "discord.messages.event-end-desc", "The event has ended!"), Color.decode(hex)
            );
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}