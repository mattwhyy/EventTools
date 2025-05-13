package net.mattwhyy.eventTools.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Team {
    private final String name;
    private ChatColor color;
    private final Set<UUID> members = new HashSet<>();

    public Team(String name, ChatColor color) {
        this.name = name;
        this.color = color;
    }

    public void addMember(Player player) {
        members.add(player.getUniqueId());
        updatePlayerDisplay(player);
    }

    public void removeMember(Player player) {
        members.remove(player.getUniqueId());
        resetPlayerDisplay(player);
    }

    private void updatePlayerDisplay(Player player) {
        String coloredName = color + player.getName();
        player.setDisplayName(coloredName);
        player.setPlayerListName(coloredName);
        player.setCustomName(coloredName);
        player.setCustomNameVisible(true);
    }

    void resetPlayerDisplay(Player player) {
        player.setDisplayName(null);
        player.setPlayerListName(null);
        player.setCustomName(null);
        player.setCustomNameVisible(false);
    }

    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public int size() { return members.size(); }

    public void setColor(ChatColor color) {
        this.color = color;
        members.stream()
                .map(uuid -> Bukkit.getPlayer(uuid))
                .filter(Objects::nonNull)
                .forEach(this::updatePlayerDisplay);
    }
}