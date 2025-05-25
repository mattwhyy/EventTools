package net.mattwhyy.eventTools.teams;

import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import net.mattwhyy.eventTools.EventTools;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.broadcastMessage;

public class TeamManager {
    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Set<UUID> unassignedPlayers = ConcurrentHashMap.newKeySet();
    private final EventTools plugin;
    private static final int MAX_TEAMS = 16;
    private final List<Team> teamEliminationOrder = Collections.synchronizedList(new ArrayList<>());
    private Team winningTeam = null;

    public TeamManager(EventTools plugin) {
        this.plugin = plugin;
    }

    public List<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }

    public Team getTeam(String name) {
        return teams.get(name.toLowerCase());
    }

    public List<String> getTeamNames() {
        return new ArrayList<>(teams.keySet());
    }

    public List<Team> getActiveTeams() {
        return teams.values().stream()
                .filter(team -> !team.getMembers().isEmpty())
                .collect(Collectors.toList());
    }

    public Optional<Team> getPlayerTeam(Player player) {
        return teams.values().stream()
                .filter(team -> team.getMembers().contains(player.getUniqueId()))
                .findFirst();
    }

    public boolean createTeam(String name, ChatColor color) {
        if (teams.size() >= MAX_TEAMS) return false;
        if (teams.containsKey(name.toLowerCase())) return false;

        Team newTeam = new Team(name, color, plugin);
        teams.put(name.toLowerCase(), newTeam);
        return true;
    }

    public boolean deleteTeam(String name) {
        Team removed = teams.remove(name.toLowerCase());
        if (removed != null) {
            try {
                Set<UUID> members = new HashSet<>(removed.getMembers());

                members.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(removed::removeMember);

                removed.unregister();
                synchronized (teamEliminationOrder) {
                    teamEliminationOrder.remove(removed);
                }
                if (winningTeam == removed) {
                    winningTeam = null;
                }

                if (plugin.eventActive) {
                    if (teams.size() == 1) {
                        deleteTeam(teams.keySet().iterator().next());
                    } else {
                        balanceTeams();
                    }
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Error deleting team " + name + ": " + e.getMessage());
            }
        }
        return false;
    }

    public void autoAssignPlayer(Player player) {
        if (teams.isEmpty() || plugin.isEliminated(player)) return;

        if (getPlayerTeam(player).isPresent()) return;

        if (!plugin.eventActive) return;

        teams.values().stream()
                .min(Comparator.comparingInt(Team::size))
                .ifPresent(team -> team.addMember(player));
    }

    public boolean addToTeam(Player player, String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        if (team == null) return false;

        getPlayerTeam(player).ifPresent(t -> t.removeMember(player));

        team.addMember(player);
        unassignedPlayers.remove(player.getUniqueId());

        if (isTeamEliminated(team) && !plugin.isEliminated(player)) {
            reviveTeam(team);
            plugin.broadcastMessage(team.getColor() + team.getName() +
                    " &ahas been revived by a new member!");
        }
        return true;
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

    public void markTeamEliminated(Team team) {
        synchronized (teamEliminationOrder) {
            if (!teamEliminationOrder.contains(team)) {
                teamEliminationOrder.add(team);
            }
        }
    }

    public void reviveTeam(Team team) {
        synchronized (teamEliminationOrder) {
            teamEliminationOrder.remove(team);
        }
        if (team.equals(winningTeam)) {
            winningTeam = null;
        }
    }

    public boolean isTeamEliminated(Team team) {
        return teamEliminationOrder.contains(team);
    }

    public boolean isTeamActive(Team team) {
        return team.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .anyMatch(p -> !plugin.isEliminated(p));
    }

    public void setWinningTeam(Team team) {
        this.winningTeam = team;
    }

    public void checkForTeamVictory() {
        if (!plugin.eventActive || teams.isEmpty()) return;

        List<Team> activeTeams = teams.values().stream()
                .filter(team -> team.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .anyMatch(p -> !plugin.isEliminated(p)))
                .collect(Collectors.toList());

        if (activeTeams.size() <= 1) {
            if (!activeTeams.isEmpty()) {
                Team winner = activeTeams.get(0);
                setWinningTeam(winner);

                String teamName = winner.getName();
                ChatColor teamColor = winner.getColor();
                Player randomWinner = winner.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .findAny()
                        .orElse(null);

                announceTeamVictory(winner);
                if (randomWinner != null) {
                    spawnFireworks(randomWinner, teamName, teamColor);
                }
            }

            announceFinalTeamPlacements();
            sendTeamStatsEmbed();
            new ArrayList<>(teams.keySet()).forEach(this::deleteTeam);
            plugin.resetEvent();
        }
    }

    private void spawnFireworks(Player winner, String teamName, ChatColor teamColor) {
        Location loc = winner.getLocation();
        Color fireworkColor = getBukkitColor(teamColor);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count++ >= 15) {
                    cancel();
                    return;
                }

                try {
                    Location fireLoc = loc.clone().add(
                            (Math.random() * 6) - 3,
                            Math.random() * 2,
                            (Math.random() * 6) - 3
                    );

                    Firework fw = fireLoc.getWorld().spawn(fireLoc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();

                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL)
                            .withColor(fireworkColor)
                            .withFade(Color.WHITE)
                            .trail(true)
                            .flicker(true)
                            .build());

                    meta.setPower(1);
                    fw.setFireworkMeta(meta);

                    plugin.broadcastSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1);

                } catch (Exception e) {
                    plugin.getLogger().warning("Firework error: " + e.getMessage());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private void announceTeamVictory(Team team) {
        plugin.broadcastTitle(
                "&6&lWINNER",
                team.getColor() + team.getName()
        );
        plugin.broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5f);
        plugin.broadcastSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);
    }

    private void announceFinalTeamPlacements() {
        List<Team> placements = new ArrayList<>();

        if (winningTeam != null) {
            placements.add(winningTeam);
        }

        synchronized (teamEliminationOrder) {
            for (int i = teamEliminationOrder.size() - 1; i >= 0; i--) {
                Team team = teamEliminationOrder.get(i);
                if (!placements.contains(team)) {
                    placements.add(team);
                    if (placements.size() >= 5) break;
                }
            }
        }

        plugin.broadcastMessage("&6&lTeam Event Results:");
        String[] suffixes = {"1st", "2nd", "3rd", "4th", "5th"};
        String[] colors = {"&6", "&7", "&c", "&f", "&f"};
        String[] icons = {"🥇 ", "🥈 ", "🥉 ", "", ""};

        for (int i = 0; i < Math.min(5, placements.size()); i++) {
            Team team = placements.get(i);
            String placement = colors[i] + icons[i] + suffixes[i] + ": " +
                    team.getColor() + team.getName();
            plugin.broadcastMessage(placement);

            if (i < 3) {
                String members = team.getMembers().stream()
                        .map(uuid -> {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                            String name = player.getName() != null ? player.getName() : "Unknown";
                            boolean isEliminated = plugin.isEliminated(uuid);
                            boolean isOnline = player.isOnline();

                            return (isOnline ? "&7" : "&8") +
                                    (isEliminated ? "&m" + name + "&r" : name);
                        })
                        .collect(Collectors.joining("&7,&r "));

                plugin.broadcastMessage("&8&l>&r &7Members: " + members);
            }
        }
        sendTeamPlacementsEmbed(placements);
    }

    private void sendTeamPlacementsEmbed(List<Team> placements) {
        if (plugin.getDiscordManager() == null) {
            return;
        }
        String winnerColor = plugin.getConfig().getString("discord.colors.event-results", "#FFA500");
        String otherColor = plugin.getConfig().getString("discord.colors.event-other-placements", "#C4C4C4");

        EmbedBuilder topTeamsEmbed = new EmbedBuilder()
                .setTitle(plugin.eventTitle + " Team Results 🏆")
                .setColor(java.awt.Color.decode(winnerColor));

        for (int i = 0; i < Math.min(3, placements.size()); i++) {
            Team team = placements.get(i);
            String[] medals = {"🥇 **WINNING TEAM**", "🥈 Runner-Up", "🥉 Third Place"};

            String members = team.getMembers().stream()
                    .map(uuid -> {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        return player.getName() != null ? player.getName() : "Unknown";
                    })
                    .collect(Collectors.joining("\n"));

            topTeamsEmbed.addField(
                    medals[i] + " - " + team.getName(),
                    members,
                    false
            );
        }

        topTeamsEmbed.setFooter("Event ended")
                .setTimestamp(Instant.now());

        plugin.getDiscordManager().sendEmbed(topTeamsEmbed.build());

        if (placements.size() > 3) {
            EmbedBuilder otherTeamsEmbed = new EmbedBuilder()
                    .setTitle("Other Team Placements")
                    .setColor(java.awt.Color.decode(otherColor))
                    .setFooter("Thanks for playing!");

            for (int i = 3; i < Math.min(5, placements.size()); i++) {
                Team team = placements.get(i);
                otherTeamsEmbed.addField(
                        (i+1) + "th Place",
                        team.getName(),
                        false
                );
            }

            plugin.getDiscordManager().sendEmbed(otherTeamsEmbed.build());
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

    public void sendTeamStatsEmbed() {
        if (plugin.getDiscordManager() == null) {
            return;
        }
        long durationMillis = System.currentTimeMillis() - plugin.eventStartTime;
        String duration = String.format("%d min %d sec",
                TimeUnit.MILLISECONDS.toMinutes(durationMillis),
                TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        );

        int participants = teams.values().stream()
                .mapToInt(Team::size)
                .sum();


        String statsColor = plugin.getConfig().getString("discord.colors.info", "#0099FF");

        EmbedBuilder statsEmbed = new EmbedBuilder()
                .setTitle("📊 Team Event Stats")
                .setColor(java.awt.Color.decode(statsColor))
                .addField("Duration", duration, true)
                .addField("Teams", String.valueOf(teams.size()), true)
                .addField("Participants", String.valueOf(participants), true);

        plugin.getDiscordManager().sendEmbed(statsEmbed.build());
    }
}