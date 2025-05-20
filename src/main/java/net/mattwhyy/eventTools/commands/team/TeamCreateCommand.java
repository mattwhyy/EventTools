package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TeamCreateCommand extends BaseCommand {
    public TeamCreateCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 3) {
            plugin.sendMessage(sender, "&cUsage: /team create <name> <color>");
            plugin.sendMessage(sender, "&7Colors: " + getColorList());
            return true;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());

            if (!isColorCode(color)) {
                plugin.sendMessage(sender, "&c" + args[2] + " is not a valid color! Use: " + getColorList());
                return true;
            }

            if (plugin.teamManager.createTeam(args[1], color)) {
                plugin.sendMessage(sender, "&eCreated team " + color + args[1]);
            } else {
                plugin.sendMessage(sender, "&cMax teams reached (16) or team already exists!");
            }
        } catch (IllegalArgumentException e) {
            plugin.sendMessage(sender, "&cInvalid color! Use: " + getColorList());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.add("<name>");
        } else if (args.length == 3) {
            Arrays.stream(ChatColor.values())
                    .filter(this::isColorCode)
                    .map(color -> color.name().toLowerCase())
                    .forEach(completions::add);
        }
        return completions;
    }

    private boolean isColorCode(ChatColor color) {
        return color.isColor() && !color.isFormat();
    }

    private String getColorList() {
        return Arrays.stream(ChatColor.values())
                .filter(this::isColorCode)
                .map(color -> color + color.name().toLowerCase())
                .collect(Collectors.joining("&7, "));
    }
}