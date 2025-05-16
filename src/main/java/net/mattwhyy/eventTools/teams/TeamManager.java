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
    private static final int MAX_TEAMS = 16;

    public TeamManager(EventTools plugin) {
        this.plugin = plugin;
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
            List<Player> playersToReassign = removed.getMembers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .toList();

            playersToReassign.forEach(removed::resetPlayerDisplay);

            if (teams.size() == 1 && plugin.eventActive) {
                Team lastTeam = teams.values().iterator().next();
                deleteTeam(lastTeam.getName());
            }
            else if (plugin.eventActive) {
                playersToReassign.forEach(p -> unassignedPlayers.add(p.getUniqueId()));
                balanceTeams();
            }
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
        if (plugin.eventActive && !teams.isEmpty()) {
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
        List<Player> unassignedPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .filter(p -> getPlayerTeam(p).isEmpty())
                .collect(Collectors.toList());

        List<Team> activeTeams = new ArrayList<>(teams.values());

        if (activeTeams.isEmpty() || unassignedPlayers.isEmpty()) {
            return;
        }

        Collections.shuffle(unassignedPlayers);
        Collections.shuffle(activeTeams);

        int teamIndex = 0;
        for (Player player : unassignedPlayers) {
            Team team = activeTeams.get(teamIndex % activeTeams.size());
            team.addMember(player);
            teamIndex++;
        }
    }

    public List<String> getTeamNames() {
        return new ArrayList<>(teams.keySet());
    }

    public void checkForTeamVictory() {
        if (!plugin.eventActive || teams.isEmpty()) return;

        List<Team> activeTeams = teams.values().stream()
                .filter(this::hasActiveMembers)
                .toList();

        if (activeTeams.size() <= 1) {
            Team winningTeam = activeTeams.isEmpty() ? null : activeTeams.get(0);

            if (winningTeam != null) {
                announceTeamVictory(winningTeam);
                celebrateVictory(winningTeam);
            } else {
                plugin.broadcastMessage("&cAll teams were eliminated!");
            }

            plugin.resetEvent();
        }
    }

    private boolean hasActiveMembers(Team team) {
        return team.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .anyMatch(p -> !plugin.isEliminated(p));
    }

    private void announceTeamVictory(Team team) {
        plugin.broadcastTitle(
                "&6&lTEAM VICTORY",
                team.getColor() + team.getName() + " &awins!"
        );
        plugin.broadcastMessage(team.getColor() + team.getName() + " &ahas won the event!");
    }

    private void celebrateVictory(Team team) {
        List<Player> winners = team.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> !plugin.isEliminated(p))
                .collect(Collectors.toList());

        if (winners.isEmpty()) return;

        Player celebrationWinner = winners.get(new Random().nextInt(winners.size()));
        Location center = celebrationWinner.getLocation();

        Bukkit.getOnlinePlayers().forEach(p ->
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)
        );

        new BukkitRunnable() {
            int fireworksLeft = 15;
            Random random = new Random();

            @Override
            public void run() {
                if (fireworksLeft <= 0) {
                    cancel();
                    return;
                }

                Location fireworkLoc = center.clone().add(
                        random.nextDouble() * 10 - 5,
                        0,
                        random.nextDouble() * 10 - 5
                );

                Firework fw = celebrationWinner.getWorld().spawn(celebrationWinner.getLocation(), Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();

                FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
                Color color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                Color fade = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));

                meta.addEffect(FireworkEffect.builder()
                        .with(type)
                        .withColor(color)
                        .withFade(fade)
                        .trail(random.nextBoolean())
                        .flicker(random.nextBoolean())
                        .build());

                meta.setPower(1 + random.nextInt(2));
                fw.setFireworkMeta(meta);

                fireworkLoc.getWorld().playSound(fireworkLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);

                fireworksLeft--;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}