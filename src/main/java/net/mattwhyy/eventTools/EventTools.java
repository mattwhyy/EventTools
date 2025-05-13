package net.mattwhyy.eventTools;

import net.mattwhyy.eventTools.teams.Team;
import net.mattwhyy.eventTools.teams.TeamManager;
import net.mattwhyy.eventTools.zones.EventZone;
import net.mattwhyy.eventTools.zones.Shape;
import net.mattwhyy.eventTools.zones.ZoneManager;
import net.mattwhyy.eventTools.zones.ZoneType;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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

    private EventToolsExpansion expansion;

    final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> disconnectedPlayers = ConcurrentHashMap.newKeySet();
    final List<UUID> eliminationOrder = Collections.synchronizedList(new ArrayList<>());
    final Map<UUID, Boolean> votes = new ConcurrentHashMap<>();

    volatile String eventTitle = "Event";
    private ZoneManager zoneManager;
    private TeamManager teamManager;
    private Location spawnLocation;
    public volatile boolean eventActive = false;
    private volatile boolean chatMuted = false;
    private volatile boolean numberGuessActive = false;
    private volatile int targetNumber;
    private volatile UUID numberGuessWinner = null;
    volatile boolean voteInProgress = false;
    volatile String currentVoteQuestion;
    private volatile BukkitTask voteTask;
    volatile int voteTimeRemaining;
    long eventStartTime;

    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        getLogger().info("EventTools has been enabled!");
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        this.zoneManager = new ZoneManager(this);
        this.teamManager = new TeamManager(this);
        this.zoneManager.startParticleRenderer();
        this.teamManager.startValidationTask();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.expansion = new EventToolsExpansion(this);
            this.expansion.register();
        }
    }

    private void registerCommands() {
        Arrays.asList(
                "eliminate", "revive", "seteventspawn", "startevent", "stopevent",
                "bring", "heal", "list", "mutechat", "clearchat", "freeze",
                "timedeffect", "invsee", "changegamemode", "kit", "startvote", "endvote", "countdown", "numberguess",
                "giveitem", "clearinventory", "zone", "team"
        ).forEach(cmd -> getCommand(cmd).setExecutor(this));
    }

    @Override
    public void onDisable() {
        cleanupTasks();
        if (this.expansion != null) {
            this.expansion.unregister();
        }
        if (zoneManager != null) {
            zoneManager.shutdown();
        }
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
                case "startevent": return handleStartEvent(sender, args);
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
                case "invsee": return handleInvSee(sender, args);
                case "changegamemode": return handleChangeGamemode(sender, args);
                case "kit": return handleKitCommand(sender, args);
                case "startvote": return handleStartVote(sender, args);
                case "endvote": return handleEndVote(sender);
                case "countdown": return handleCountdown(sender, args);
                case "numberguess": return handleNumberGuess(sender, args);
                case "mutechat": return handleMuteChat(sender);
                case "clearchat": return handleClearChat(sender);
                case "zone": return handleZoneCommand(sender, args);
                case "team": return handleTeamCommand(sender, args);
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

    private boolean handleStartEvent(CommandSender sender, String[] args) {
        if (eventActive) {
            sendMessage(sender, "&cEvent is already running!");
            return true;
        }

        if (teamManager.hasActiveTeams()) {
            List<Team> activeTeams = teamManager.getActiveTeams();

            if (activeTeams.size() < 2) {
                sendMessage(sender, "&cYou need at least 2 teams to start a team event!");
                return true;
            }

            if (activeTeams.stream().anyMatch(team -> team.size() < 1)) {
                sendMessage(sender, "&cAll teams must have at least 1 player!");
                return true;
            }
        }

        eliminatedPlayers.clear();
        eliminationOrder.clear();
        eventStartTime = System.currentTimeMillis();

        eventActive = true;
        chatMuted = false;
        numberGuessActive = false;
        votes.clear();

        if (args.length > 0) {
            eventTitle = String.join(" ", args);
        } else {
            eventTitle = "Event";
        }

        if (teamManager.hasActiveTeams()) {
            teamManager.preserveTeamsOnStart();
            broadcastMessage("&6&lTEAM EVENT STARTED! &e" +
                    teamManager.getActiveTeams().size() + " teams competing!");
        } else {
            broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
        }

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
        eventStartTime = 0;
        broadcastMessage("&a&lEVENT ENDED!");
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

        String targetName = target.matches("all|alive|eliminated") ?
                brought + " player" + (brought != 1 ? "s" : "") :
                Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : "target";
        sendMessage(sender, String.format("&aBrought %s to you!", targetName));
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

        String targetName = target.matches("all|alive|eliminated") ?
                healed + " player" + (healed != 1 ? "s" : "") :
                Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : "target";
        sendMessage(sender, String.format("&aHealed %s!", targetName));
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

        String targetName = target.matches("all|alive|eliminated") ?
                given + " player" + (given != 1 ? "s" : "") :
                Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : "target";
        sendMessage(sender, String.format("&aGave item to %s!", targetName));
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

        String targetName = target.matches("all|alive|eliminated") ?
                cleared + " player" + (cleared != 1 ? "s" : "") :
                Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : "target";
        sendMessage(sender, String.format("&aCleared inventory of %s!", targetName));
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
                handleElimination(player);

                count++;
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

        handleElimination(target);
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
                if (revivePlayer(sender, player)) {
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
        if (revivePlayer(sender, reviveTarget)) {
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

        int affected = 0;
        String target = args[0].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender)) continue;

            boolean shouldAffect = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[0]);
            };

            if (shouldAffect) {
                boolean currentlyFrozen = player.getWalkSpeed() == 0;
                freezePlayer(player, !currentlyFrozen);
                affected++;
            }
        }

        if (affected == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        String targetName = target.matches("all|alive|eliminated") ?
                affected + " player" + (affected != 1 ? "s" : "") :
                Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : "target";

        boolean anyFrozen = Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> p.getWalkSpeed() == 0);
        String action = anyFrozen ? "Froze" : "Unfroze";

        sendMessage(sender, String.format("&a%s %s!", action, targetName));
        return true;
    }

    private boolean handleTimedEffect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /timedeffect <effect> <duration> [amplifier] <player|all|alive|eliminated>");
            sendMessage(sender, "&7Example: /timedeffect speed 30 1 all");
            return true;
        }

        try {
            PotionEffectType type = PotionEffectType.getByName(args[0].toUpperCase());
            if (type == null) throw new IllegalArgumentException();

            int durationArgPos = 1;
            int amplifierArgPos = 2;
            int targetArgPos = 2;

            if (args.length >= 4) {
                amplifierArgPos = 2;
                targetArgPos = 3;
            }

            int duration = parseInt(args[durationArgPos], 1) * 20;
            int amplifier = args.length >= 4 ? parseInt(args[amplifierArgPos], 0) : 0;
            int applied = 0;
            String target = args[targetArgPos].toLowerCase();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.equals(sender)) continue;

                boolean shouldApply = switch (target) {
                    case "all" -> true;
                    case "alive" -> !isEliminated(player);
                    case "eliminated" -> isEliminated(player);
                    default -> player.getName().equalsIgnoreCase(args[targetArgPos]);
                };

                if (shouldApply) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                    applied++;
                    sendMessage(player, String.format(
                            "&aYou received %s %s for %s seconds!",
                            amplifier > 0 ? "level " + (amplifier + 1) : "",
                            type.getName().toLowerCase().replace("_", " "),
                            duration / 20
                    ));
                }
            }

            if (applied == 0 && !target.matches("all|alive|eliminated")) {
                sendMessage(sender, "&cPlayer not found!");
                return true;
            }

            String targetName;
            if (target.matches("all|alive|eliminated")) {
                targetName = applied + " player" + (applied != 1 ? "s" : "");
            } else {
                Player targetPlayer = Bukkit.getPlayer(args[targetArgPos]);
                targetName = targetPlayer != null ? targetPlayer.getName() : "unknown player";
            }

            sendMessage(sender, String.format(
                    "&aApplied %s (level %d) to %s for %d seconds!",
                    type.getName(),
                    amplifier + 1,
                    targetName,
                    duration / 20
            ));
        } catch (Exception e) {
            sendMessage(sender, "&cInvalid effect, duration or amplifier!");
            sendMessage(sender, "&7Example: /timedeffect speed 30 1 all");
            sendMessage(sender, "&7Example: /timedeffect jump_boost 15 alive");
        }
        return true;
    }

    private boolean handleInvSee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }

        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /invsee <player>");
            return true;
        }

        Player admin = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || target.equals(admin)) {
            sendMessage(sender, "&cInvalid player!");
            return true;
        }

        admin.openInventory(target.getInventory());
        sendMessage(sender, "&aViewing " + target.getName() + "'s inventory");
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
                    Bukkit.getOnlinePlayers().forEach(p -> {
                        String title = timeLeft <= 3 ? "§c" + timeLeft : "§e" + timeLeft;
                        p.sendTitle(title, "", 5, 20, 5);

                        float pitch = 1.0f + (1.0f - (timeLeft / (float)seconds));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                    });

                    if (timeLeft <= 0) {
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            p.sendTitle("§aGO!", "§7The event begins!", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        });
                        cancel();
                        return;
                    }

                    timeLeft--;
                }
            }.runTaskTimer(this, 0, 20);
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number! Use a whole number (e.g. 10)");
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
        return true;
    }

    private boolean handleClearChat(CommandSender sender) {
        for (int i = 0; i < 100; i++) {
            Bukkit.broadcastMessage("");
        }
        broadcastMessage("&7Chat has been cleared by " + sender.getName());
        return true;
    }

    private boolean handleZoneCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use zone commands!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            sendZoneHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleZoneCreate(player, args);
            case "delete":
                return handleZoneDelete(player, args);
            case "list":
                return handleZoneList(player);
            case "toggle":
                return handleZoneToggle(player, args);
            default:
                sendZoneHelp(sender);
                return true;
        }
    }

    private boolean handleZoneCreate(Player sender, String[] args) {
        if (args.length < 5) {
            sendMessage(sender, "&cUsage: /zone create <name> <circle|square> <radius> <effect|must_stay|safe> [effect:amplifier]");
            return true;
        }

        try {
            String name = args[1];
            Shape shape = Shape.valueOf(args[2].toUpperCase());
            int radius = Math.min(Integer.parseInt(args[3]), 50);
            ZoneType type = ZoneType.valueOf(args[4].toUpperCase());

            PotionEffect effect = null;
            if (type == ZoneType.EFFECT) {
                if (args.length < 6) {
                    sendMessage(sender, "&cEffect zones require an effect! Example: /zone create speedzone circle 10 effect speed:1");
                    return true;
                }
                String[] effectParts = args[5].split(":");
                PotionEffectType effectType = PotionEffectType.getByName(effectParts[0].toUpperCase());
                if (effectType == null) throw new IllegalArgumentException();
                int amplifier = effectParts.length > 1 ? Integer.parseInt(effectParts[1]) : 0;
                effect = new PotionEffect(effectType, Integer.MAX_VALUE, amplifier);
            } else if (args.length > 5) {
                sendMessage(sender, "&cOnly effect zones need additional arguments!");
                return true;
            }

            EventZone zone = new EventZone(name, sender.getLocation(), shape, radius, type, effect);
            zoneManager.addZone(zone);

            sendMessage(sender, String.format(
                    "&aCreated %s zone '%s' (Radius: %d) at your location!",
                    type.name().toLowerCase(), name, radius
            ));
            return true;
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid arguments! Valid zone types: effect, must_stay, safe");
            return true;
        }
    }

    private boolean handleZoneDelete(Player sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /zone delete <name>");
            return true;
        }

        if (zoneManager.removeZone(args[1])) {
            sendMessage(sender, "&aDeleted zone '" + args[1] + "'");
        } else {
            sendMessage(sender, "&cZone not found!");
        }
        return true;
    }

    private boolean handleZoneList(Player sender) {
        List<String> zones = zoneManager.getZoneNames();
        if (zones.isEmpty()) {
            sendMessage(sender, "&7No active zones");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Active Zones:\n");
        for (String zoneName : zones) {
            EventZone zone = zoneManager.getZone(zoneName);
            message.append(String.format(
                    "&7- &e%s &7(Type: %s, Radius: %d, Location: %d,%d,%d)\n",
                    zoneName,
                    zone.getType(),
                    zone.getRadius(),
                    zone.getCenter().getBlockX(),
                    zone.getCenter().getBlockY(),
                    zone.getCenter().getBlockZ()
            ));
        }
        sendMessage(sender, message.toString());
        return true;
    }

    private void sendZoneHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6Zone Commands:\n" +
                        "&e/zone create <name> <circle|square> <radius> <effect|must_stay|safe> [effect:amplifier]\n" +
                        "&e/zone delete <name>\n" +
                        "&e/zone list\n" +
                        "&e/zone toggle <name>\n" +
                        "&7Example: /zone create speed_zone circle 15 effect speed:1"
        ));
    }

    private boolean handleZoneToggle(Player player, String[] args) {
        if (args.length < 2) {
            sendMessage(player, "&cUsage: /zone toggle <name>");
            return true;
        }

        EventZone zone = zoneManager.getZone(args[1]);
        if (zone == null) {
            sendMessage(player, "&cZone not found!");
            return true;
        }

        zone.setActive(!zone.isActive());
        sendMessage(player, String.format("&aZone '%s' is now %s",
                zone.getName(),
                zone.isActive() ? "&aACTIVE" : "&cINACTIVE"
        ));
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

    public boolean isEliminated(Player player) {
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

    public void resetEvent() {
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

    private boolean handleChangeGamemode(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendMessage(sender, "&cUsage: /changegamemode <mode> [player|all|alive|eliminated]");
            sendMessage(sender, "&7Modes: survival, creative, adventure, spectator");
            return true;
        }

        GameMode mode;
        try {
            mode = GameMode.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid gamemode! Use: survival, creative, adventure, spectator");
            return true;
        }

        String target = args.length > 1 ? args[1].toLowerCase() : "self";

        int changed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldChange = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                case "self" -> sender instanceof Player && ((Player) sender).getUniqueId().equals(player.getUniqueId());
                default -> player.getName().equalsIgnoreCase(args[1]);
            };

            if (shouldChange) {
                player.setGameMode(mode);
                sendMessage(player, "&aYour gamemode was changed to " + mode.name().toLowerCase());
                changed++;
            }
        }

        if (changed == 0 && !target.matches("all|alive|eliminated|self")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        String targetName = target.matches("all|alive|eliminated") ?
                target + " players" :
                target.equals("self") ? "yourself" : args[1];
        sendMessage(sender, String.format("&aChanged gamemode of %s to %s", targetName, mode.name().toLowerCase()));
        return true;
    }

    private boolean handleKitCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /kit <kitName> <player|all|alive|eliminated>");
            return true;
        }

        String kitName = args[0].toLowerCase();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");

        if (kitsSection == null || !kitsSection.contains(kitName)) {
            sendMessage(sender, "&cKit '" + kitName + "' not found!");
            sendMessage(sender, "&7Available kits: " + String.join(", ", kitsSection.getKeys(false)));
            return true;
        }

        int given = 0;
        String target = args[1].toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender)) continue;

            boolean shouldGive = switch (target) {
                case "all" -> true;
                case "alive" -> !isEliminated(player);
                case "eliminated" -> isEliminated(player);
                default -> player.getName().equalsIgnoreCase(args[1]);
            };

            if (shouldGive) {
                giveKit(player, kitName);
                sendMessage(player, "&aYou received the " + kitName + " kit!");
                given++;
            }
        }

        if (given == 0 && !target.matches("all|alive|eliminated")) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        String targetName = target.matches("all|alive|eliminated") ?
                given + " player" + (given != 1 ? "s" : "") :
                Bukkit.getPlayer(args[1]) != null ? Bukkit.getPlayer(args[1]).getName() : "target";
        sendMessage(sender, String.format("&aGave %s kit to %s!", kitName, targetName));
        return true;
    }

    private void giveKit(Player player, String kitName) {
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

    private boolean handleTeamCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendTeamHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleTeamCreate(sender, args);
            case "delete":
                return handleTeamDelete(sender, args);
            case "assign":
                return handleTeamAssign(sender, args);
            case "balance":
                return handleTeamBalance(sender);
            case "color":
                return handleTeamColor(sender, args);
            case "info":
                return handleTeamInfo(sender);
            default:
                sendTeamHelp(sender);
                return true;
        }
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /team create <name> <color>");
            sendMessage(sender, "&7Available colors: " + Arrays.toString(ChatColor.values()));
            return true;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());
            if (teamManager.createTeam(args[1], color)) {
                sendMessage(sender, "&aCreated team " + color + args[1]);
            } else {
                sendMessage(sender, "&cMax teams reached (4) or team already exists");
            }
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid color! Use: " + Arrays.toString(ChatColor.values()));
        }
        return true;
    }

    private boolean handleTeamDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /team delete <name>");
            return true;
        }

        if (teamManager.deleteTeam(args[1])) {
            sendMessage(sender, "&aDeleted team " + args[1]);
        } else {
            sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    private boolean handleTeamAssign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /team assign <player> <team>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        if (teamManager.addToTeam(target, args[2])) {
            sendMessage(sender, "&aAssigned " + target.getName() + " to " + args[2]);
            sendMessage(target, "&aYou've been assigned to team " + args[2]);
        } else {
            sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    private boolean handleTeamBalance(CommandSender sender) {
        teamManager.balanceTeams();
        broadcastMessage("&aTeams have been balanced!");
        return true;
    }

    private boolean handleTeamColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /team color <team> <newColor>");
            return true;
        }

        Team team = teamManager.getTeam(args[1]);
        if (team == null) {
            sendMessage(sender, "&cTeam not found!");
            return true;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());
            team.setColor(color);
            sendMessage(sender, "&aTeam color updated!");
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid color! Use: " + Arrays.toString(ChatColor.values()));
        }
        return true;
    }

    private boolean handleTeamInfo(CommandSender sender) {
        if (!teamManager.hasActiveTeams()) {
            sendMessage(sender, "&7No active teams. Use /team create");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Active Teams:\n");
        teamManager.getActiveTeams().forEach(team -> {
            message.append(team.getColor())
                    .append(team.getName())
                    .append(" &7(")
                    .append(team.size())
                    .append(" players): ");

            message.append(team.getMembers().stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        String status = (p != null && isEliminated(p)) ? "&m" : "";
                        return status + (p != null ? p.getName() : "?");
                    })
                    .collect(Collectors.joining("&7, ")));

            message.append("\n");
        });

        sendMessage(sender, message.toString());
        return true;
    }

    private void sendTeamHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6Team Commands:\n" +
                        "&e/team create <name> <color> &7- Create new team\n" +
                        "&e/team delete <name> &7- Remove a team\n" +
                        "&e/team assign <player> <team> &7- Assign a player to a team\n" +
                        "&e/team balance &7- Randomly distribute players\n" +
                        "&e/team color <name> <color> &7- Change team color\n" +
                        "&e/team info &7- Show detailed team info\n" +
                        "&7Available colors: &f" + Arrays.toString(ChatColor.values())
        ));
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
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        eliminatedPlayers.add(player.getUniqueId());
        eliminationOrder.add(player.getUniqueId());
        player.setGameMode(eliminationMode);
        return true;
    }

    public void handleElimination(Player player) {
        if (!eliminatePlayer(player)) return;

        Optional<Team> team = teamManager.getPlayerTeam(player);

        if (team.isPresent()) {
            broadcastMessage(team.get().getColor() + team.get().getName() +
                    " &7> &c" + player.getName() + " has been eliminated!");
        } else {
            broadcastMessage("&c" + player.getName() + " has been eliminated!");
        }

        checkForEventEnd();
    }

    private void checkForEventEnd() {
        if (teamManager.getTeamNames().isEmpty()) {
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
        } else {
            teamManager.checkForTeamVictory();
        }
    }

    private boolean revivePlayer(CommandSender sender, Player player) {
        if (player.hasPermission("eventtools.bypass") || !isEliminated(player)) {
            return false;
        }
        eliminatedPlayers.remove(player.getUniqueId());
        eliminationOrder.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);

        teamManager.handleRevival(player);

        if (sender instanceof Player senderPlayer) {
            safeTeleport(player, senderPlayer.getLocation());
        } else if (spawnLocation != null) {
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
        if (winner != null) placements.add(winner);

        synchronized (eliminationOrder) {
            for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
                UUID playerId = eliminationOrder.get(i);
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && isEliminated(p) && !placements.contains(p)) {
                    placements.add(p);
                    if (placements.size() >= 4) break;
                }
            }
        }

        broadcastMessage("&6&lEvent Results:");
        String[] suffixes = {"1st", "2nd", "3rd", "4th", "5th"};
        String[] colors = {"&6", "&7", "&c", "&f", "&f"};
        String[] icons = {"🥇 ", "🥈 ", "🥉 ", "", ""};

        for (int i = 0; i < Math.min(5, placements.size()); i++) {
            String placement = colors[i] + icons[i] + suffixes[i] + ": &r" + placements.get(i).getName();
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

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void broadcastMessage(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
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

        if (eventActive && !disconnectedPlayers.remove(player.getUniqueId())) {
            eliminatePlayer(player);
            sendMessage(player, "&cYou joined mid-event and were automatically eliminated!");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("eventtools.admin")) return completions;

        String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        switch (cmd.getName().toLowerCase()) {
            case "bring":
            case "heal":
            case "clearinventory":
            case "freeze":
            case "eliminate":
            case "revive":
            case "invsee":
                if (args.length == 1) addTargetCompletions(completions);
                break;

            case "giveitem":
                if (args.length == 1) {
                    addTargetCompletions(completions);
                } else if (args.length == 2) {
                    completions.add("<amount>");
                }
                break;

            case "timedeffect":
                if (args.length == 1) {
                    Arrays.stream(PotionEffectType.values())
                            .map(e -> e.getName().toLowerCase())
                            .forEach(completions::add);
                } else if (args.length == 2) {
                    completions.add("<duration>");
                } else if (args.length == 3) {
                    completions.add("<amplifier>");
                    addTargetCompletions(completions);
                } else if (args.length == 4) {
                    addTargetCompletions(completions);
                }
                break;

            case "kit":
                if (args.length == 1) {
                    if (config.getConfigurationSection("kits") != null) {
                        completions.addAll(config.getConfigurationSection("kits").getKeys(false));
                    }
                } else if (args.length == 2) {
                    addTargetCompletions(completions);
                }
                break;

            case "changegamemode":
                if (args.length == 1) {
                    completions.addAll(Arrays.asList("survival", "creative", "adventure", "spectator"));
                } else if (args.length == 2) {
                    completions.addAll(Arrays.asList("all", "alive", "eliminated"));
                    completions.addAll(getOnlinePlayerNames());
                }
                break;

            case "startevent":
                if (args.length == 1) {
                    if (eventTitle != null && !eventTitle.equals("Event")) {
                        completions.add(eventTitle);
                    }
                    completions.add("<title>");
                }
                break;


            case "startvote":
                if (args.length >= 1) completions.add("<question>");
                break;
            case "numberguess":
                if (args.length == 1) completions.add("<maxNumber>");
                break;
            case "countdown":
                if (args.length == 1) completions.add("<seconds>");
                break;
            case "list":
                if (args.length == 1) completions.addAll(Arrays.asList("alive", "eliminated", "all"));
                break;

            case "zone":
                if (args.length == 1) {
                    return filterCompletions(Arrays.asList("create", "delete", "list", "toggle"), args[0]);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(Collections.singletonList("<name>"), args[1]);
                } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(Arrays.asList("circle", "square"), args[2]);
                } else if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(Collections.singletonList("<radius>"), args[3]);
                } else if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(Arrays.asList("effect", "must_stay", "safe"), args[4]);
                } else if (args.length == 6 && args[0].equalsIgnoreCase("create") && args[4].equalsIgnoreCase("effect")) {
                    return filterCompletions(
                            Arrays.stream(PotionEffectType.values())
                                    .map(e -> e.getName().toLowerCase())
                                    .collect(Collectors.toList()),
                            args[5]
                    );
                } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("toggle"))) {
                    return filterCompletions(zoneManager.getZoneNames(), args[1]);
                }
                break;

            case "team":
                if (args.length == 1) {
                    return filterCompletions(Arrays.asList(
                            "create", "delete", "assign",
                            "balance", "color", "info"
                    ), args[0]);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(Collections.singletonList("<name>"), args[1]);
                } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
                    return filterCompletions(
                            Arrays.stream(ChatColor.values())
                                    .filter(c -> c != ChatColor.RESET)
                                    .map(Enum::name)
                                    .collect(Collectors.toList()),
                            args[2]
                    );
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("assign")) {
                        return filterCompletions(getOnlinePlayerNames(), args[1]);
                    } else if (args[0].equalsIgnoreCase("delete") ||
                            args[0].equalsIgnoreCase("color")) {
                        return filterCompletions(teamManager.getTeamNames(), args[1]);
                    }
                } else if (args.length == 3) {
                    if (args[0].equalsIgnoreCase("assign")) {
                        return filterCompletions(teamManager.getTeamNames(), args[2]);
                    } else if (args[0].equalsIgnoreCase("color")) {
                        return filterCompletions(
                                Arrays.stream(ChatColor.values())
                                        .map(Enum::name)
                                        .collect(Collectors.toList()),
                                args[2]
                        );
                    }
                }
                break;

            case "seteventspawn":
            case "stopevent":
            case "mutechat":
            case "clearchat":
            case "endvote":
                break;
        }

        return filterCompletions(completions, currentArg);
    }

    private void addTargetCompletions(List<String> completions) {
        completions.addAll(Arrays.asList("all", "alive", "eliminated"));
        completions.addAll(getOnlinePlayerNames());
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterCompletions(List<String> completions, String currentArg) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }
}