package net.mattwhyy.eventTools.zones;

import net.mattwhyy.eventTools.EventTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneManager {
    private final EventTools plugin;
    private final Map<String, EventZone> zones = new ConcurrentHashMap<>();
    private BukkitTask checkTask;

    public ZoneManager(EventTools plugin) {
        this.plugin = plugin;
        startZoneChecker();
        registerEvents();
    }

    private void registerEvents() {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                Player player = event.getPlayer();
                zones.values().stream()
                        .filter(zone -> zone.getPlayersInside().contains(player))
                        .forEach(zone -> zone.removeEffects(player));
            }
        }, plugin);
    }

    private void startZoneChecker() {
        this.checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.eventActive) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (EventZone zone : zones.values()) {
                        boolean isInside = zone.contains(player.getLocation());

                        if (zone.getType() == ZoneType.TEAM_ONLY && isInside && !zone.canAccess(player)) {
                            pushPlayerOut(player, zone);
                            continue;
                        }

                        if (isInside) {
                            zone.applyEffects(player);
                        } else {
                            zone.removeEffects(player);
                            if (zone.getType() == ZoneType.MUST_STAY) {
                                plugin.handleElimination(player);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void pushPlayerOut(Player player, EventZone zone) {
        @NotNull Vector direction = player.getLocation().toVector()
                .subtract(zone.getCenter().toVector())
                .normalize()
                .multiply(0.7);

        player.setVelocity(direction);
        plugin.sendMessage(player, "&cThis area is restricted to " +
                zone.getAllowedTeam() + " team!");
    }

    public void startParticleRenderer() {
        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (EventZone zone : zones.values()) {
                    if (!zone.isActive()) continue;

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getWorld().equals(zone.getCenter().getWorld())) {
                            if (player.getLocation().distance(zone.getCenter()) <= zone.getRadius() + 20) {
                                zone.displayBorder(player);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void addZone(EventZone zone) {
        zones.put(zone.getName().toLowerCase(), zone);
    }

    public List<String> getZoneNames() {
        return new ArrayList<>(zones.keySet());
    }

    public EventZone getZone(String name) {
        return zones.get(name.toLowerCase());
    }

    public boolean removeZone(String name) {
        EventZone zone = zones.remove(name.toLowerCase());
        if (zone != null) {
            zone.setActive(false);
            plugin.cancelResizeTask(name.toLowerCase());
            return true;
        }
        return false;
    }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        zones.values().forEach(zone ->
                Bukkit.getOnlinePlayers().forEach(zone::removeEffects));
    }
}