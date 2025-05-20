package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZoneCommand extends BaseCommand {
    public ZoneCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (!requirePlayer(sender)) return true;
        if (args.length < 1) {
            sendZoneHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return new ZoneCreateCommand(plugin).execute(sender, args);
            case "delete":
                return new ZoneDeleteCommand(plugin).execute(sender, args);
            case "info":
                return new ZoneInfoCommand(plugin).execute(sender, args);
            case "toggle":
                return new ZoneToggleCommand(plugin).execute(sender, args);
            case "resize":
                return new ZoneResizeCommand(plugin).execute(sender, args);
            case "movehere":
                return new ZoneMoveHereCommand(plugin).execute(sender, args);
            default:
                sendZoneHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "delete", "info", "toggle", "resize", "movehere"));
        } else if (args.length >= 2) {
            switch (args[0].toLowerCase()) {
                case "delete":
                case "toggle":
                case "movehere":
                case "resize":
                    if (args.length == 2) {
                        completions.addAll(plugin.zoneManager.getZoneNames());
                    } else if (args.length == 3) {
                    completions.add("<radius>");
                    } else if (args.length == 4) {
                        completions.add("[stepCount]");
                    }
                    break;
                case "create":
                    if (args.length == 2) {
                        completions.add("<name>");
                    } else if (args.length == 3) {
                        completions.addAll(Arrays.asList("circle", "square"));
                    } else if (args.length == 4) {
                        completions.add("<radius>");
                    } else if (args.length == 5) {
                        completions.addAll(Arrays.asList("effect", "must_stay", "safe", "team_only", "damage"));
                    } else if (args.length == 6) {
                        if (args[4].equalsIgnoreCase("effect")) {
                            completions.add("<effect:level>");
                        } else if (args[4].equalsIgnoreCase("team_only")) {
                            completions.addAll(plugin.teamManager.getTeamNames());
                        } else if (args[4].equalsIgnoreCase("damage")) {
                            completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
                        }
                    }
                    break;
            }
        }
        return completions;
    }

    private void sendZoneHelp(CommandSender sender) {
        plugin.sendMessage(sender,
                "&6Zone Commands:\n" +
                        "&e/zone create <name> <circle|square> <radius> <effect|must_stay|safe|team_only> [effect:amplifier|team] &7- Create a new zone\n" +
                        "&e/zone delete <name> &7- Delete a zone\n" +
                        "&e/zone info &7- Show detailed zone info\n" +
                        "&e/zone toggle <name> &7- Toggles a zone\n" +
                        "&e/zone resize <name> <radius> [stepCount] &7- Resizes a zone\n" +
                        "&e/zone movehere <name> &7- Moves zone to your location\n"
        );
    }
}