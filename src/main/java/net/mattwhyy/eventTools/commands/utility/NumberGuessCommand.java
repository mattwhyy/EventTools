package net.mattwhyy.eventTools.commands.utility;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NumberGuessCommand extends BaseCommand {
    public NumberGuessCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (plugin.numberGuessActive) {
            plugin.sendMessage(sender, "&cA number guess game is already active!");
            return true;
        }

        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /numberguess <maxNumber>");
            return true;
        }

        try {
            int max = parseInt(args[0], 100);
            plugin.targetNumber = new Random().nextInt(max) + 1;
            plugin.numberGuessActive = true;
            plugin.numberGuessWinner = null;

            plugin.broadcastMessage("&eGuess a number between &a1 &eand &a" + max + "&e!");
            plugin.broadcastMessage("&7First to type the correct number wins!");
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, "&cInvalid number!");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("<maxNumber>");
        }
        return completions;
    }
}