package net.mattwhyy.eventTools.zones;

import net.mattwhyy.eventTools.EventTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ZoneManager {
    private final EventTools plugin;
    private final Map<String, EventZone> zones = new HashMap<>();
    private BukkitTask checkTask;

    public ZoneManager(EventTools plugin) {
        this.plugin = plugin;
        startZoneChecker();
    }

    private void startZoneChecker() {
        this.checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.eventActive) return;

                zones.values().forEach(zone -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        boolean isInside = zone.contains(player.getLocation());

                        if (isInside) {
                            zone.applyEffects(player);
                        } else {
                            zone.removeEffects(player);
                            if (zone.getType() == ZoneType.MUST_STAY) {
                                plugin.handleElimination(player);
                            }
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 10L);
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