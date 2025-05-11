package net.mattwhyy.eventTools;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventToolsExpansion extends PlaceholderExpansion {
    private final EventTools plugin;

    public EventToolsExpansion(EventTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eventtools";
    }

    @Override
    public @NotNull String getAuthor() {
        return "mattwhyy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "alive_count":
                return String.valueOf(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !plugin.isEliminated(p))
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .count());

            case "eliminated_count":
                return String.valueOf(plugin.eliminatedPlayers.size());

            case "all_players_count":
                return String.valueOf(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .count());

            case "is_eliminated":
                if (params.contains("_")) {
                    String targetName = params.split("_")[1];
                    Player target = Bukkit.getPlayer(targetName);
                    return target != null && plugin.isEliminated(target) ? "Yes" : "No";
                }

                return plugin.isEliminated(player) ? "Yes" : "No";

            case "event_active":
                return plugin.eventActive ? "Yes" : "No";

            case "current_vote":
                return plugin.currentVoteQuestion != null ? plugin.currentVoteQuestion : "None";

            case "event_time_elapsed":
                if (plugin.eventStartTime == 0) return "Not running";
                long elapsed = System.currentTimeMillis() - plugin.eventStartTime;
                return formatDuration(elapsed);

            case "vote_time_left":
                if (!plugin.voteInProgress) return "No vote";
                return plugin.voteTimeRemaining + "s";

            case "vote_yes_percent":
                if (!plugin.voteInProgress) return "0%";
                int totalVotes = plugin.votes.size();
                if (totalVotes == 0) return "0%";
                long yesVotes = plugin.votes.values().stream().filter(b -> b).count();
                return Math.round((yesVotes * 100.0) / totalVotes) + "%";

            case "vote_leading_option":
                if (!plugin.voteInProgress) return "None";
                long yes = plugin.votes.values().stream().filter(b -> b).count();
                long no = plugin.votes.size() - yes;
                if (yes == no) return "Tie";
                return yes > no ? "YES" : "NO";

            case "event_title":
                return !plugin.eventActive ? "None" :
                        (plugin.eventTitle != null ? plugin.eventTitle : "Event");

            default:
                return null;
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d",
                (seconds % 3600) / 60,
                seconds % 60);
    }
}
