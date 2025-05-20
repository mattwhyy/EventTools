package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class CountdownCommand extends BaseCommand {
    public CountdownCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /countdown <seconds>");
            return true;
        }

        try {
            int seconds = parseInt(args[0], 5);
            new BukkitRunnable() {
                int timeLeft = seconds;

                @Override
                public void run() {
                    String title = timeLeft <= 3 ? "§c" + timeLeft : "§e" + timeLeft;
                    plugin.broadcastTitle(title, "");

                    plugin.broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0);

                    if (timeLeft <= 0) {
                        plugin.broadcastTitle("&aGO!", "");
                        plugin.broadcastSound(Sound.ENTITY_PLAYER_LEVELUP, 1,1.5f);
                        cancel();
                        return;
                    }

                    timeLeft--;
                }
            }.runTaskTimer(plugin, 0, 20);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, "&cInvalid number! Use a whole number (e.g. 10)");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("<seconds>");
        }
        return completions;
    }
}