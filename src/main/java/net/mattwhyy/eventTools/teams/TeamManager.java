package net.mattwhyy.eventTools.teams;

import net.mattwhyy.eventTools.EventTools;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TeamManager {
    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Set<UUID> unassignedPlayers = ConcurrentHashMap.newKeySet();
    private final EventTools plugin;
    private static final int MAX_TEAMS = 4;

    public TeamManager(EventTools plugin) {
        this.plugin = plugin;
    }

    public void validateTeams() {
        teams.values().removeIf(Objects::isNull);

        teams.forEach((name, team) -> {
            team.getMembers().removeIf(uuid ->
                    Bukkit.getPlayer(uuid) == null || plugin.isEliminated(Bukkit.getPlayer(uuid))
            );
        });

        if (plugin.eventActive) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.hasPermission("eventtools.bypass"))
                    .filter(p -> !plugin.isEliminated(p))
                    .filter(p -> getPlayerTeam(p).isEmpty())
                    .forEach(this::autoAssignPlayer);
        }
    }

    public void startValidationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                validateTeams();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    public void preserveTeamsOnStart() {
        getAllTeams().forEach(team -> {
            team.getMembers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .forEach(player -> {
                        player.setDisplayName(team.getColor() + player.getName());
                        player.setPlayerListName(team.getColor() + player.getName());
                    });
        });
    }

    public boolean createTeam(String name, ChatColor color) {
        if (teams.size() >= MAX_TEAMS) return false;
        if (teams.containsKey(name.toLowerCase())) return false;

        teams.put(name.toLowerCase(), new Team(name, color));
        return true;
    }

    public boolean deleteTeam(String name) {
        Team removed = teams.remove(name.toLowerCase());
        if (removed != null) {
            removed.getMembers().stream()
                    .map(uuid -> Bukkit.getPlayer(uuid))
                    .filter(Objects::nonNull)
                    .forEach(removed::resetPlayerDisplay);
            return true;
        }
        return false;
    }

    public boolean hasActiveTeams() {
        return !teams.isEmpty() && teams.values().stream()
                .anyMatch(team -> !team.getMembers().isEmpty());
    }

    public List<Team> getActiveTeams() {
        return teams.values().stream()
                .filter(team -> !team.getMembers().isEmpty())
                .collect(Collectors.toList());
    }

    public List<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }

    public Team getTeam(String name) {
        return teams.get(name.toLowerCase());
    }

    public void autoAssignPlayer(Player player) {
        if (teams.isEmpty() || plugin.isEliminated(player)) return;

        if (getPlayerTeam(player).isPresent()) return;

        if (!plugin.eventActive) return;

        teams.values().stream()
                .min(Comparator.comparingInt(Team::size))
                .ifPresent(team -> team.addMember(player));
    }

    public void handleRevival(Player player) {
        if (plugin.eventActive && teams.size() > 0) {
            autoAssignPlayer(player);
        }
    }

    public boolean addToTeam(Player player, String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        if (team == null) return false;

        getPlayerTeam(player).ifPresent(t -> t.removeMember(player));

        team.addMember(player);
        unassignedPlayers.remove(player.getUniqueId());
        return true;
    }

    public Optional<Team> getPlayerTeam(Player player) {
        return teams.values().stream()
                .filter(team -> team.getMembers().contains(player.getUniqueId()))
                .findFirst();
    }

    public void balanceTeams() {
        teams.values().forEach(team ->
                new HashSet<>(team.getMembers()).forEach(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) team.removeMember(p);
                })
        );

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        Collections.shuffle(players);
        int teamIndex = 0;
        Team[] teamArray = teams.values().toArray(new Team[0]);

        for (Player player : players) {
            if (teamArray.length == 0) {
                unassignedPlayers.add(player.getUniqueId());
                continue;
            }

            teamArray[teamIndex % teamArray.length].addMember(player);
            teamIndex++;
        }
    }

    public List<String> getTeamNames() {
        return new ArrayList<>(teams.keySet());
    }

    public void checkForTeamVictory() {
        if (!plugin.eventActive || teams.isEmpty()) return;

        long activeTeams = teams.values().stream()
                .filter(team -> team.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .anyMatch(p -> !plugin.isEliminated(p)))
                .count();

        if (activeTeams <= 1) {
            Team winningTeam = teams.values().stream()
                    .filter(team -> team.getMembers().stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .anyMatch(p -> !plugin.isEliminated(p)))
                    .findFirst()
                    .orElse(null);

            if (winningTeam != null) {
                plugin.broadcastTitle(
                        "&6&lTEAM VICTORY",
                        winningTeam.getColor() + winningTeam.getName() + " &awins!"
                );

                List<Player> winners = winningTeam.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(player -> !plugin.isEliminated(player))
                        .collect(Collectors.toList());

                if (winners.isEmpty()) {
                    plugin.resetEvent();
                    return;
                }

                Player firstWinner = winners.get(0);
                Bukkit.getOnlinePlayers().forEach(player -> {
                    player.playSound(
                            firstWinner.getLocation(),
                            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                            1.0f,
                            0.5f
                    );
                });

                int totalFireworks = Math.min(15, 3 + winners.size());
                int fireworksPerPlayer = Math.max(1, totalFireworks / winners.size());

                Random random = new Random();

                for (Player winner : winners) {
                    new BukkitRunnable() {
                        int fireworksLeft = fireworksPerPlayer;

                        @Override
                        public void run() {
                            if (fireworksLeft <= 0) {
                                cancel();
                                return;
                            }

                            Location loc = winner.getLocation().add(
                                    random.nextDouble() * 6 - 3,
                                    0,
                                    random.nextDouble() * 6 - 3
                            );

                            Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                            FireworkMeta meta = fw.getFireworkMeta();

                            FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
                            Color color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                            Color fade = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));

                            loc.getWorld().getNearbyEntities(loc, 20, 20, 20).forEach(entity -> {
                                if (entity instanceof Player) {
                                    ((Player) entity).playSound(
                                            loc,
                                            Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                                            0.7f,
                                            1.0f
                                    );
                                }
                            });

                            meta.addEffect(FireworkEffect.builder()
                                    .with(type)
                                    .withColor(color)
                                    .withFade(fade)
                                    .trail(random.nextBoolean())
                                    .flicker(random.nextBoolean())
                                    .build());

                            meta.setPower(1 + random.nextInt(2));
                            fw.setFireworkMeta(meta);

                            fireworksLeft--;
                        }

                    }.runTaskTimer(plugin, 0L, 5 + random.nextInt(10));
                }

                plugin.resetEvent();
            }
        }
    }
}