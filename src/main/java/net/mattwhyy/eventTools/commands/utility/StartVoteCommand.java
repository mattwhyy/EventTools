package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class StartVoteCommand extends BaseCommand {
    public StartVoteCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (plugin.voteInProgress) {
            plugin.sendMessage(sender, "&cA vote is already in progress!");
            return true;
        }

        if (args.length < 1) {
            plugin.sendMessage(sender, "&cUsage: /startvote <question>");
            return true;
        }

        plugin.currentVoteQuestion = String.join(" ", args);
        plugin.votes.clear();
        plugin.voteInProgress = true;
        plugin.voteTimeRemaining = 30;

        plugin.broadcastMessage("&6&lVOTE STARTED: &e" + plugin.currentVoteQuestion);
        plugin.broadcastMessage("&eType &aYES &eor &cNO &ein chat to vote!");
        plugin.broadcastMessage("&7Vote ends in &e30 &7seconds!");

        plugin.voteTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.voteTimeRemaining--;

                if (plugin.voteTimeRemaining == 15 || plugin.voteTimeRemaining == 5) {
                    plugin.broadcastMessage("&e" + plugin.voteTimeRemaining + " &7seconds remaining to vote!");
                }

                if (plugin.voteTimeRemaining <= 0) {
                    plugin.endVote();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length >= 1) {
            completions.add("<question>");
        }
        return completions;
    }
}