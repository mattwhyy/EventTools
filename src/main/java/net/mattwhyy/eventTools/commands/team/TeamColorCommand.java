package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.teams.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TeamColorCommand extends BaseCommand {
    public TeamColorCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 3) {
            plugin.sendMessage(sender, "&cUsage: /team color <team> <newColor>");
            return true;
        }

        Team team = plugin.teamManager.getTeam(args[1]);
        if (team == null) {
            plugin.sendMessage(sender, "&cTeam not found!");
            return true;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());
            team.setColor(color);
            plugin.sendMessage(sender, "&eTeam color updated!");
        } catch (IllegalArgumentException e) {
            plugin.sendMessage(sender, "&cInvalid color! Use: " + getColorList());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.addAll(plugin.teamManager.getTeamNames());
        } else if (args.length == 3) {
            Arrays.stream(ChatColor.values())
                    .filter(ChatColor::isColor)
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