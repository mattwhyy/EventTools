package net.mattwhyy.eventTools.commands.inventory;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GiveItemCommand extends BaseCommand {
    public GiveItemCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;
        if (args.length < 1) {
            plugin.sendMessage(sender, "&cUsage: /giveitem <player|all|alive|eliminated|@team> [amount]");
            return true;
        }

        Player givingPlayer = (Player) sender;
        ItemStack item = givingPlayer.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            plugin.sendMessage(sender, "&cYou must be holding an item!");
            return true;
        }

        List<Player> targets = getFilteredTargets(sender, args[0]);
        if (targets.isEmpty()) {
            handleNoTargets(sender, args[0]);
            return true;
        }

        int amount = args.length >= 2 ? parseInt(args[1], 1) : 1;
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);

        targets.forEach(target -> {
            target.getInventory().addItem(toGive.clone());
            plugin.sendMessage(target, "&aYou received an item from " + sender.getName());
        });

        String targetName = formatTargetName(args[0], targets.size());
        plugin.sendMessage(sender, String.format("&eGave item to %s!", targetName));
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
            plugin.sendMessage(sender, "&cYou can't give items to yourself!");
        } else if (getTargetPlayers(sender, target).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
            plugin.sendMessage(sender, "&cYou can't give items to that player!");
        } else {
            plugin.sendMessage(sender, "&cNo matching players found!");
        }
    }

    private String formatTargetName(String arg, int count) {
        if (arg.startsWith("@")) {
            String teamName = arg.substring(1);
            Team team = plugin.teamManager.getTeam(teamName);
            if (team != null) {
                return "team " + team.getColor() + teamName + "&r";
            }
            return "team " + teamName;
        }
        return arg.matches("all|alive|eliminated") ? count + " players" : arg;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("all", "alive", "eliminated"));
            completions.addAll(plugin.getOnlinePlayerNames());
            completions.addAll(plugin.teamManager.getTeamNames().stream().map(name -> "@" + name).toList());
        } else if (args.length == 2) {
            completions.add("<amount>");
        }
        return completions;
    }
}