package net.mattwhyy.eventTools.commands.team;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamInfoCommand extends BaseCommand {
    public TeamInfoCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (plugin.teamManager.getAllTeams().isEmpty()) {
            plugin.sendMessage(sender, "&7No teams available. Use &e/team create &7to create some!");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Teams:\n");
        plugin.teamManager.getAllTeams().forEach(team -> {
            message.append(team.getColor())
                    .append(team.getName())
                    .append(" &7(")
                    .append(team.size())
                    .append(" Member").append(team.size() != 1 ? "s" : "").append(")");

            message.append("\n&8&l>&r &7Settings: ");
            message.append("Friendly Fire: ").append(team.friendlyFire ? "&atrue" : "&cfalse");
            message.append("&7, Collision: ").append(team.collisionEnabled ? "&atrue" : "&cfalse");
            message.append("&7, Nametags: ").append(team.nameTagVisibility ? "&atrue" : "&cfalse");

            message.append("\n&8&l>&r &7Members: ");
            if (team.getMembers().isEmpty()) {
                message.append("&7None");
            } else {
                message.append(team.getMembers().stream()
                        .map(uuid -> {
                            OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
                            String name = player.getName() != null ? player.getName() : "Unknown";
                            boolean isOnline = player.isOnline();
                            boolean isEliminated = plugin.isEliminated(uuid);

                            return (isOnline ? "&7" : "&8") +
                                    (isEliminated ? "&m" + name + "&r" : name);
                        })
                        .collect(Collectors.joining("&7,&r ")));
            }
            message.append("\n");
        });

        plugin.sendMessage(sender, message.toString());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}