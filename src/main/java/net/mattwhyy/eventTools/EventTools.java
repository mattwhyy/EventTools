package net.mattwhyy.eventTools;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class EventTools extends JavaPlugin implements Listener {

    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> disconnectedPlayers = new HashSet<>();
    private Location spawnLocation;
    private boolean eventActive = false;
    private boolean chatMuted = false;
    private boolean freezeAll = false;
    private boolean numberGuessActive = false;
    private int targetNumber;
    private UUID numberGuessWinner = null;
    private boolean voteInProgress = false;
    private Map<UUID, Boolean> votes = new HashMap<>();
    private String currentVoteQuestion;
    private BukkitTask voteTask;
    private int voteTimeRemaining;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        getLogger().info("EventTools has been enabled!");
        getCommand("eliminate").setExecutor(this);
        getCommand("revive").setExecutor(this);
        getCommand("seteventspawn").setExecutor(this);
        getCommand("startevent").setExecutor(this);
        getCommand("stopevent").setExecutor(this);
        getCommand("bring").setExecutor(this);
        getCommand("heal").setExecutor(this);
        getCommand("list").setExecutor(this);
        getCommand("mutechat").setExecutor(this);
        getCommand("clearchat").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("EventTools has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, config.getString("messages.no-permission", "&cNo permission"));
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "seteventspawn":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, "&cOnly players can set spawn!");
                    return true;
                }
                spawnLocation = ((Player) sender).getLocation();
                sendMessage(sender, "&aSpawn set at your location!");
                return true;

            case "startevent":
                if (eventActive) {
                    sendMessage(sender, "&cEvent is already running!");
                    return true;
                }
                eventActive = true;
                String startTitle = config.getString("messages.event-start-title", "§6Event started!");
                String startSubtitle = config.getString("messages.event-start-subtitle", "§eGood luck!");
                Bukkit.getOnlinePlayers().forEach(player -> {
                    player.sendTitle(startTitle, startSubtitle, 10, 70, 20);
                });
                broadcastMessage(config.getString("messages.event-started", "&6&lEVENT STARTED! &eEliminations are now active."));
                return true;

            case "stopevent":
                if (!eventActive) {
                    sendMessage(sender, "&cNo event is currently running!");
                    return true;
                }
                eventActive = false;
                String endTitle = config.getString("messages.event-end-title", "§aEvent ended!");
                String endSubtitle = config.getString("messages.event-end-subtitle", "§7Thanks for playing!");
                Bukkit.getOnlinePlayers().forEach(player -> {
                    player.sendTitle(endTitle, endSubtitle, 10, 70, 20);
                });
                broadcastMessage(config.getString("messages.event-ended", "&a&lEVENT ENDED!"));
                return true;

            case "bring":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /bring <player|all|alive|eliminated>");
                    return true;
                }

                int brought = 0;
                Player bringTarget;
                switch (args[0].toLowerCase()) {
                    case "all":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender)) {
                                onlinePlayer.teleport(((Player) sender).getLocation());
                                sendMessage(onlinePlayer, "&aYou were brought to " + sender.getName());
                                brought++;
                            }
                        }
                        sendMessage(sender, "&aBrought " + brought + " players to you!");
                        return true;

                    case "alive":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && !eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.teleport(((Player) sender).getLocation());
                                sendMessage(onlinePlayer, "&aYou were brought to " + sender.getName());
                                brought++;
                            }
                        }
                        sendMessage(sender, "&aBrought " + brought + " alive players to you!");
                        return true;

                    case "eliminated":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.teleport(((Player) sender).getLocation());
                                sendMessage(onlinePlayer, "&aYou were brought to " + sender.getName());
                                brought++;
                            }
                        }
                        sendMessage(sender, "&aBrought " + brought + " eliminated players to you!");
                        return true;

                    default:
                        bringTarget = Bukkit.getPlayer(args[0]);
                        if (bringTarget == null) {
                            sendMessage(sender, "&cPlayer not found!");
                            return true;
                        }
                        bringTarget.teleport(((Player) sender).getLocation());
                        sendMessage(sender, "&aBrought " + bringTarget.getName() + " to you!");
                        sendMessage(bringTarget, "&aYou were brought to " + sender.getName());
                        return true;
                }

            case "heal":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /heal <player|all|alive|eliminated>");
                    return true;
                }

                int healed = 0;
                Player healTarget;
                switch (args[0].toLowerCase()) {
                    case "all":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender)) {
                                healPlayer(onlinePlayer);
                                sendMessage(onlinePlayer, "&aYou have been healed!");
                                healed++;
                            }
                        }
                        sendMessage(sender, "&aHealed " + healed + " players!");
                        return true;

                    case "alive":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && !eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                healPlayer(onlinePlayer);
                                sendMessage(onlinePlayer, "&aYou have been healed!");
                                healed++;
                            }
                        }
                        sendMessage(sender, "&aHealed " + healed + " alive players!");
                        return true;

                    case "eliminated":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                healPlayer(onlinePlayer);
                                sendMessage(onlinePlayer, "&aYou have been healed!");
                                healed++;
                            }
                        }
                        sendMessage(sender, "&aHealed " + healed + " eliminated players!");
                        return true;

                    default:
                        healTarget = Bukkit.getPlayer(args[0]);
                        if (healTarget == null) {
                            sendMessage(sender, "&cPlayer not found!");
                            return true;
                        }
                        healPlayer(healTarget);
                        sendMessage(sender, "&aHealed " + healTarget.getName() + "!");
                        sendMessage(healTarget, "&aYou have been healed!");
                        return true;
                }

            case "giveitem":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, "&cOnly players can use this command!");
                    return true;
                }
                if (args.length < 1) {
                    sendMessage(sender, "&cUsage: /giveitem <player|all|alive|eliminated> [amount]");
                    return true;
                }

                Player givingPlayer = (Player) sender;
                ItemStack item = givingPlayer.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR) {
                    sendMessage(sender, "&cYou must be holding an item!");
                    return true;
                }

                int amount = 1;
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sendMessage(sender, "&cInvalid amount!");
                        return true;
                    }
                }

                ItemStack toGive = item.clone();
                toGive.setAmount(amount);

                int given = 0;
                switch (args[0].toLowerCase()) {
                    case "all":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender)) {
                                onlinePlayer.getInventory().addItem(toGive.clone());
                                sendMessage(onlinePlayer, "&aYou received an item from " + sender.getName());
                                given++;
                            }
                        }
                        sendMessage(sender, "&aGave item to " + given + " players!");
                        return true;

                    case "alive":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && !eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.getInventory().addItem(toGive.clone());
                                sendMessage(onlinePlayer, "&aYou received an item from " + sender.getName());
                                given++;
                            }
                        }
                        sendMessage(sender, "&aGave item to " + given + " alive players!");
                        return true;

                    case "eliminated":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.getInventory().addItem(toGive.clone());
                                sendMessage(onlinePlayer, "&aYou received an item from " + sender.getName());
                                given++;
                            }
                        }
                        sendMessage(sender, "&aGave item to " + given + " eliminated players!");
                        return true;

                    default:
                        Player giveTarget = Bukkit.getPlayer(args[0]);
                        if (giveTarget == null) {
                            sendMessage(sender, "&cPlayer not found!");
                            return true;
                        }
                        giveTarget.getInventory().addItem(toGive.clone());
                        sendMessage(sender, "&aGave item to " + giveTarget.getName() + "!");
                        sendMessage(giveTarget, "&aYou received an item from " + sender.getName());
                        return true;
                }

            case "clearinventory":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /clearinventory <player|all|alive|eliminated>");
                    return true;
                }

                int cleared = 0;
                Player clearTarget;
                switch (args[0].toLowerCase()) {
                    case "all":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender)) {
                                onlinePlayer.getInventory().clear();
                                sendMessage(onlinePlayer, "&cYour inventory was cleared!");
                                cleared++;
                            }
                        }
                        sendMessage(sender, "&aCleared inventory of " + cleared + " players!");
                        return true;

                    case "alive":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && !eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.getInventory().clear();
                                sendMessage(onlinePlayer, "&cYour inventory was cleared!");
                                cleared++;
                            }
                        }
                        sendMessage(sender, "&aCleared inventory of " + cleared + " alive players!");
                        return true;

                    case "eliminated":
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (!onlinePlayer.equals(sender) && eliminatedPlayers.contains(onlinePlayer.getUniqueId())) {
                                onlinePlayer.getInventory().clear();
                                sendMessage(onlinePlayer, "&cYour inventory was cleared!");
                                cleared++;
                            }
                        }
                        sendMessage(sender, "&aCleared inventory of " + cleared + " eliminated players!");
                        return true;

                    default:
                        clearTarget = Bukkit.getPlayer(args[0]);
                        if (clearTarget == null) {
                            sendMessage(sender, "&cPlayer not found!");
                            return true;
                        }
                        clearTarget.getInventory().clear();
                        sendMessage(sender, "&aCleared inventory of " + clearTarget.getName() + "!");
                        sendMessage(clearTarget, "&cYour inventory was cleared!");
                        return true;
                }

            case "eliminate":
                if (!eventActive) {
                    sendMessage(sender, "&cNo event is currently running!");
                    return true;
                }
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /eliminate <player|all>");
                    return true;
                }

                if (args[0].equalsIgnoreCase("all")) {
                    int count = 0;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.hasPermission("eventtools.bypass") && eliminatePlayer(player)) {
                            count++;
                        }
                    }
                    broadcastMessage("&c✖ &f" + count + " players have been eliminated!");
                    sendMessage(sender, "&aEliminated " + count + " players!");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sendMessage(sender, "&cPlayer not found!");
                    return true;
                }
                if (target.hasPermission("eventtools.bypass")) {
                    sendMessage(sender, "&cYou can't eliminate this player!");
                    return true;
                }
                if (eliminatePlayer(target)) {
                    broadcastMessage("&c✖ &f" + target.getName() + " has been eliminated!");
                } else {
                    sendMessage(sender, "&c" + target.getName() + " is already eliminated!");
                }
                return true;

            case "revive":
                if (!eventActive) {
                    sendMessage(sender, "&cNo event is currently running!");
                    return true;
                }
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /revive <player|all>");
                    return true;
                }

                if (args[0].equalsIgnoreCase("all")) {
                    int count = 0;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (revivePlayer(player)) {
                            count++;
                        }
                    }
                    broadcastMessage("&a✔ &f" + count + " players have been revived!");
                    sendMessage(sender, "&aRevived " + count + " players!");
                    return true;
                }

                Player reviveTarget = Bukkit.getPlayer(args[0]);
                if (reviveTarget == null) {
                    sendMessage(sender, "&cPlayer not found!");
                    return true;
                }
                if (revivePlayer(reviveTarget)) {
                    broadcastMessage("&a✔ &f" + reviveTarget.getName() + " has been revived!");
                } else {
                    sendMessage(sender, "&c" + reviveTarget.getName() + " isn't eliminated!");
                }
                return true;

            case "list":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /list <alive|eliminated|all>");
                    return true;
                }

                StringBuilder list = new StringBuilder();
                switch (args[0].toLowerCase()) {
                    case "alive":
                        list.append("&aAlive Players:\n");
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> !p.hasPermission("eventtools.bypass"))
                                .filter(p -> !eliminatedPlayers.contains(p.getUniqueId()))
                                .forEach(p -> list.append("&7- ").append(p.getName()).append("\n"));
                        break;
                    case "eliminated":
                        list.append("&cEliminated Players:\n");
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> !p.hasPermission("eventtools.bypass"))
                                .filter(p -> eliminatedPlayers.contains(p.getUniqueId()))
                                .forEach(p -> list.append("&7- ").append(p.getName()).append("\n"));
                        break;
                    case "all":
                        list.append("&6All Players:\n");
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> !p.hasPermission("eventtools.bypass"))
                                .forEach(p -> {
                                    if (eliminatedPlayers.contains(p.getUniqueId())) {
                                        list.append("&c✖ ").append(p.getName()).append("\n");
                                    } else {
                                        list.append("&a✔ ").append(p.getName()).append("\n");
                                    }
                                });
                        break;
                    default:
                        sendMessage(sender, "&cUsage: /list <alive|eliminated|all>");
                        return true;
                }
                sendMessage(sender, list.toString());
                return true;

            case "freeze":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /freeze <player|all|alive|eliminated>");
                    return true;
                }

                int frozen = 0;
                switch (args[0].toLowerCase()) {
                    case "all":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.equals(sender)) {
                                freezePlayer(p, true);
                                frozen++;
                            }
                        }
                        sendMessage(sender, "&aFroze " + frozen + " players!");
                        break;

                    case "alive":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.equals(sender) && !eliminatedPlayers.contains(p.getUniqueId())) {
                                freezePlayer(p, true);
                                frozen++;
                            }
                        }
                        sendMessage(sender, "&aFroze " + frozen + " alive players!");
                        break;

                    case "eliminated":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.equals(sender) && eliminatedPlayers.contains(p.getUniqueId())) {
                                freezePlayer(p, true);
                                frozen++;
                            }
                        }
                        sendMessage(sender, "&aFroze " + frozen + " eliminated players!");
                        break;

                    default:
                        Player freezeTarget = Bukkit.getPlayer(args[0]);
                        if (freezeTarget == null || freezeTarget.equals(sender)) {
                            sendMessage(sender, "&cInvalid player!");
                            return true;
                        }
                        freezePlayer(freezeTarget, true);
                        sendMessage(sender, "&aFroze " + freezeTarget.getName() + "!");
                }
                return true;

            case "timedeffect":
                if (args.length < 3) {
                    sendMessage(sender, "&cUsage: /timedeffect <effect> <duration> <player|all|alive|eliminated>");
                    return true;
                }

                try {
                    PotionEffectType type = PotionEffectType.getByName(args[0].toUpperCase());
                    if (type == null) throw new IllegalArgumentException();

                    int duration = Integer.parseInt(args[1]) * 20;
                    int applied = 0;

                    switch (args[2].toLowerCase()) {
                        case "all":
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!p.equals(sender)) {
                                    p.addPotionEffect(new PotionEffect(type, duration, 1));
                                    applied++;
                                }
                            }
                            break;

                        case "alive":
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!p.equals(sender) && !eliminatedPlayers.contains(p.getUniqueId())) {
                                    p.addPotionEffect(new PotionEffect(type, duration, 1));
                                    applied++;
                                }
                            }
                            break;

                        case "eliminated":
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!p.equals(sender) && eliminatedPlayers.contains(p.getUniqueId())) {
                                    p.addPotionEffect(new PotionEffect(type, duration, 1));
                                    applied++;
                                }
                            }
                            break;

                        default:
                            Player effectTarget = Bukkit.getPlayer(args[2]);
                            if (effectTarget == null || effectTarget.equals(sender)) {
                                sendMessage(sender, "&cInvalid player!");
                                return true;
                            }
                            effectTarget.addPotionEffect(new PotionEffect(type, duration, 1));
                            applied = 1;
                    }

                    sendMessage(sender, "&aApplied " + type.getName() + " to " + applied + " players!");
                } catch (Exception e) {
                    sendMessage(sender, "&cInvalid effect or duration! Example: /timedeffect speed 30 all");
                }
                return true;

            case "startvote":
                if (voteInProgress) {
                    sendMessage(sender, "&cA vote is already in progress!");
                    return true;
                }

                if (args.length < 1) {
                    sendMessage(sender, "&cUsage: /startvote <question>");
                    return true;
                }

                currentVoteQuestion = String.join(" ", args);
                votes.clear();
                voteInProgress = true;
                voteTimeRemaining = 30;

                broadcastMessage("&6&lVOTE STARTED: &e" + currentVoteQuestion);
                broadcastMessage("&aType &2YES &aor &cNO &ain chat to vote!");
                broadcastMessage("&7Vote ends in 30 seconds!");

                voteTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        voteTimeRemaining--;

                        if (voteTimeRemaining == 15 || voteTimeRemaining == 5) {
                            broadcastMessage("&7" + voteTimeRemaining + " seconds remaining to vote!");
                        }

                        if (voteTimeRemaining <= 0) {
                            endVote();
                            cancel();
                        }
                    }
                }.runTaskTimer(this, 20L, 20L);

                return true;

            case "endvote":
                if (!voteInProgress) {
                    sendMessage(sender, "&cNo vote is currently running!");
                    return true;
                }
                endVote();
                sendMessage(sender, "&aVote ended manually!");
                return true;

            case "countdown":
                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /countdown <seconds>");
                    return true;
                }

                try {
                    int seconds = Integer.parseInt(args[0]);
                    new BukkitRunnable() {
                        int timeLeft = seconds;

                        @Override
                        public void run() {
                            if (timeLeft <= 0) {
                                cancel();
                                return;
                            }

                            if (timeLeft <= 5 || timeLeft % 10 == 0) {
                                Bukkit.getOnlinePlayers().forEach(p ->
                                        p.sendTitle("§e" + timeLeft, "", 5, 20, 5));
                                Bukkit.getOnlinePlayers().forEach(p ->
                                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1));
                            }

                            timeLeft--;
                        }
                    }.runTaskTimer(this, 0, 20);
                } catch (NumberFormatException e) {
                    sendMessage(sender, "&cInvalid number!");
                }
                return true;

            case "numberguess":
                if (numberGuessActive) {
                    sendMessage(sender, "&cA number guess game is already active!");
                    return true;
                }

                if (args.length != 1) {
                    sendMessage(sender, "&cUsage: /numberguess <maxNumber>");
                    return true;
                }

                try {
                    int max = Integer.parseInt(args[0]);
                    targetNumber = new Random().nextInt(max) + 1;
                    numberGuessActive = true;
                    numberGuessWinner = null;

                    broadcastMessage("&eGuess a number between &a1 &eand &a" + max + "&e!");
                    broadcastMessage("&7First to type the correct number wins!");
                } catch (NumberFormatException e) {
                    sendMessage(sender, "&cInvalid number!");
                }
                return true;

            case "mutechat":
                chatMuted = !chatMuted;
                broadcastMessage(chatMuted ? "&cChat has been muted!" : "&aChat has been unmuted!");
                sendMessage(sender, chatMuted ? "&aChat muted!" : "&cChat unmuted!");
                return true;

            case "clearchat":
                for (int i = 0; i < 100; i++) {
                    Bukkit.broadcastMessage("");
                }
                broadcastMessage("&7Chat has been cleared by " + sender.getName());
                return true;
            default:
                return false;
        }
    }

    private void endVote() {
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

    private void healPlayer(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));
    }

    private boolean eliminatePlayer(Player player) {
        if (player.hasPermission("eventtools.bypass")) {
            return false;
        }
        if (eliminatedPlayers.add(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            sendMessage(player, "&cYou've been eliminated!");
            return true;
        }
        return false;
    }

    private boolean revivePlayer(Player player) {
        if (player.hasPermission("eventtools.bypass")) {
            return false;
        }
        if (eliminatedPlayers.remove(player.getUniqueId())) {
            player.setGameMode(GameMode.SURVIVAL);
            sendMessage(player, "&aYou've been revived!");
            if (spawnLocation != null) {
                player.teleport(spawnLocation);
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("eventtools.bypass")) {
            return;
        }
        if (eventActive && disconnectedPlayers.remove(player.getUniqueId())) {
            eliminatePlayer(player);
            player.sendMessage(ChatColor.RED + "You were eliminated for disconnecting!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("eventtools.bypass")) {
            return;
        }
        if (eventActive && eliminatedPlayers.remove(player.getUniqueId())) {
            disconnectedPlayers.add(player.getUniqueId());
        }
    }

    private void freezePlayer(Player player, boolean freeze) {
        player.setWalkSpeed(freeze ? 0 : 0.2f);
        player.setFlySpeed(freeze ? 0 : 0.1f);
        player.setInvulnerable(freeze);
        sendMessage(player, freeze ? "&cYou have been frozen!" : "&aYou have been unfrozen!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!eventActive) return;

        Player player = event.getEntity();
        if (player.hasPermission("eventtools.bypass")) {
            return;
        }

        if (eliminatedPlayers.add(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (chatMuted && !event.getPlayer().hasPermission("eventtools.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently muted!");
        }
        if (!chatMuted && numberGuessActive && numberGuessWinner == null) {
            try {
                int guess = Integer.parseInt(event.getMessage());
                if (guess == targetNumber) {
                    numberGuessWinner = event.getPlayer().getUniqueId();
                    broadcastMessage("&a" + event.getPlayer().getName() + " &6guessed the number &a" + targetNumber + "&6!");
                    broadcastMessage("&eThey are the winner!");
                    numberGuessActive = false;
                    event.setCancelled(true);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (!chatMuted && voteInProgress) {
            String message = event.getMessage().toLowerCase();
            if (message.equals("yes") || message.equals("y") || message.equals("agree")) {
                votes.put(player.getUniqueId(), true);
                sendMessage(player, "&aYour YES vote has been counted!");
                event.setCancelled(true);
            }
            else if (message.equals("no") || message.equals("n") || message.equals("disagree")) {
                votes.put(player.getUniqueId(), false);
                sendMessage(player, "&cYour NO vote has been counted!");
                event.setCancelled(true);
            }
        }
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void broadcastMessage(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}