package net.mattwhyy.eventTools.commands.inventory;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class InvSeeCommand extends BaseCommand {
    public InvSeeCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;
        if (args.length != 1) {
            plugin.sendMessage(sender, "&cUsage: /invsee <player>");
            return true;
        }

        Player admin = (Player) sender;
        List<Player> targets = getFilteredTargets(sender, args[0]);
        if (targets.isEmpty()) {
            handleNoTargets(sender, args[0]);
            return true;
        }

        Player target = targets.get(0);
        admin.openInventory(target.getInventory());
        plugin.sendMessage(sender, "&eViewing " + target.getName() + "'s inventory");
        return true;
    }

    private List<Player> getFilteredTargets(CommandSender sender, String target) {
        List<Player> targets = getTargetPlayers(sender, target);
        if (sender instanceof Player) {
            targets.removeIf(p -> p.equals(sender));
        }
        targets.removeIf(p -> p.hasPermission("eventtools.bypass"));
        return targets;
    }

    private void handleNoTargets(CommandSender sender, String target) {
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.sendMessage(sender, "&cYou can't view your own inventory!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't view that player's inventory!");
        } else {
            plugin.sendMessage(sender, "&cPlayer not found!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(plugin.getOnlinePlayerNames());
        }
        return completions;
    }
}