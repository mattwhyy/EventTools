package net.mattwhyy.eventTools.zones;

import net.mattwhyy.eventTools.teams.Team;
import net.mattwhyy.eventTools.teams.TeamManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.stream.Collectors;

public class EventZone {
    private final String name;
    private final Location center;
    private final ZoneType type;
    private final Shape shape;
    private int radius;
    private final List<PotionEffect> effects;
    private boolean active;
    private final double damagePerSecond;
    private final Set<UUID> playersInside = new HashSet<>();
    private String allowedTeam;
    private final TeamManager teamManager;

    public void setAllowedTeam(String teamName) {
        this.allowedTeam = teamName != null ? teamName.toLowerCase() : null;
    }

    public String getAllowedTeam() {
        return allowedTeam;
    }

    public void setRadius(int radius) {
        this.radius = Math.min(radius, 100);
    }

    public boolean canAccess(Player player) {
        if (allowedTeam == null) return true;
        if (player.hasPermission("eventtools.bypass")) return true;

        return teamManager.getPlayerTeam(player)
                .map(team -> team.getName().equalsIgnoreCase(allowedTeam))
                .orElse(false);
    }

    public EventZone(String name, Location center, Shape shape, int radius, ZoneType type, List<PotionEffect> effects, double damage, TeamManager teamManager) {
        this.name = name;
        this.center = center;
        this.shape = shape;
        this.radius = Math.min(radius, 100);
        this.type = type;
        this.teamManager = teamManager;
        this.active = true;
        this.effects = effects != null ? effects : new ArrayList<>();
        this.damagePerSecond = damage;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().equals(center.getWorld())) return false;

        return shape == Shape.CIRCLE
                ? location.distance(center) <= radius
                : Math.max(Math.abs(location.getX() - center.getX()),
                Math.abs(location.getZ() - center.getZ())) <= radius;
    }

    public void applyEffects(Player player) {
        if (!active || player.hasPermission("eventtools.bypass")) return;

        switch (type) {
            case EFFECT:
                effects.forEach(player::addPotionEffect);
                break;
            case DAMAGE:
                if ((player.getNoDamageTicks() == 0)) {
                    player.damage(damagePerSecond / 2);
                }
                break;
            case SAFE:
                player.setInvulnerable(true);
                break;
        }
        playersInside.add(player.getUniqueId());
    }

    public void removeEffects(Player player) {
        if (type == ZoneType.EFFECT) {
            effects.forEach(effect ->
                    player.removePotionEffect(effect.getType()));
        }
        else if (type == ZoneType.SAFE) {
            player.setInvulnerable(false);
        }
        playersInside.remove(player.getUniqueId());
    }


    public void displayBorder(Player viewer) {
        if (!active || !viewer.getWorld().equals(center.getWorld())) return;

        Particle.DustOptions dustOptions = switch (type) {
            case SAFE -> new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
            case MUST_STAY -> new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
            case EFFECT -> new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5f);
            case TEAM_ONLY -> {
                if (allowedTeam != null) {
                    Team team = teamManager.getTeam(allowedTeam);
                    yield new Particle.DustOptions(
                            getBukkitColor(team != null ? team.getColor() : ChatColor.WHITE),
                            1.5f
                    );
                }
                yield new Particle.DustOptions(Color.WHITE, 1.5f);
            }
            case DAMAGE -> new Particle.DustOptions(Color.fromRGB(255, 145, 0), 1.5f);
        };

        double circumference = shape == Shape.CIRCLE
                ? 2 * Math.PI * radius
                : 8 * radius;

        int optimalParticles = (int) Math.round(circumference / 2.0);

        int particles = Math.min(
                Math.max(optimalParticles, 12),
                150
        );

        double angleIncrement = (2 * Math.PI) / particles;
        double y = center.getY() + 0.1;

        for (int i = 0; i < particles; i++) {
            double angle = i * angleIncrement;
            double x, z;

            if (shape == Shape.CIRCLE) {
                x = center.getX() + radius * Math.cos(angle);
                z = center.getZ() + radius * Math.sin(angle);
            } else {
                double sideProgress = (angle % (Math.PI/2)) / (Math.PI/2);
                int side = (int) (angle / (Math.PI/2)) % 4;

                switch (side) {
                    case 0:
                        x = center.getX() + radius;
                        z = center.getZ() - radius + (sideProgress * 2 * radius);
                        break;
                    case 1:
                        x = center.getX() + radius - (sideProgress * 2 * radius);
                        z = center.getZ() + radius;
                        break;
                    case 2:
                        x = center.getX() - radius;
                        z = center.getZ() + radius - (sideProgress * 2 * radius);
                        break;
                    default:
                        x = center.getX() - radius + (sideProgress * 2 * radius);
                        z = center.getZ() - radius;
                }
            }

            viewer.spawnParticle(Particle.REDSTONE,
                    new Location(center.getWorld(), x, y, z),
                    1, dustOptions);
        }
    }

    public void cleanupDisconnectedPlayers() {
        Iterator<UUID> iterator = playersInside.iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                iterator.remove();
            }
        }
    }

    public String getName() { return name; }
    public ZoneType getType() { return type; }
    public Shape getShape() { return shape; }
    public List<PotionEffect> getEffects() { return effects; }
    public boolean isActive() { return active; }
    public double getDamage() { return damagePerSecond; }
    public Set<Player> getPlayersInside() {
        cleanupDisconnectedPlayers();
        return playersInside.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    public int getRadius() {
        return radius;
    }
    public Location getCenter() {
        return center.clone();
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            getPlayersInside().forEach(this::removeEffects);
            playersInside.clear();
        }
    }

    private Color getBukkitColor(ChatColor chatColor) {
        switch (chatColor) {
            case AQUA: return Color.AQUA;
            case BLACK: return Color.BLACK;
            case BLUE: return Color.BLUE;
            case DARK_AQUA: return Color.TEAL;
            case DARK_BLUE: return Color.NAVY;
            case DARK_GRAY: return Color.GRAY;
            case DARK_GREEN: return Color.GREEN;
            case DARK_PURPLE: return Color.PURPLE;
            case DARK_RED: return Color.MAROON;
            case GOLD: return Color.ORANGE;
            case GRAY: return Color.SILVER;
            case GREEN: return Color.LIME;
            case LIGHT_PURPLE: return Color.FUCHSIA;
            case RED: return Color.RED;
            case WHITE: return Color.WHITE;
            case YELLOW: return Color.YELLOW;
            default: return Color.WHITE;
        }
    }
}