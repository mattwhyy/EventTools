package net.mattwhyy.eventTools;

import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import net.mattwhyy.eventTools.commands.CommandManager;
import net.mattwhyy.eventTools.teams.Team;
import net.mattwhyy.eventTools.teams.TeamManager;
import net.mattwhyy.eventTools.zones.ZoneManager;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class EventTools extends JavaPlugin implements Listener {

    private PlaceholderAPIManager papiManager;
    private DiscordSRVManager discordManager;
    public Chat chat = null;

    public final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    public final List<UUID> eliminationOrder = Collections.synchronizedList(new ArrayList<>());
    public final Map<UUID, Boolean> votes = new ConcurrentHashMap<>();
    public final Map<String, BukkitTask> activeResizeTasks = new ConcurrentHashMap<>();

    public volatile String eventTitle = "Event";
    public volatile EventType eventType;
    public long eventStartTime;
    public volatile boolean eventActive = false;

    public Location spawnLocation;

    public volatile boolean chatMuted = false;

    public volatile boolean numberGuessActive = false;
    public volatile UUID numberGuessWinner = null;
    public volatile int targetNumber;

    public volatile boolean voteInProgress = false;
    public volatile String currentVoteQuestion;
    public volatile BukkitTask voteTask;
    public volatile int voteTimeRemaining;

    private CommandManager commandManager;
    public ZoneManager zoneManager;
    public TeamManager teamManager;

    public enum EventType {
        PURE_FFA,
        HYBRID_FFA,
        TEAM_BATTLE
    }

    private FileConfiguration config;

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            config = getConfig();
        } else {
            getConfig().options().copyDefaults(true);
            saveConfig();
            config = getConfig();
        }

        getLogger().info("EventTools has been enabled");

        this.commandManager = new CommandManager(this);
        registerCommandExecutors();

        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("evt_"))
                .forEach(org.bukkit.scoreboard.Team::unregister);

        getServer().getPluginManager().registerEvents(this, this);
        this.zoneManager = new ZoneManager(this);
        this.teamManager = new TeamManager(this);

        this.zoneManager.startParticleRenderer();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiManager = new PlaceholderAPIManager(this);
            this.papiManager.register();
            getLogger().info("Hooked into PlaceHolderAPI");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            setupChat();
            getLogger().info("Hooked into Vault");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            this.discordManager = new DiscordSRVManager(this);
            getLogger().info("Hooked into DiscordSRV");
        }
    }

    private void registerCommandExecutors() {
        getCommand("startevent").setExecutor(commandManager);
        getCommand("stopevent").setExecutor(commandManager);
        getCommand("seteventspawn").setExecutor(commandManager);
        getCommand("eliminate").setExecutor(commandManager);
        getCommand("revive").setExecutor(commandManager);
        getCommand("bring").setExecutor(commandManager);
        getCommand("heal").setExecutor(commandManager);
        getCommand("freeze").setExecutor(commandManager);
        getCommand("giveitem").setExecutor(commandManager);
        getCommand("clearinventory").setExecutor(commandManager);
        getCommand("invsee").setExecutor(commandManager);
        getCommand("timedeffect").setExecutor(commandManager);
        getCommand("changegamemode").setExecutor(commandManager);
        getCommand("kit").setExecutor(commandManager);
        getCommand("startvote").setExecutor(commandManager);
        getCommand("endvote").setExecutor(commandManager);
        getCommand("countdown").setExecutor(commandManager);
        getCommand("numberguess").setExecutor(commandManager);
        getCommand("mutechat").setExecutor(commandManager);
        getCommand("clearchat").setExecutor(commandManager);
        getCommand("broadcast").setExecutor(commandManager);
        getCommand("list").setExecutor(commandManager);
        getCommand("zone").setExecutor(commandManager);
        getCommand("team").setExecutor(commandManager);
    }

    @Override
    public void onDisable() {
        cleanupTasks();
        getLogger().info("EventTools has been disabled");
    }

    private void setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp == null) {
            return;
        }
        chat = rsp.getProvider();
    }

    public DiscordSRVManager getDiscordManager() {
        return discordManager;
    }

    public List<Player> getTargetPlayers(CommandSender sender, String target) {
        List<Player> targets = new ArrayList<>();

        if (target.startsWith("@")) {
            String teamName = target.substring(1);
            Team team = teamManager.getTeam(teamName);
            if (team != null) {
                targets.addAll(team.getOnlineMembers());
            }
        } else {
            switch (target.toLowerCase()) {
                case "all":
                    targets.addAll(Bukkit.getOnlinePlayers());
                    break;
                case "alive":
                    targets.addAll(getNonBypassPlayers().stream()
                            .filter(p -> !isEliminated(p))
                            .collect(Collectors.toList()));
                    break;
                case "eliminated":
                    targets.addAll(getNonBypassPlayers().stream()
                            .filter(this::isEliminated)
                            .collect(Collectors.toList()));
                    break;
                default:
                    Player player = Bukkit.getPlayer(target);
                    if (player != null) targets.add(player);
            }
        }

        if (sender instanceof Player) {
            targets.remove(sender);
        }

        targets.removeIf(p -> p.hasPermission("eventtools.bypass"));

        return targets;
    }

    public List<Player> getNonBypassPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());
    }

    public List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    public int eliminateAllPlayers() {
        int count = 0;
        List<Player> toEliminate = getNonBypassPlayers().stream()
                .filter(p -> !isEliminated(p))
                .collect(Collectors.toList());

        for (Player player : toEliminate) {
            handleElimination(player);
            count++;
        }
        return count;
    }

    public int eliminateTeam(String teamName) {
        Team team = teamManager.getTeam(teamName);
        if (team == null) return 0;

        int count = 0;
        for (Player player : team.getOnlineMembers()) {
            if (!player.hasPermission("eventtools.bypass") && !isEliminated(player)) {
                handleElimination(player);
                count++;
            }
        }
        return count;
    }

    public int reviveAllPlayers(CommandSender sender) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("eventtools.bypass") && isEliminated(player)) {
                revivePlayer(sender, player);
                count++;
            }
        }
        return count;
    }

    public int reviveTeam(String teamName, CommandSender sender) {
        Team team = teamManager.getTeam(teamName);
        if (team == null) return 0;

        int count = 0;
        for (Player player : team.getOnlineMembers()) {
            if (!player.hasPermission("eventtools.bypass") && isEliminated(player)) {
                revivePlayer(sender, player);
                count++;
            }
        }
        return count;
    }

    private void cleanupTasks() {
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }
        if (this.papiManager != null) {
            this.papiManager.unregister();
        }
        if (zoneManager != null) {
            zoneManager.shutdown();
        }
        activeResizeTasks.values().forEach(BukkitTask::cancel);
        activeResizeTasks.clear();
    }

    public void cancelResizeTask(String zoneName) {
        BukkitTask task = activeResizeTasks.get(zoneName);
        if (task != null) {
            task.cancel();
            activeResizeTasks.remove(zoneName);
        }
    }

    private int parseInt(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public boolean isEliminated(Player player) {
        return isEliminated(player.getUniqueId());
    }

    public void resetEvent() {
        eliminatedPlayers.clear();
        eliminationOrder.clear();
        votes.clear();

        eventActive = false;
        chatMuted = false;
        numberGuessActive = false;
        numberGuessWinner = null;
        voteInProgress = false;
        currentVoteQuestion = null;

        if (!teamManager.getTeamNames().isEmpty()) {
            teamManager.getAllTeams().forEach(team -> {
                team.getMembers().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(player -> {
                            player.setDisplayName(null);
                            player.setPlayerListName(null);
                            player.setCustomName(null);
                        });
            });

            new ArrayList<>(teamManager.getTeamNames()).forEach(teamManager::deleteTeam);
        }

        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("eventtools.bypass")) continue;

            player.setGameMode(GameMode.SURVIVAL);
            clearPotionEffects(player);

            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setInvulnerable(false);

            healPlayer(player);

            if (spawnLocation != null) {
                safeTeleport(player, spawnLocation);
            }
        }

        targetNumber = 0;
    }

    public void endVote() {
        voteInProgress = false;
        if (voteTask != null) {
            voteTask.cancel();
        }

        int yesVotes = (int) votes.values().stream().filter(v -> v).count();
        int noVotes = votes.size() - yesVotes;
        int totalPlayers = Bukkit.getOnlinePlayers().size();

        double yesPercent = votes.isEmpty() ? 0 : (yesVotes * 100.0) / votes.size();
        double noPercent = votes.isEmpty() ? 0 : (noVotes * 100.0) / votes.size();

        broadcastMessage("&6&lVOTE RESULTS: &e" + currentVoteQuestion);
        broadcastMessage(String.format("&aYES: &2%d (%.1f%%)", yesVotes, yesPercent));
        broadcastMessage(String.format("&cNO: &4%d (%.1f%%)", noVotes, noPercent));
        broadcastMessage("&7Total voters: " + votes.size() + "/" + totalPlayers);

        votes.clear();
        currentVoteQuestion = null;
    }

    public void healPlayer(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        clearPotionEffects(player);
    }

    private void clearPotionEffects(Player player) {
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));
    }

    public void giveKit(Player player, String kitName) {
        ConfigurationSection kitSection = config.getConfigurationSection("kits." + kitName);
        if (kitSection == null) return;

        if (kitSection.getBoolean("clear-inventory", false)) {
            player.getInventory().clear();
        }

        if (kitSection.contains("armor")) {
            ConfigurationSection armorSection = kitSection.getConfigurationSection("armor");
            if (armorSection != null) {
                ItemStack helmet = getItemFromConfig(armorSection, "helmet");
                ItemStack chestplate = getItemFromConfig(armorSection, "chestplate");
                ItemStack leggings = getItemFromConfig(armorSection, "leggings");
                ItemStack boots = getItemFromConfig(armorSection, "boots");

                if (helmet != null) player.getInventory().setHelmet(helmet);
                if (chestplate != null) player.getInventory().setChestplate(chestplate);
                if (leggings != null) player.getInventory().setLeggings(leggings);
                if (boots != null) player.getInventory().setBoots(boots);
            }
        }

        if (kitSection.contains("items")) {
            List<ItemStack> items = kitSection.getStringList("items").stream()
                    .map(this::getItemFromString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            for (ItemStack item : items) {
                player.getInventory().addItem(item);
            }
        }

        if (kitSection.contains("effects")) {
            for (String effectKey : kitSection.getConfigurationSection("effects").getKeys(false)) {
                PotionEffectType type = PotionEffectType.getByName(effectKey.toUpperCase());
                if (type != null) {
                    int duration = kitSection.getInt("effects." + effectKey + ".duration", 200) * 20;
                    int amplifier = kitSection.getInt("effects." + effectKey + ".amplifier", 0);
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            }
        }

        if (kitSection.getBoolean("heal", false)) {
            healPlayer(player);
        }
    }

    public boolean eliminatePlayer(Player player) {
        if (player.hasPermission("eventtools.bypass") || isEliminated(player)) {
            return false;
        }

        String gamemodeName = config.getString("settings.elimination-gamemode", "SURVIVAL");
        GameMode eliminationMode;
        try {
            eliminationMode = GameMode.valueOf(gamemodeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid gamemode in config! Using SURVIVAL as fallback.");
            eliminationMode = GameMode.SURVIVAL;
        }

        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation(), 1);
        broadcastSound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.1f);
        eliminatedPlayers.add(player.getUniqueId());
        eliminationOrder.add(player.getUniqueId());
        player.setGameMode(eliminationMode);
        return true;
    }

    public void handleElimination(Player player) {
        if (!eliminatePlayer(player)) return;

        Optional<Team> team = teamManager.getPlayerTeam(player);

        if (team.isPresent()) {
            Team playerTeam = team.get();
            if (!teamManager.isTeamActive(playerTeam)) {
                teamManager.markTeamEliminated(playerTeam);
                broadcastMessage("&c☠ " + playerTeam.getColor() + playerTeam.getName() + " &chas been fully eliminated!");
            }
            broadcastMessage(playerTeam.getColor() + playerTeam.getName() +
                    " &8&l>&r &c☠ " + player.getName() +  " &chas been eliminated!");
        } else {
            broadcastMessage("&c☠ " + player.getName() + " &chas been eliminated!");
        }

        checkForEventEnd();
    }

    public void handleMidEventJoin(Player player) {
        if (!eliminatePlayer(player)) return;

        Optional<Team> team = teamManager.getPlayerTeam(player);

        if (team.isPresent()) {
            Team playerTeam = team.get();
            if (!teamManager.isTeamActive(playerTeam)) {
                teamManager.markTeamEliminated(playerTeam);
                broadcastMessage("&c☠ " + playerTeam.getColor() + playerTeam.getName() + " &chas been fully eliminated because a member joined mid-event!");
            }
            broadcastMessage(playerTeam.getColor() + playerTeam.getName() +
                    " &8&l>&r &c☠ " + player.getName() + " &cwas eliminated for joining mid-event!");
        } else {
            broadcastMessage("&c☠ " + player.getName() + " &cwas eliminated for joining mid-event!");
        }

        checkForEventEnd();
    }

    public void checkForEventEnd() {
        if (eventType == null) return;
        switch (eventType) {
            case TEAM_BATTLE:
                teamManager.checkForTeamVictory();
                break;

            case HYBRID_FFA:
            case PURE_FFA:
                List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !isEliminated(p))
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .collect(Collectors.toList());

                if (alivePlayers.size() <= 1) {
                    Player winner = alivePlayers.get(0);
                    celebrateVictory(winner, "&6&lWINNER", "&7" + winner.getName());
                    announceFinalPlacements();
                    sendEventStatsEmbed();
                    resetEvent();
                }
                break;
        }
    }

    private void celebrateVictory(Player winner, String victoryTitle, String victorySubtitle) {
        broadcastTitle(victoryTitle, victorySubtitle);
        broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5f);
        broadcastSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);

        Location loc = winner.getLocation();

        new BukkitRunnable() {
            int count = 0;
            Random random = new Random();

            @Override
            public void run() {
                if (count++ >= 15) {
                    cancel();
                    return;
                }

                try {
                    Location fireLoc = loc.clone().add(
                            (random.nextDouble() * 6) - 3,
                            random.nextDouble() * 2,
                            (random.nextDouble() * 6) - 3
                    );

                    Firework fw = fireLoc.getWorld().spawn(fireLoc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();

                    Color[] allowedColors = {
                    Color.AQUA,
                    Color.BLACK,
                    Color.BLUE,
                    Color.TEAL,
                    Color.NAVY,
                    Color.GRAY,
                    Color.GREEN,
                    Color.PURPLE,
                    Color.MAROON,
                    Color.ORANGE,
                    Color.SILVER,
                    Color.LIME,
                    Color.FUCHSIA,
                    Color.RED,
                    Color.WHITE,
                    Color.YELLOW
                    };

                    Color randomColor = allowedColors[random.nextInt(allowedColors.length)];

                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL)
                            .withColor(randomColor)
                            .withFade(Color.WHITE)
                            .trail(true)
                            .flicker(true)
                            .build());

                    meta.setPower(1);
                    fw.setFireworkMeta(meta);

                    broadcastSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1);

                } catch (Exception e) {
                    getLogger().warning("FFA Firework error: " + e.getMessage());
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public boolean revivePlayer(CommandSender sender, Player player) {
        if (player.hasPermission("eventtools.bypass") || !isEliminated(player)) {
            return false;
        }
        eliminatedPlayers.remove(player.getUniqueId());
        eliminationOrder.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);

        Optional<Team> team = teamManager.getPlayerTeam(player);
        if (team.isPresent()) {
            Team playerTeam = team.get();
            if (teamManager.isTeamEliminated(playerTeam)) {
                teamManager.reviveTeam(playerTeam);
                broadcastMessage(playerTeam.getColor() + playerTeam.getName() +
                        " &ahas been revived!");
            }
        }

        if (sender instanceof Player senderPlayer) {
            safeTeleport(player, senderPlayer.getLocation());
        } else if (spawnLocation != null) {
            safeTeleport(player, spawnLocation);
        }

        return true;
    }

    public void freezePlayer(Player player, boolean freeze) {
        player.setWalkSpeed(freeze ? 0 : 0.2f);
        player.setFlySpeed(freeze ? 0 : 0.1f);
        player.setInvulnerable(freeze);
        sendMessage(player, freeze ? "&cYou have been frozen!" : "&aYou have been unfrozen!");
    }

    public void safeTeleport(Player player, Location location) {
        try {
            player.teleport(location);
        } catch (Exception e) {
            getLogger().warning("Failed to teleport player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void announceFinalPlacements() {
        List<UUID> placements = new ArrayList<>();

        Player onlineWinner = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .filter(p -> !isEliminated(p))
                .findFirst()
                .orElse(null);

        if (onlineWinner != null) {
            placements.add(onlineWinner.getUniqueId());
        } else if (!eliminationOrder.isEmpty()) {
            placements.add(eliminationOrder.get(eliminationOrder.size() - 1));
        }

        synchronized (eliminationOrder) {
            for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
                UUID playerId = eliminationOrder.get(i);
                if (!placements.contains(playerId)) {
                    placements.add(playerId);
                    if (placements.size() >= 5) break;
                }
            }
        }

        broadcastMessage("&6&lEvent Results:");
        String[] suffixes = {"1st", "2nd", "3rd", "4th", "5th"};
        String[] colors = {"&6", "&7", "&c", "&f", "&f"};
        String[] icons = {"🥇 ", "🥈 ", "🥉 ", "", ""};


        if (discordManager != null && discordManager.isEnabled()) {
            String winnerColor = config.getString("discord.colors.event-results", "#FFA500");
            String otherPlacementsColor = config.getString("discord.colors.event-other-placements", "#C4C4C4");
            EmbedBuilder top3Embed = new EmbedBuilder()
                    .setTitle(eventTitle + " Results 🏆")
                    .setColor(java.awt.Color.decode(winnerColor));

            for (int i = 0; i < Math.min(3, placements.size()); i++) {
                UUID playerId = placements.get(i);
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                String name = player.getName() != null ? player.getName() : "Unknown";

                String[] medals = {"🥇 **WINNER**", "🥈 Runner-Up", "🥉 Third Place"};
                String[] fieldColors = {"#FFD700", "#C0C0C0", "#CD7F32"};

                top3Embed.addField(
                        medals[i],
                        "**" + name + "**",
                        false
                );
            }

            top3Embed.setFooter("Event ended")
                    .setTimestamp(Instant.now());

            getDiscordManager().sendEmbed(top3Embed.build());

            if (placements.size() > 3) {
                EmbedBuilder remainingEmbed = new EmbedBuilder()
                        .setTitle("Other Placements")
                        .setFooter("Thanks for playing!")
                        .setColor(java.awt.Color.decode(otherPlacementsColor));

                for (int i = 3; i < Math.min(5, placements.size()); i++) {
                    UUID playerId = placements.get(i);
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                    String name = player.getName() != null ? player.getName() : "Unknown";

                    remainingEmbed.addField(
                            (i + 1) + "th Place",
                            name,
                            true
                    );
                }

                getDiscordManager().sendEmbed(remainingEmbed.build());
            }
        }

        for (int i = 0; i < Math.min(5, placements.size()); i++) {
            UUID playerId = placements.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            boolean isOnline = offlinePlayer.isOnline();

            String status = isOnline ? "&7" : "&8";
            String placement = colors[i] + icons[i] + suffixes[i] + ": &r" + status + name;
            broadcastMessage(placement);
        }
    }

    public void broadcastTitle(String title, String subtitle) {
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', title),
                        ChatColor.translateAlternateColorCodes('&', subtitle),
                        10, 70, 20
                )
        );
    }

    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void broadcastMessage(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            playSound(player, sound, volume, pitch);
        }
    }

    private void sendEventStatsEmbed() {
        if (discordManager.isEnabled()) {
            long durationMillis = System.currentTimeMillis() - eventStartTime;
            String duration = String.format("%d min %d sec",
                    TimeUnit.MILLISECONDS.toMinutes(durationMillis),
                    TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
            );

            int participants = eliminationOrder.size() + (eventActive ? 1 : 0);
            String statsColor = config.getString("discord.colors.info", "#0099FF");

            EmbedBuilder statsEmbed = new EmbedBuilder()
                    .setTitle("📊 Event Stats")
                    .setColor(java.awt.Color.decode(statsColor))
                    .addField("Duration", duration, true)
                    .addField("Participants", String.valueOf(participants), true);

            getDiscordManager().sendEmbed(statsEmbed.build());
        }
    }

    private ItemStack getItemFromConfig(ConfigurationSection section, String path) {
        if (!section.contains(path)) return null;
        return getItemFromString(section.getString(path));
    }

    private ItemStack getItemFromString(String itemString) {
        try {
            String[] parts = itemString.split(":");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) return null;

            int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            ItemStack item = new ItemStack(material, amount);

            if (parts.length > 2) {
                item.setDurability(Short.parseShort(parts[2]));
            }

            return item;
        } catch (Exception e) {
            getLogger().warning("Failed to parse item: " + itemString);
            return null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("eventtools.bypass")) return;

        if (eventActive) {
            handleMidEventJoin(player);
            sendMessage(player, "&cYou joined mid-event and were automatically eliminated!");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!eventActive) return;

        Player player = event.getEntity();
        if (player.hasPermission("eventtools.bypass")) return;

        handleElimination(player);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.hasPermission("eventtools.bypass")) return;

            Optional<Team> team = teamManager.getPlayerTeam(player);
            if (team.isPresent()) {
                Team playerTeam = team.get();

                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    if (!playerTeam.isFallDamageEnabled() || playerTeam.isInvulnerable()) {
                        event.setCancelled(true);
                    }
                } else {
                    if (playerTeam.isInvulnerable()) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.hasPermission("eventtools.bypass")) return;

            Optional<Team> team = teamManager.getPlayerTeam(player);
            if (team.isPresent() && !team.get().isHungerDecayEnabled()) {
                if (event.getFoodLevel() < player.getFoodLevel()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!chatMuted || player.hasPermission("eventtools.bypass")) {
            return;
        }

        String command = event.getMessage().split(" ")[0].toLowerCase();

        List<String> blockedCommands = Arrays.asList(
                "/me", "/minecraft:me",
                "/say", "/minecraft:say",
                "/msg", "/minecraft:msg",
                "/tell", "/minecraft:tell",
                "/whisper", "/minecraft:whisper",
                "/w", "/minecraft:w",
                "/pm", "/minecraft:pm",
                "/t", "/minecraft:t",
                "/emote", "/minecraft:emote",
                "/action", "/minecraft:action"
        );

        if (blockedCommands.contains(command)) {
            event.setCancelled(true);
            sendMessage(player, "&cThis command is blocked while the chat is muted!");
            return;
        }

        if (event.getMessage().matches("/(msg|tell|w|me)(?i).*")) {
            event.setCancelled(true);
            sendMessage(player, "&cThis command is blocked while the chat is muted!");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (chatMuted && !player.hasPermission("eventtools.bypass")) {
            event.setCancelled(true);
            sendMessage(player, "&cChat is currently muted!");
            return;
        }

        Optional<Team> team = teamManager.getPlayerTeam(player);
        if (team.isPresent() && team.get().hasTeamChatToggled(player.getUniqueId())) {
            event.setCancelled(true);
            sendTeamChatMessage(player, team.get(), message);
            return;
        }

        synchronized (this) {
            if (numberGuessActive && numberGuessWinner == null) {
                try {
                    int guess = Integer.parseInt(event.getMessage());
                    if (guess == targetNumber) {
                        numberGuessWinner = player.getUniqueId();
                        broadcastMessage("&a" + player.getName() + " &6guessed the number &a" + targetNumber + "&6!");
                        broadcastMessage("&eThey are the winner!");
                        numberGuessActive = false;
                        event.setCancelled(true);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (voteInProgress) {
            String lcmessage = event.getMessage().toLowerCase();
            if (lcmessage.equals("yes") || lcmessage.equals("y") || lcmessage.equals("agree")) {
                votes.put(player.getUniqueId(), true);
                sendMessage(player, "&7Your &aYES &7vote has been counted!");
                playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
                event.setCancelled(true);
            }
            else if (lcmessage.equals("no") || lcmessage.equals("n") || lcmessage.equals("disagree")) {
                votes.put(player.getUniqueId(), false);
                sendMessage(player, "&7Your &cNO &7vote has been counted!");
                playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
                event.setCancelled(true);
            }
        }
    }

    private void sendTeamChatMessage(Player sender, Team team, String message) {
        String prefix = chat.getPlayerPrefix(sender);
        String suffix = chat.getPlayerSuffix(sender);
        String formatted = String.format(team.getColor() + team.getName() +
                " &8&l>&r " + prefix + sender.getDisplayName() + suffix + "&r: " + message
        );

        team.getOnlineMembers().forEach(member ->
                sendMessage(member, formatted)
        );

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("eventtools.bypass"))
                .filter(p -> !team.getMembers().contains(p.getUniqueId()))
                .forEach(staff ->
                        sendMessage(staff,
                                "&8&lSPY&r " + team.getColor() + team.getName() +
                                        " &8&l>&r " + prefix + sender.getName() + suffix + "&r: " + message)
                );

        getLogger().info("[TEAM-CHAT] " + team.getColor() + team.getName() + prefix + sender.getName() + suffix + message);
    }
}