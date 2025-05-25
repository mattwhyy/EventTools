package net.mattwhyy.eventTools.teams;

import net.mattwhyy.eventTools.EventTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.scoreboard.Team.Option;

import java.util.*;

public class Team {
    private final String name;
    private ChatColor color;
    private final Set<UUID> members = new HashSet<>();
    private final org.bukkit.scoreboard.Team scoreboardTeam;
    private final EventTools plugin;

    public Team(String name, ChatColor color, EventTools plugin) {
        this.name = name;
        this.color = color;
        this.plugin = plugin;

        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "evt_" + name.replace(" ", "_").toLowerCase();

        org.bukkit.scoreboard.Team existingTeam = scoreboard.getTeam(teamName);
        if (existingTeam != null) {
            existingTeam.unregister();
        }

        this.scoreboardTeam = scoreboard.registerNewTeam(teamName);
        this.scoreboardTeam.setColor(color);
    }

    public boolean friendlyFire = false;
    public boolean nameTagVisibility = true;
    public boolean collisionEnabled = true;
    public boolean fallDamageEnabled = true;
    public boolean hungerDecayEnabled = true;
    public boolean invulnerable = false;

    public void setFriendlyFire(boolean enabled) {
        this.friendlyFire = enabled;
        updateTeamProperties();
    }

    public void setNameTagVisibility(boolean visible) {
        this.nameTagVisibility = visible;
        updateTeamProperties();
    }

    public void setCollisionEnabled(boolean enabled) {
        this.collisionEnabled = enabled;
        updateTeamProperties();
    }

    public void setFallDamageEnabled(boolean enabled) {
        this.fallDamageEnabled = enabled;
        updateTeamProperties();
    }

    public void setHungerDecayEnabled(boolean enabled) {
        this.hungerDecayEnabled = enabled;
        updateTeamProperties();
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
        updateTeamProperties();
    }

    private void updateTeamProperties() {
        scoreboardTeam.setAllowFriendlyFire(friendlyFire);
        scoreboardTeam.setNameTagVisibility(nameTagVisibility ?
                NameTagVisibility.ALWAYS : NameTagVisibility.NEVER);
        scoreboardTeam.setOption(Option.COLLISION_RULE,
                collisionEnabled ? OptionStatus.ALWAYS : OptionStatus.NEVER);

        getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> {
                    scoreboardTeam.removeEntry(player.getName());
                    scoreboardTeam.addEntry(player.getName());
                });
    }

    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public boolean isFallDamageEnabled() { return fallDamageEnabled; }
    public boolean isHungerDecayEnabled() { return hungerDecayEnabled; }
    public boolean isInvulnerable() { return invulnerable; }
    public List<Player> getOnlineMembers() {
        List<Player> onlineMembers = new ArrayList<>();
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                onlineMembers.add(player);
            }
        }
        return onlineMembers;
    }
    public int size() { return members.size(); }

    public void unregister() {
        try {
            if (scoreboardTeam != null) {
                Set<String> entries = new HashSet<>(scoreboardTeam.getEntries());
                entries.forEach(scoreboardTeam::removeEntry);

                if (Bukkit.getScoreboardManager().getMainScoreboard().getTeams().contains(scoreboardTeam)) {
                    scoreboardTeam.unregister();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error unregistering team " + name + ": " + e.getMessage());
        }
    }

    public void addMember(Player player) {
        if (player == null) return;

        members.add(player.getUniqueId());
        try {
            scoreboardTeam.addEntry(player.getName());
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Failed to add " + player.getName() + " to team " + name);
        }
    }

    public void removeMember(Player player) {
        if (player == null) return;

        members.remove(player.getUniqueId());
        try {
            scoreboardTeam.removeEntry(player.getName());
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Failed to remove " + player.getName() + " from team " + name);
        }
    }

    public void setColor(ChatColor color) {
        this.color = color;
        scoreboardTeam.setColor(color);

        members.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> {
                    scoreboardTeam.removeEntry(player.getName());
                    scoreboardTeam.addEntry(player.getName());
                });
    }

    private boolean teamChatEnabled = false;
    private final Set<UUID> teamChatToggled = new HashSet<>();

    public void setTeamChatEnabled(boolean enabled) {
        this.teamChatEnabled = enabled;
    }

    public boolean isTeamChatEnabled() {
        return teamChatEnabled;
    }

    public void toggleTeamChat(UUID playerId) {
        if (teamChatToggled.contains(playerId)) {
            teamChatToggled.remove(playerId);
        } else {
            teamChatToggled.add(playerId);
        }
    }

    public boolean hasTeamChatToggled(UUID playerId) {
        return teamChatToggled.contains(playerId);
    }
}