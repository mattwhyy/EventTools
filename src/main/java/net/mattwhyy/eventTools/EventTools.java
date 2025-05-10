package net.mattwhyy.eventTools;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EventTools extends JavaPlugin implements Listener {

    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> disconnectedPlayers = ConcurrentHashMap.newKeySet();
    private final List<UUID> eliminationOrder = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Boolean> votes = new ConcurrentHashMap<>();

    private Location spawnLocation;
    private volatile boolean eventActive = false;
    private volatile boolean chatMuted = false;
    private volatile boolean numberGuessActive = false;
    private volatile int targetNumber;
    private volatile UUID numberGuessWinner = null;
    private volatile boolean voteInProgress = false;
    private volatile String currentVoteQuestion;
    private volatile BukkitTask voteTask;
    private volatile int voteTimeRemaining;

    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        getLogger().info("EventTools has been enabled!");
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void registerCommands() {
        Arrays.asList(
                "eliminate", "revive", "seteventspawn", "startevent", "stopevent",
                "bring", "heal", "list", "mutechat", "clearchat", "freeze",
                "timedeffect", "startvote", "endvote", "countdown", "numberguess",
                "giveitem", "clearinventory"
        ).forEach(cmd -> getCommand(cmd).setExecutor(this));
    }

    @Override
    public void onDisable() {
        cleanupTasks();
        getLogger().info("EventTools has been disabled!");
    }

    private void cleanupTasks() {
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (!sender.hasPermission("eventtools.admin")) {
                sendMessage(sender, config.getString("messages.no-permission", "&cNo permission"));
                return true;
            }

            switch (cmd.getName().toLowerCase()) {
                case "seteventspawn": return handleSetSpawn(sender);
                case "startevent": return handleStartEvent(sender);
                case "stopevent": return handleStopEvent(sender);
                case "bring": return handleBring(sender, args);
                case "heal": return handleHeal(sender, args);
                case "giveitem": return handleGiveItem(sender, args);
                case "clearinventory": return handleClearInventory(sender, args);
                case "eliminate": return handleEliminateCommand(sender, args);
                case "revive": return handleReviveCommand(sender, args);
                case "list": return handleListCommand(sender, args);
                case "freeze": return handleFreeze(sender, args);
                case "timedeffect": return handleTimedEffect(sender, args);
                case "startvote": return handleStartVote(sender, args);
                case "endvote": return handleEndVote(sender);
                case "countdown": return handleCountdown(sender, args);
                case "numberguess": return handleNumberGuess(sender, args);
                case "mutechat": return handleMuteChat(sender);
                case "clearchat": return handleClearChat(sender);
                default: return false;
            }
        } catch (Exception e) {
            getLogger().severe("Command error: " + e.getMessage());
            sendMessage(sender, "&cCommand failed: " + e.getMessage());
            return true;
        }
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can set spawn!");
            return true;
        }
        spawnLocation = ((Player) sender).getLocation();

        sendMessage(sender, "&aSpawn set at your location!");
        return true;
    }

    private boolean handleStartEvent(CommandSender sender) {
        if (eventActive) {
            sendMessage(sender, "&cEvent is already running!");
            return true;
        }

        int eligiblePlayers = getEligiblePlayerCount(sender);
        if (eligiblePlayers < 4) {
            sendMessage(sender, "&cYou need at least 4 players to start!");
            return true;
        }

        resetEvent();
        eventActive = true;
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ENDER_DRAGON_GROWL,
                    1.0f,
                    0.5f
            );
        });
        broadcastTitle(
                config.getString("messages.event-start-title", "§6Event started!"),
                config.getString("messages.event-start-subtitle", "§eGood luck!")
        );
        broadcastMessage(config.getString("messages.event-started", "&6&lEVENT STARTED! &eEliminations are now active."));
        return true;
    }

    private boolean handleStopEvent(CommandSender sender) {
        if (!eventActive) {
            sendMessage(sender, "&cNo event is currently running!");
            return true;
        }

        broadcastTitle(
                config.getString("messages.event-end-title", "§aEvent ended!"),
                config.getString("messages.event-end-subtitle", "§7Thanks for playing!")
        );
        resetEvent();
        broadcastMessage(config.getString("messages.event-ended", "&a&lEVENT ENDED!"));
        return true;
    }

    private boolean handleBring(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /bring <player|all|alive|eliminated>");
            return true;
        }

        Player senderPlayer = (Player) sender;
        int brought = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(senderPlayer)) continue;

            boolean shouldBring = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldBring) {
                safeTeleport(player, senderPlayer.getLocation());
                sendMessage(player, "&aYou were brought to " + sender.getName());
                brought++;
            }
        }

        if (brought == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        sendMessage(sender, String.format("&aBrought %d %s to you!",
                brought,
                target.matches("all|alive|eliminated") ? "players" : "player"));
        return true;
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /heal <player|all|alive|eliminated>");
            return true;
        }

        int healed = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender)) continue;

            boolean shouldHeal = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldHeal) {
                healPlayer(player);
                sendMessage(player, "&aYou have been healed!");
                healed++;
            }
        }

        if (healed == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        sendMessage(sender, String.format("&aHealed %d %s!",
                healed,
                target.matches("all|alive|eliminated") ? "players" : "player"));
        return true;
    }

    private boolean handleGiveItem(CommandSender sender, String[] args) {
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

        int amount = args.length >= 2 ? parseInt(args[1], 1) : 1;
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);

        int given = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(givingPlayer)) continue;

            boolean shouldGive = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldGive) {
                player.getInventory().addItem(toGive.clone());
                sendMessage(player, "&aYou received an item from " + sender.getName());
                given++;
            }
        }

        if (given == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        sendMessage(sender, String.format("&aGave item to %d %s!",
                given,
                target.matches("all|alive|eliminated") ? "players" : "player"));
        return true;
    }

    private boolean handleClearInventory(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /clearinventory <player|all|alive|eliminated>");
            return true;
        }

        int cleared = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender)) continue;

            boolean shouldClear = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldClear) {
                player.getInventory().clear();
                sendMessage(player, "&cYour inventory was cleared!");
                cleared++;
            }
        }

        if (cleared == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        sendMessage(sender, String.format("&aCleared inventory of %d %s!",
                cleared,
                target.matches("all|alive|eliminated") ? "players" : "player"));
        return true;
    }

    private boolean handleEliminateCommand(CommandSender sender, String[] args) {
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
            List<Player> toEliminate = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.hasPermission("eventtools.bypass"))
                    .collect(Collectors.toList());

            for (Player player : toEliminate) {
                if (eliminatePlayer(player)) {
                    count++;
                    broadcastMessage("&c" + player.getName() + " has been eliminated!");
                }
            }

            sendMessage(sender, "&aEliminated " + count + " players!");
            checkForEventEnd();
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
            broadcastMessage("&c" + target.getName() + " has been eliminated!");
            checkForEventEnd();
        } else {
            sendMessage(sender, "&c" + target.getName() + " is already eliminated!");
        }
        return true;
    }

    private boolean handleReviveCommand(CommandSender sender, String[] args) {
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
            broadcastMessage("&a" + count + " players have been revived!");
            sendMessage(sender, "&aRevived " + count + " players!");
            return true;
        }

        Player reviveTarget = Bukkit.getPlayer(args[0]);
        if (reviveTarget == null) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }
        if (revivePlayer(reviveTarget)) {
            broadcastMessage("&a" + reviveTarget.getName() + " has been revived!");
        } else {
            sendMessage(sender, "&c" + reviveTarget.getName() + " isn't eliminated!");
        }
        return true;
    }

    private boolean handleListCommand(CommandSender sender, String[] args) {
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
                        .filter(p -> !isEliminated(p))
                        .forEach(p -> list.append("&7- ").append(p.getName()).append("\n"));
                break;
            case "eliminated":
                list.append("&cEliminated Players:\n");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .filter(this::isEliminated)
                        .forEach(p -> list.append("&7- ").append(p.getName()).append("\n"));
                break;
            case "all":
                list.append("&6All Players:\n");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .forEach(p -> {
                            if (isEliminated(p)) {
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
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /freeze <player|all|alive|eliminated>");
            return true;
        }

        int frozen = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender)) continue;

            boolean shouldFreeze = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldFreeze) {
                freezePlayer(player, true);
                frozen++;
            }
        }

        if (frozen == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        sendMessage(sender, String.format("&aFroze %d %s!",
                frozen,
                target.matches("all|alive|eliminated") ? "players" : "player"));
        return true;
    }

    private boolean handleTimedEffect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /timedeffect <effect> <duration> <player|all|alive|eliminated>");
            return true;
        }

        try {
            PotionEffectType type = PotionEffectType.getByName(args[0].toUpperCase());
            if (type == null) throw new IllegalArgumentException();

            int duration = parseInt(args[1], 1) * 20;
            int applied = 0;
            String target = args[2].toLowerCase();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.equals(sender)) continue;

                boolean shouldApply = switch (target) {
                    case "all" -> true;
                    case "alive" -> !isEliminated(player);
                    case "eliminated" -> isEliminated(player);
                    default -> player.getName().equalsIgnoreCase(args[2]);
                };

                if (shouldApply) {
                    player.addPotionEffect(new PotionEffect(type, duration, 1));
                    applied++;
                }
            }

            if (applied == 0 && !target.matches("all|alive|eliminated")) {
                sendMessage(sender, "&cPlayer not found!");
                return true;
            }

            sendMessage(sender, String.format("&aApplied %s to %d %s!",
                    type.getName(),
                    applied,
                    target.matches("all|alive|eliminated") ? "players" : "player"));
        } catch (Exception e) {
            sendMessage(sender, "&cInvalid effect or duration! Example: /timedeffect speed 30 all");
        }
        return true;
    }

    private boolean handleStartVote(CommandSender sender, String[] args) {
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
    }

    private boolean handleEndVote(CommandSender sender) {
        if (!voteInProgress) {
            sendMessage(sender, "&cNo vote is currently running!");
            return true;
        }
        endVote();
        sendMessage(sender, "&aVote ended manually!");
        return true;
    }

    private boolean handleCountdown(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /countdown <seconds>");
            return true;
        }

        try {
            int seconds = parseInt(args[0], 5);
            new BukkitRunnable() {
                int timeLeft = seconds;

                @Override
                public void run() {
                    if (timeLeft <= 0) {
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            p.sendTitle("§aGO!", "", 5, 20, 5);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                        });
                        cancel();
                        return;
                    }

                    if (timeLeft <= 5 || timeLeft % 10 == 0) {
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            p.sendTitle("§e" + timeLeft, "", 5, 20, 5);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        });
                    }

                    timeLeft--;
                }
            }.runTaskTimer(this, 0, 20);
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number!");
        }
        return true;
    }

    private boolean handleNumberGuess(CommandSender sender, String[] args) {
        if (numberGuessActive) {
            sendMessage(sender, "&cA number guess game is already active!");
            return true;
        }

        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /numberguess <maxNumber>");
            return true;
        }

        try {
            int max = parseInt(args[0], 100);
            targetNumber = new Random().nextInt(max) + 1;
            numberGuessActive = true;
            numberGuessWinner = null;

            broadcastMessage("&eGuess a number between &a1 &eand &a" + max + "&e!");
            broadcastMessage("&7First to type the correct number wins!");
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number!");
        }
        return true;
    }

    private boolean handleMuteChat(CommandSender sender) {
        chatMuted = !chatMuted;
        broadcastMessage(chatMuted ? "&cChat has been muted!" : "&aChat has been unmuted!");
        sendMessage(sender, chatMuted ? "&aChat muted!" : "&cChat unmuted!");
        return true;
    }

    private boolean handleClearChat(CommandSender sender) {
        for (int i = 0; i < 100; i++) {
            Bukkit.broadcastMessage("");
        }
        broadcastMessage("&7Chat has been cleared by " + sender.getName());
        return true;
    }

    private int getEligiblePlayerCount(CommandSender sender) {
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(sender))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .count();
    }

    private int parseInt(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isEliminated(Player player) {
        return eliminatedPlayers.contains(player.getUniqueId());
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
        clearPotionEffects(player);
    }

    private void clearPotionEffects(Player player) {
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));
    }

    private void resetEvent() {
        eliminatedPlayers.clear();
        disconnectedPlayers.clear();
        eliminationOrder.clear();
        votes.clear();

        eventActive = false;
        chatMuted = false;
        numberGuessActive = false;
        numberGuessWinner = null;
        voteInProgress = false;
        currentVoteQuestion = null;

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

    private boolean eliminatePlayer(Player player) {
        if (player.hasPermission("eventtools.bypass") || isEliminated(player)) {
            return false;
        }
        eliminatedPlayers.add(player.getUniqueId());
        eliminationOrder.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        return true;
    }

    private void handleElimination(Player player) {
        if (!eliminatePlayer(player)) return;

        broadcastMessage("&c" + player.getName() + " has been eliminated!");

        checkForEventEnd();
    }

    private void checkForEventEnd() {
        List<Player> remainingPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .filter(p -> !isEliminated(p))
                .collect(Collectors.toList());

        if (remainingPlayers.size() <= 1) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<Player> finalPlayers = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.hasPermission("eventtools.bypass"))
                            .filter(p -> !isEliminated(p))
                            .collect(Collectors.toList());

                    if (finalPlayers.size() == 1) {
                        Player winner = finalPlayers.get(0);
                        broadcastTitle("&6&lWINNER", "&7"+ winner.getName());
                        EventTools plugin = (EventTools) Bukkit.getPluginManager().getPlugin("EventTools");

                        Bukkit.getOnlinePlayers().forEach(player -> {
                            player.playSound(
                                    winner.getLocation(),
                                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    1.0f,
                                    0.5f
                            );
                        });

                        new BukkitRunnable() {
                            int fireworksLeft = 15;
                            Random random = new Random();

                            @Override
                            public void run() {
                                if (fireworksLeft <= 0) {
                                    cancel();
                                    return;
                                }

                                Location loc = winner.getLocation();
                                Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                                FireworkMeta meta = fw.getFireworkMeta();

                                FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
                                Color color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                                Color fade = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));

                                Bukkit.getOnlinePlayers().forEach(player -> {
                                    player.playSound(
                                            winner.getLocation(),
                                            Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                                            1.0f,
                                            1.0f
                                    );
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
                        }.runTaskTimer(plugin, 0L, 10L);

                    } else if (finalPlayers.isEmpty()) {
                        broadcastMessage("&cAll players were eliminated!");
                    }

                    announceFinalPlacements();
                    resetEvent();
                }
            }.runTask(this);
        }
    }

    private boolean revivePlayer(Player player) {
        if (player.hasPermission("eventtools.bypass") || !isEliminated(player)) {
            return false;
        }
        eliminatedPlayers.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        if (spawnLocation != null) {
            safeTeleport(player, spawnLocation);
        }
        return true;
    }

    private void freezePlayer(Player player, boolean freeze) {
        player.setWalkSpeed(freeze ? 0 : 0.2f);
        player.setFlySpeed(freeze ? 0 : 0.1f);
        player.setInvulnerable(freeze);
        sendMessage(player, freeze ? "&cYou have been frozen!" : "&aYou have been unfrozen!");
    }

    private void safeTeleport(Player player, Location location) {
        try {
            player.teleport(location);
        } catch (Exception e) {
            getLogger().warning("Failed to teleport player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void announceFinalPlacements() {
        List<Player> placements = new ArrayList<>();

        Player winner = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .filter(p -> !isEliminated(p))
                .findFirst()
                .orElse(null);

        if (winner != null) {
            placements.add(winner);
        }

        synchronized (eliminationOrder) {
            for (int i = Math.min(4, eliminationOrder.size() - 1); i >= 0; i--) {
                Player p = Bukkit.getPlayer(eliminationOrder.get(i));
                if (p != null && !placements.contains(p)) {
                    placements.add(p);
                }
            }
        }

        broadcastMessage("&6&lEvent Results:");
        String[] suffixes = {"1st", "2nd", "3rd", "4th", "5th"};
        for (int i = 0; i < Math.min(5, placements.size()); i++) {
            broadcastMessage("&e" + suffixes[i] + ": &f" + placements.get(i).getName());
        }
    }

    private void broadcastTitle(String title, String subtitle) {
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', title),
                        ChatColor.translateAlternateColorCodes('&', subtitle),
                        10, 70, 20
                )
        );
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void broadcastMessage(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("eventtools.bypass")) return;

        if (eventActive && disconnectedPlayers.remove(player.getUniqueId())) {
            eliminatePlayer(player);
            sendMessage(player, "&cYou were eliminated for disconnecting!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("eventtools.bypass")) return;

        if (eventActive && isEliminated(player)) {
            disconnectedPlayers.add(player.getUniqueId());
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
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (chatMuted && !player.hasPermission("eventtools.bypass")) {
            event.setCancelled(true);
            sendMessage(player, "&cChat is currently muted!");
            return;
        }

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
            } catch (NumberFormatException ignored) {}
        }

        if (voteInProgress) {
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
}