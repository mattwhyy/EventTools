package net.mattwhyy.eventTools.commands;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.event.SetEventSpawnCommand;
import net.mattwhyy.eventTools.commands.event.StartEventCommand;
import net.mattwhyy.eventTools.commands.event.StopEventCommand;
import net.mattwhyy.eventTools.commands.inventory.ClearInventoryCommand;
import net.mattwhyy.eventTools.commands.inventory.GiveItemCommand;
import net.mattwhyy.eventTools.commands.inventory.InvSeeCommand;
import net.mattwhyy.eventTools.commands.inventory.KitCommand;
import net.mattwhyy.eventTools.commands.player.*;
import net.mattwhyy.eventTools.commands.team.TeamCommand;
import net.mattwhyy.eventTools.commands.utility.*;
import net.mattwhyy.eventTools.commands.zone.ZoneCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandManager implements TabExecutor {
    private final Map<String, BaseCommand> commands = new HashMap<>();
    private final EventTools plugin;

    public CommandManager(EventTools plugin) {
        this.plugin = plugin;
        registerCommands();
    }

    private void registerCommands() {
        // Event commands
        commands.put("startevent", new StartEventCommand(plugin));
        commands.put("stopevent", new StopEventCommand(plugin));
        commands.put("seteventspawn", new SetEventSpawnCommand(plugin));

        // Player commands
        commands.put("eliminate", new EliminateCommand(plugin));
        commands.put("revive", new ReviveCommand(plugin));
        commands.put("bring", new BringCommand(plugin));
        commands.put("heal", new HealCommand(plugin));
        commands.put("freeze", new FreezeCommand(plugin));

        // Inventory commands
        commands.put("giveitem", new GiveItemCommand(plugin));
        commands.put("clearinventory", new ClearInventoryCommand(plugin));
        commands.put("invsee", new InvSeeCommand(plugin));

        // Effects commands
        commands.put("timedeffect", new TimedEffectCommand(plugin));

        // Game commands
        commands.put("changegamemode", new ChangeGamemodeCommand(plugin));
        commands.put("kit", new KitCommand(plugin));

        // Voting commands
        commands.put("startvote", new StartVoteCommand(plugin));
        commands.put("endvote", new EndVoteCommand(plugin));

        // Utility commands
        commands.put("countdown", new CountdownCommand(plugin));
        commands.put("numberguess", new NumberGuessCommand(plugin));
        commands.put("mutechat", new MuteChatCommand(plugin));
        commands.put("clearchat", new ClearChatCommand(plugin));
        commands.put("list", new ListCommand(plugin));

        // Zone commands
        commands.put("zone", new ZoneCommand(plugin));

        // Team commands
        commands.put("team", new TeamCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        BaseCommand command = commands.get(cmd.getName().toLowerCase());
        if (command != null) {
            try {
                boolean result = command.execute(sender, args);
                if (!result) {
                    return false;
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing command: " + e.getMessage());
                e.printStackTrace();
                plugin.sendMessage(sender, "&cAn error occurred. Please check console.");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        BaseCommand command = commands.get(cmd.getName().toLowerCase());
        if (command != null) {
            return command.tabComplete(sender, args);
        }
        return new ArrayList<>();
    }
}