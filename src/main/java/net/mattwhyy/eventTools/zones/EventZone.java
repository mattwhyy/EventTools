package net.mattwhyy.eventTools.zones;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import java.util.HashSet;
import java.util.Set;

public class EventZone {
    private final String name;
    private final Location center;
    private final ZoneType type;
    private final Shape shape;
    private final int radius;
    private final PotionEffect effect;
    private boolean active;
    private final Set<Player> playersInside = new HashSet<>();

    public EventZone(String name, Location center, Shape shape, int radius, ZoneType type, PotionEffect effect) {
        this.name = name;
        this.center = center;
        this.shape = shape;
        this.radius = Math.min(radius, 50);
        this.type = type;
        this.effect = effect;
        this.active = true;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().equals(center.getWorld())) return false;

        return shape == Shape.CIRCLE
                ? location.distance(center) <= radius
                : Math.max(Math.abs(location.getX() - center.getX()),
                Math.abs(location.getZ() - center.getZ())) <= radius;
    }

    public void applyEffects(Player player) {
        if (!active) return;

        if (type == ZoneType.EFFECT && effect != null) {
            player.addPotionEffect(effect);
        } else if (type == ZoneType.SAFE) {
            player.setInvulnerable(true);
        }
        playersInside.add(player);
    }

    public void removeEffects(Player player) {
        if (type == ZoneType.EFFECT && effect != null) {
            player.removePotionEffect(effect.getType());
        } else if (type == ZoneType.SAFE) {
            player.setInvulnerable(false);
        }
        playersInside.remove(player);
    }

    public void displayBorder(Player viewer) {
        if (!active || !viewer.getWorld().equals(center.getWorld())) return;

        Particle.DustOptions dustOptions = switch (type) {
            case SAFE -> new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
            case MUST_STAY -> new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
            case EFFECT -> new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5f);
        };

        int points = 30;
        double y = center.getY() + 0.1;

        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double x, z;

            if (shape == Shape.CIRCLE) {
                double angle = progress * 2 * Math.PI;
                x = center.getX() + radius * Math.cos(angle);
                z = center.getZ() + radius * Math.sin(angle);
            } else {
                double sideLength = radius * 2;
                double perimeterPos = progress * (sideLength * 4);

                if (perimeterPos < sideLength) {
                    x = center.getX() - radius + perimeterPos;
                    z = center.getZ() - radius;
                } else if (perimeterPos < sideLength * 2) {
                    x = center.getX() + radius;
                    z = center.getZ() - radius + (perimeterPos - sideLength);
                } else if (perimeterPos < sideLength * 3) {
                    x = center.getX() + radius - (perimeterPos - sideLength * 2);
                    z = center.getZ() + radius;
                } else {
                    x = center.getX() - radius;
                    z = center.getZ() + radius - (perimeterPos - sideLength * 3);
                }
            }

            viewer.spawnParticle(Particle.REDSTONE,
                    new Location(center.getWorld(), x, y, z),
                    1, dustOptions);
        }
    }

    public String getName() { return name; }
    public ZoneType getType() { return type; }
    public boolean isActive() { return active; }
    public Set<Player> getPlayersInside() { return new HashSet<>(playersInside); }
    public int getRadius() {
        return radius;
    }
    public Location getCenter() {
        return center.clone();
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            new HashSet<>(playersInside).forEach(this::removeEffects);
        }
    }
}