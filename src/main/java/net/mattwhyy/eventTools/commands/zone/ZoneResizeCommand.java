package net.mattwhyy.eventTools.commands.zone;

import net.mattwhyy.eventTools.EventTools;
import net.mattwhyy.eventTools.commands.BaseCommand;
import net.mattwhyy.eventTools.zones.EventZone;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class ZoneResizeCommand extends BaseCommand {
    public ZoneResizeCommand(EventTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "eventtools.admin")) return true;
        if (args.length < 3) {
            plugin.sendMessage(sender, "&cUsage: /zone resize <name> <radius> [stepCount]");
            return true;
        }

        String zoneName = args[1];
        EventZone zone = plugin.zoneManager.getZone(zoneName);
        if (zone == null) {
            plugin.sendMessage(sender, "&cZone '" + zoneName + "' not found!");
            return true;
        }

        try {
            int newRadius = Integer.parseInt(args[2]);
            newRadius = Math.min(newRadius, 100);

            int stepCount = 1;
            if (args.length >= 4) {
                stepCount = Math.min(Integer.parseInt(args[3]), 900);
                if (stepCount <= 0) {
                    plugin.sendMessage(sender, "&cStep count must be positive! (1-900)");
                    return true;
                }
            }

            plugin.cancelResizeTask(zoneName);

            int currentRadius = zone.getRadius();
            int difference = newRadius - currentRadius;
            double changePerStep = (double)difference / stepCount;

            if (stepCount > 1) {
                int finalNewRadius = newRadius;
                int finalStepCount = stepCount;
                BukkitTask task = new BukkitRunnable() {
                    private int stepsCompleted = 0;
                    private double current = currentRadius;

                    @Override
                    public void run() {
                        if (plugin.zoneManager.getZone(zoneName) == null) {
                            this.cancel();
                            return;
                        }

                        if (stepsCompleted >= finalStepCount) {
                            zone.setRadius(finalNewRadius);
                            plugin.sendMessage(sender, String.format(
                                    "&aZone &e'%s' &ahas finished resizing to radius &e%d",
                                    zoneName, finalNewRadius
                            ));
                            this.cancel();
                            return;
                        }

                        current += changePerStep;
                        zone.setRadius((int)Math.round(current));
                        stepsCompleted++;

                        if (stepsCompleted % 5 == 0 || stepsCompleted == finalStepCount) {
                            plugin.sendMessage(sender, String.format(
                                    "&7Zone &e'%s' &7progress: &a%d%% &8(%d/%d steps)",
                                    zoneName,
                                    (stepsCompleted * 100) / finalStepCount,
                                    stepsCompleted,
                                    finalStepCount
                            ));
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L);

                plugin.activeResizeTasks.put(zoneName, task);

                plugin.sendMessage(sender, String.format(
                        "&aZone &e'%s' &aresizing from &e%d &ato &e%d &ain &e%d &aseconds",
                        zoneName, currentRadius, newRadius, stepCount
                ));
            } else {
                zone.setRadius(newRadius);
                plugin.sendMessage(sender, String.format(
                        "&aZone &e'%s' &aresized to radius &e%d",
                        zoneName, newRadius
                ));
            }

            return true;
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, "&cInvalid number format for radius or step count!");
            return true;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.addAll(plugin.zoneManager.getZoneNames());
        } else if (args.length == 3) {
            completions.add("<radius>");
        } else if (args.length == 4) {
            completions.add("[stepCount]");
        }
        return completions;
    }
}