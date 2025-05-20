package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TeamCommand extends BaseCommand {
    public TeamCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendTeamHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return new TeamCreateCommand(plugin).execute(sender, args);
            case "delete":
                return new TeamDeleteCommand(plugin).execute(sender, args);
            case "assign":
                return new TeamAssignCommand(plugin).execute(sender, args);
            case "join":
                return new TeamJoinCommand(plugin).execute(sender, args);
            case "leave":
                return new TeamLeaveCommand(plugin).execute(sender, args);
            case "color":
                return new TeamColorCommand(plugin).execute(sender, args);
            case "info":
                return new TeamInfoCommand(plugin).execute(sender, args);
            case "settings":
                return new TeamSettingsCommand(plugin).execute(sender, args);
            default:
                sendTeamHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("join");
            commands.add("leave");

            if (sender.hasPermission("eventtools.admin")) {
                commands.addAll(Arrays.asList("create", "delete", "assign", "color", "info", "settings"));
            }

            return filterCompletions(commands, args[0]);
        }
        else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "join":
                    return filterCompletions(plugin.teamManager.getTeamNames(), args[1]);
                case "leave":
                    if (sender instanceof Player) {
                        return filterCompletions(
                                plugin.teamManager.getPlayerTeam((Player)sender)
                                        .map(team -> Collections.singletonList(team.getName()))
                                        .orElse(Collections.emptyList()),
                                args[1]
                        );
                    }
                    break;
                case "create":
                    if (sender.hasPermission("eventtools.admin")) return filterCompletions(Collections.singletonList("<name>"), args[1]);
                    break;
                case "delete":
                case "color":
                    if (sender.hasPermission("eventtools.admin")) return filterCompletions(plugin.teamManager.getTeamNames(), args[1]);
                    break;
                case "assign":
                    if (sender.hasPermission("eventtools.admin")) return filterCompletions(plugin.getOnlinePlayerNames(), args[1]);
                    break;
                case "settings":
                    if (sender.hasPermission("eventtools.admin")) {
                        List<String> options = new ArrayList<>(plugin.teamManager.getTeamNames());
                        options.add("all");
                        return filterCompletions(options, args[1]);
                    }
                    break;
            }
        }
        else if (args.length == 3 && sender.hasPermission("eventtools.admin")) {
            switch (args[0].toLowerCase()) {
                case "create":
                case "color":
                    return filterCompletions(
                            Arrays.stream(ChatColor.values())
                                    .filter(ChatColor::isColor)
                                    .map(color -> color.name().toLowerCase())
                                    .collect(Collectors.toList()),
                            args[2]
                    );
                case "assign":
                    return filterCompletions(plugin.teamManager.getTeamNames(), args[2]);
                case "settings":
                    return filterCompletions(Arrays.asList("friendlyfire", "nametags", "collision"), args[2]);
            }
        }
        else if (args.length == 4 && sender.hasPermission("eventtools.admin") && args[0].equalsIgnoreCase("settings")) {
            return filterCompletions(Arrays.asList("true", "false"), args[3]);
        }
        return completions;
    }

    private void sendTeamHelp(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {
            plugin.sendMessage(sender, "&6Team Commands:\n" +
                    "&e/team join <team> &7- Join a team\n" +
                    "&e/team leave &7- Leave a team\n");
            return;
        }
        plugin.sendMessage(sender, "&6Team Commands:\n" +
                "&e/team create <name> <color> &7- Create a new team\n" +
                "&e/team delete <name> &7- Delete a team\n" +
                "&e/team assign <player> <team> &7- Assign a player to a team\n" +
                "&e/team color <name> <color> &7- Change team color\n" +
                "&e/team info &7- Show detailed team info\n" +
                "&e/team settings &7- Modify settings of teams\n");
    }

    private List<String> filterCompletions(List<String> completions, String currentArg) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg.toLowerCase()))
                .collect(Collectors.toList());
    }
}