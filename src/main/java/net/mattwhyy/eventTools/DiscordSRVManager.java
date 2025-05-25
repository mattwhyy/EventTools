package net.mattwhyy.eventTools;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.*;

public class DiscordSRVManager {
    private final EventTools plugin;
    private boolean enabled;
    private boolean initialized;
    private String channelId;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY = 5000;

    public DiscordSRVManager(EventTools plugin) {
        this.plugin = plugin;
        scheduleInitialization();
    }

    private void scheduleInitialization() {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::delayedInit, 20L);
    }

    private void delayedInit() {
        reload();
        if (enabled && !initialized && retryCount < MAX_RETRIES) {
            retryCount++;
            plugin.getLogger().info("Retrying DiscordSRV initialization (" + retryCount + "/" + MAX_RETRIES + ")...");
            plugin.getServer().getScheduler().runTaskLater(plugin, this::delayedInit, RETRY_DELAY / 50);
        }
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("discord.enabled", false) &&
                plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV");

        if (enabled) {
            try {
                if (DiscordSRV.getPlugin() != null &&
                        DiscordSRV.getPlugin().getJda() != null &&
                        DiscordSRV.getPlugin().getJda().getStatus() == JDA.Status.CONNECTED) {

                    channelId = config.getString("discord.channels.announcements");
                    validateChannels();
                    initialized = true;
                    plugin.getLogger().info("Successfully initialized Discord integration");
                } else {
                    plugin.getLogger().info("DiscordSRV not fully initialized yet...");
                    initialized = false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize DiscordSRV integration: " + e.getMessage());
                enabled = false;
                initialized = false;
            }
        }
    }

    private void validateChannels() {
        if (channelId == null) {
            plugin.getLogger().warning("No announcement channel ID set in config!");
        }
    }

    public TextChannel getAnnouncementChannel() {
        if (!enabled || !initialized) return null;
        return getChannel(channelId);
    }

    private TextChannel getChannel(String channelId) {
        if (!enabled || !initialized || channelId == null) return null;
        try {
            return DiscordSRV.getPlugin().getJda().getTextChannelById(channelId);
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting Discord channel: " + e.getMessage());
            return null;
        }
    }

    public void broadcastAnnouncement(String title, String description, Color color) {
        if (!enabled || !initialized) return;
        try {
            TextChannel channel = getAnnouncementChannel();
            if (channel != null) {
                MessageEmbed embed = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(color)
                        .build();

                channel.sendMessageEmbeds(embed).queue(
                        success -> plugin.getLogger().info("Sent embed announcement to Discord"),
                        error -> plugin.getLogger().warning("Failed to send Discord embed: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Discord announcement: " + e.getMessage());
        }
    }

    public void sendEmbed(MessageEmbed embed) {
        if (!enabled || !initialized) return;
        try {
            TextChannel channel = getAnnouncementChannel();
            if (channel != null) {
                channel.sendMessageEmbeds(embed).queue(
                        success -> plugin.getLogger().info("Sent embed to Discord"),
                        error -> plugin.getLogger().warning("Failed to send Discord embed: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Discord embed: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled && initialized;
    }
}