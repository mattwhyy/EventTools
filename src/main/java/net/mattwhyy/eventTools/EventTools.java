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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
    final List<UUID> eliminationOrder = Collections.synchronizedList(new ArrayList<>());
    final Map<UUID, Boolean> votes = new ConcurrentHashMap<>();

    volatile String eventTitle = "Event";
    private ZoneManager zoneManager;
    TeamManager teamManager;
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

    private enum EventType {
        PURE_FFA,
        HYBRID_FFA,
        TEAM_BATTLE
    }
    private volatile EventType eventType;

    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getLogger().info("EventTools has been enabled!");

        registerCommands();

        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("evt_"))
                .forEach(org.bukkit.scoreboard.Team::unregister);

        getServer().getPluginManager().registerEvents(this, this);
        this.zoneManager = new ZoneManager(this);
        this.teamManager = new TeamManager(this);

        this.zoneManager.startParticleRenderer();

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
            sendMessage(sender, "&cAn error occurred. Please check console for details.");
            getLogger().severe("Command error (" + sender.getName() + "): " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private List<Player> getTargetPlayers(CommandSender sender, String target) {
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
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !isEliminated(p))
                            .forEach(targets::add);
                    break;
                case "eliminated":
                    Bukkit.getOnlinePlayers().stream()
                            .filter(this::isEliminated)
                            .forEach(targets::add);
                    break;
                default:
                    Player player = Bukkit.getPlayer(target);
                    if (player != null) {
                        targets.add(player);
                    }
            }
        }

        if (sender instanceof Player) {
            targets.remove(sender);
        }

        targets.removeIf(p -> p.hasPermission("eventtools.bypass"));

        return targets;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can set spawn!");
            return true;
        }
        spawnLocation = ((Player) sender).getLocation();

        sendMessage(sender, "&eSpawn set at your location!");
        return true;
    }

    private boolean handleStartEvent(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (eventActive) {
            sendMessage(sender, "&cEvent is already running!");
            return true;
        }

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());
        if (players.size() < 2) {
            sendMessage(sender, "&cYou need at least &e2 &cplayers to start an event!");
            return true;
        }

        eliminatedPlayers.clear();
        eliminationOrder.clear();
        eventStartTime = System.currentTimeMillis();
        votes.clear();

        eventTitle = args.length > 0 ? String.join(" ", args) : "Event";

        if (!teamManager.getTeamNames().isEmpty()) {
            List<Team> activeTeams = teamManager.getActiveTeams();

            if (activeTeams.isEmpty()) {
                List<String> teamNames = new ArrayList<>(teamManager.getTeamNames());
                teamNames.forEach(teamManager::deleteTeam);
                broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
            }
            else if (activeTeams.size() == 1) {
                Team team = activeTeams.get(0);
                broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
                broadcastMessage(team.getColor() + team.getName() +
                        " &7playing as a group against unassigned players");

                eventType = EventType.HYBRID_FFA;
            }
            else {
                List<Player> unassignedPlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.hasPermission("eventtools.bypass"))
                        .filter(p -> !teamManager.getPlayerTeam(p).isPresent())
                        .collect(Collectors.toList());

                if (!unassignedPlayers.isEmpty()) {
                    teamManager.balanceTeams();
                    broadcastMessage("&aBalanced &e" + unassignedPlayers.size() + " &aunassigned players");
                }

                broadcastMessage("&6&lEVENT STARTED! &e" + activeTeams.size() + " teams competing!");
                eventType = EventType.TEAM_BATTLE;
            }
        } else {
            broadcastMessage("&6&lEVENT STARTED! &eFree-for-all mode!");
            eventType = EventType.PURE_FFA;
        }

        eventActive = true;
        chatMuted = false;
        numberGuessActive = false;
        broadcastSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 1);

        broadcastTitle(
                config.getString("messages.event-start-title", "§6Event started!"),
                config.getString("messages.event-start-subtitle", "§eGood luck!")
        );
        return true;
    }

    private boolean handleStopEvent(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /bring <player|all|alive|eliminated|@team>");
            return true;
        }

        Player senderPlayer = (Player) sender;
        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !p.equals(senderPlayer))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(senderPlayer.getName())) {
                sendMessage(sender, "&cYou can't bring yourself!");
            }
            else if (getTargetPlayers(sender, args[0]).stream()
                    .anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't bring that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        for (Player target : targets) {
            safeTeleport(target, senderPlayer.getLocation());
            sendMessage(target, "&aYou were brought to " + sender.getName());
        }

        String targetName;
        if (args[0].startsWith("@")) {
            targetName = "team " + args[0].substring(1);
        } else {
            targetName = args[0].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[0];
        }

        sendMessage(sender, String.format("&eBrought %s to you!", targetName));
        return true;
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /heal <player|all|alive|eliminated|@team>");
            return true;
        }

        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !(sender instanceof Player && p.equals((Player)sender)))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't heal yourself!");
            }
            else if (getTargetPlayers(sender, args[0]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't heal that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        for (Player player : targets) {
            healPlayer(player);
            sendMessage(player, "&aYou have been healed!");
        }

        String targetName;
        if (args[0].startsWith("@")) {
            targetName = "team " + args[0].substring(1);
        } else {
            targetName = args[0].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[0];
        }

        sendMessage(sender, String.format("&aHealed %s!", targetName));
        return true;
    }

    private boolean handleGiveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }
        if (args.length < 1) {
            sendMessage(sender, "&cUsage: /giveitem <player|all|alive|eliminated|@team> [amount]");
            return true;
        }

        Player givingPlayer = (Player) sender;
        ItemStack item = givingPlayer.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            sendMessage(sender, "&cYou must be holding an item!");
            return true;
        }

        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !p.equals(givingPlayer))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't give items to yourself!");
            }
            else if (getTargetPlayers(sender, args[0]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't give items to that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        int amount = args.length >= 2 ? parseInt(args[1], 1) : 1;
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);

        for (Player player : targets) {
            player.getInventory().addItem(toGive.clone());
            sendMessage(player, "&aYou received an item from " + sender.getName());
        }

        String targetName;
        if (args[0].startsWith("@")) {
            targetName = "team " + args[0].substring(1);
        } else {
            targetName = args[0].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[0];
        }

        sendMessage(sender, String.format("&eGave item to %s!", targetName));
        return true;
    }

    private boolean handleClearInventory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /clearinventory <player|all|alive|eliminated|@team>");
            return true;
        }

        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !(sender instanceof Player && p.equals((Player)sender)))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't clear your own inventory!");
            }
            else if (getTargetPlayers(sender, args[0]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't clear the inventory of that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        for (Player player : targets) {
            player.getInventory().clear();
            sendMessage(player, "&cYour inventory was cleared!");
        }

        String targetName;
        if (args[0].startsWith("@")) {
            targetName = "team " + args[0].substring(1);
        } else {
            targetName = args[0].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[0];
        }

        sendMessage(sender, String.format("&eCleared inventory of %s!", targetName));
        return true;
    }

    private boolean handleEliminateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!eventActive) {
            sendMessage(sender, "&cNo event is currently running!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /eliminate <player|all|@team>");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            int count = 0;
            List<Player> toEliminate = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.hasPermission("eventtools.bypass"))
                    .filter(p -> !isEliminated(p))
                    .collect(Collectors.toList());

            for (Player player : toEliminate) {
                handleElimination(player);
                count++;
            }

            if (count == 0) {
                sendMessage(sender, "&cAll players are already eliminated!");
            } else {
                sendMessage(sender, "&aEliminated " + count + " players!");
            }
            return true;
        }

        if (args[0].startsWith("@")) {
            String teamName = args[0].substring(1);
            Team team = teamManager.getTeam(teamName);
            if (team == null) {
                sendMessage(sender, "&cTeam not found!");
                return true;
            }

            int count = 0;
            for (Player player : team.getOnlineMembers()) {
                if (!player.hasPermission("eventtools.bypass") && !isEliminated(player)) {
                    handleElimination(player);
                    count++;
                }
            }

            if (count == 0) {
                sendMessage(sender, "&cAll members of team " + team.getColor() + teamName + " &care already eliminated!");
            } else {
                sendMessage(sender, "&eEliminated " + count + " members of team " + team.getColor() + teamName);
            }
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
        if (isEliminated(target)) {
            sendMessage(sender, "&c" + target.getName() + " is already eliminated!");
            return true;
        }

        handleElimination(target);
        checkForEventEnd();
        return true;
    }

    private boolean handleReviveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!eventActive) {
            sendMessage(sender, "&cNo event is currently running!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /revive <player|all|@team>");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("eventtools.bypass") && isEliminated(player)) {
                    revivePlayer(sender, player);
                    count++;
                }
            }
            broadcastMessage("&a" + count + " players have been revived!");
            sendMessage(sender, "&eRevived " + count + " players!");
            return true;
        }

        if (args[0].startsWith("@")) {
            String teamName = args[0].substring(1);
            Team team = teamManager.getTeam(teamName);
            if (team == null) {
                sendMessage(sender, "&cTeam not found!");
                return true;
            }

            int count = 0;
            for (Player player : team.getOnlineMembers()) {
                if (!player.hasPermission("eventtools.bypass") && isEliminated(player)) {
                    revivePlayer(sender, player);
                    count++;
                }
            }

            if (count == 0) {
                sendMessage(sender, "&cNo eliminated members found in team " + team.getColor() + teamName);
            } else {
                broadcastMessage("&aRevived " + count + " members of team " + team.getColor() + teamName);
                sendMessage(sender, "&eRevived " + count + " members of team " + team.getColor() + teamName);
            }
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /freeze <player|all|alive|eliminated|@team>");
            return true;
        }

        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !(sender instanceof Player && p.equals((Player) sender)))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't freeze yourself!");
            }
            else if (getTargetPlayers(sender, args[0]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't freeze that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        boolean anyFrozen = false;
        for (Player player : targets) {
            boolean currentlyFrozen = player.getWalkSpeed() == 0;
            freezePlayer(player, !currentlyFrozen);
            anyFrozen = anyFrozen || !currentlyFrozen;
        }

        String targetName;
        if (args[0].startsWith("@")) {
            targetName = "team " + args[0].substring(1);
        } else {
            targetName = args[0].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[0];
        }

        String action = anyFrozen ? "Froze" : "Unfroze";
        sendMessage(sender, String.format("&e%s %s!", action, targetName));
        return true;
    }

    private boolean handleTimedEffect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /timedeffect <effect> <duration> [amplifier] <player|all|alive|eliminated|@team>");
            return true;
        }

        try {
            PotionEffectType type = PotionEffectType.getByName(args[0].toUpperCase());

            int durationArgPos = 1;
            int amplifierArgPos = 2;
            int targetArgPos = 2;

            if (args.length >= 4) {
                amplifierArgPos = 2;
                targetArgPos = 3;
            }

            int duration = parseInt(args[durationArgPos], 1) * 20;
            int amplifier = args.length >= 4 ? parseInt(args[amplifierArgPos], 0) : 0;

            List<Player> targets = getTargetPlayers(sender, args[targetArgPos])
                    .stream()
                    .filter(p -> !(sender instanceof Player && p.equals((Player) sender)))
                    .filter(p -> !p.hasPermission("eventtools.bypass"))
                    .collect(Collectors.toList());

            if (targets.isEmpty()) {
                if (args[targetArgPos].equalsIgnoreCase(sender.getName())) {
                    sendMessage(sender, "&cYou can't apply effects to yourself!");
                }
                else if (getTargetPlayers(sender, args[targetArgPos]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                    sendMessage(sender, "&cYou can't apply effects to that player!");
                }
                else {
                    sendMessage(sender, "&cNo matching players found!");
                }
                return true;
            }

            for (Player player : targets) {
                player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                sendMessage(player, String.format(
                        "&aYou received %s %s for %s seconds!",
                        amplifier > 0 ? "level " + (amplifier + 1) : "",
                        type.getName().toLowerCase().replace("_", " "),
                        duration / 20
                ));
            }

            String targetName;
            if (args[targetArgPos].startsWith("@")) {
                targetName = "team " + args[targetArgPos].substring(1);
            } else {
                targetName = args[targetArgPos].matches("all|alive|eliminated") ?
                        targets.size() + " players" : args[targetArgPos];
            }

            sendMessage(sender, String.format(
                    "&eApplied %s (level %d) to %s for %d seconds!",
                    type.getName(),
                    amplifier + 1,
                    targetName,
                    duration / 20
            ));
        } catch (Exception e) {
            sendMessage(sender, "&cInvalid effect, duration or amplifier!");
        }
        return true;
    }

    private boolean handleInvSee(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }
        if (args.length != 1) {
            sendMessage(sender, "&cUsage: /invsee <player>");
            return true;
        }

        Player admin = (Player) sender;

        List<Player> targets = getTargetPlayers(sender, args[0])
                .stream()
                .filter(p -> !p.equals(admin))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[0].equalsIgnoreCase(admin.getName())) {
                sendMessage(sender, "&cYou can't view your own inventory!");
            }
            else if (Bukkit.getPlayer(args[0]) != null && Bukkit.getPlayer(args[0]).hasPermission("eventtools.bypass")) {
                sendMessage(sender, "&cYou can't view that player's inventory!");
            }
            else {
                sendMessage(sender, "&cPlayer not found!");
            }
            return true;
        }

        Player target = targets.get(0);
        admin.openInventory(target.getInventory());
        sendMessage(sender, "&eViewing " + target.getName() + "'s inventory");
        return true;
    }

    private boolean handleStartVote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (!voteInProgress) {
            sendMessage(sender, "&cNo vote is currently running!");
            return true;
        }
        endVote();
        sendMessage(sender, "&aVote ended manually!");
        return true;
    }

    private boolean handleCountdown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
                    String title = timeLeft <= 3 ? "§c" + timeLeft : "§e" + timeLeft;
                    broadcastTitle(title, "");

                    broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0);

                    if (timeLeft <= 0) {
                        broadcastTitle("&aGO!", "");
                        broadcastSound(Sound.ENTITY_PLAYER_LEVELUP, 1,1.5f);
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        chatMuted = !chatMuted;
        broadcastMessage(chatMuted ? "&cChat has been muted!" : "&aChat has been unmuted!");
        return true;
    }

    private boolean handleClearChat(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        for (int i = 0; i < 100; i++) {
            broadcastMessage("");
        }
        broadcastMessage("&8Chat has been cleared by " + sender.getName());
        return true;
    }

    private boolean handleZoneCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
            case "info":
                return handleZoneInfo(player);
            case "toggle":
                return handleZoneToggle(player, args);
            case "resize":
                return handleZoneResize(player, args);
            case "movehere":
                return handleZoneMoveHere(player, args);
            default:
                sendZoneHelp(sender);
                return true;
        }
    }

    private boolean handleZoneCreate(Player sender, String[] args) {
        if (args.length < 5) {
            sendMessage(sender, "&cUsage: /zone create <name> <circle|square> <radius> <type> [args]");
            sendMessage(sender, "&7Types: effect(<effect:level>,<effect2:level>), damage(<amount>), safe, must_stay, team_only(<team>)");
            return true;
        }

        try {
            String name = args[1];
            Shape shape = Shape.valueOf(args[2].toUpperCase());
            int radius = Math.min(Integer.parseInt(args[3]), 50);
            ZoneType type = ZoneType.valueOf(args[4].toUpperCase());

            List<PotionEffect> effects = new ArrayList<>();
            double damage = 0;
            String teamName = null;

            switch (type) {
                case EFFECT:
                    if (args.length < 6) {
                        sendMessage(sender, "&cEffect zones require effects! Example: speed:1,jump_boost:2");
                        return true;
                    }
                    for (String effectStr : args[5].split(",")) {
                        String[] parts = effectStr.split(":");
                        PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                        int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        effects.add(new PotionEffect(effectType, Integer.MAX_VALUE, amplifier));
                    }
                    break;

                case DAMAGE:
                    if (args.length < 6) {
                        sendMessage(sender, "&cDamage zones require damage amount! Example: damage 2.5");
                        return true;
                    }
                    damage = Double.parseDouble(args[5]);
                    break;

                case TEAM_ONLY:
                    if (args.length < 6) {
                        sendMessage(sender, "&cTeam zones require team name! Example: team_only red");
                        return true;
                    }
                    teamName = args[5];
                    if (teamManager.getTeam(teamName) == null) {
                        sendMessage(sender, "&cTeam '" + teamName + "' doesn't exist!");
                        return true;
                    }
                    break;

                default:
                    if (args.length > 5) {
                        sendMessage(sender, "&cOnly effect, damage, and team_only zones need extra arguments!");
                        return true;
                    }
            }

            EventZone zone = new EventZone(name, sender.getLocation(), shape, radius,
                    type, effects, damage, teamManager);

            if (type == ZoneType.TEAM_ONLY) {
                zone.setAllowedTeam(teamName);
            }

            zoneManager.addZone(zone);

            String extraInfo = "";
            if (type == ZoneType.TEAM_ONLY) {
                extraInfo = " &7(Team: " + teamName + ")";
            } else if (type == ZoneType.DAMAGE) {
                extraInfo = " &7(Damage: " + damage + " hearts/sec)";
            } else if (type == ZoneType.EFFECT) {
                extraInfo = " &7(Effects: " + args[5] + ")";
            }

            sendMessage(sender, String.format(
                    "&eCreated %s zone '%s' &8(Radius: %d)%s",
                    type.name().toLowerCase(), name, radius, extraInfo
            ));
            return true;

        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid arguments! Error: " + e.getMessage());
            return true;
        }
    }

    private boolean handleZoneDelete(Player sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /zone delete <name>");
            return true;
        }

        if (zoneManager.removeZone(args[1])) {
            sendMessage(sender, "&eDeleted zone '" + args[1] + "'");
        } else {
            sendMessage(sender, "&cZone not found!");
        }
        return true;
    }

    private boolean handleZoneInfo(Player sender) {
        List<String> zones = zoneManager.getZoneNames();
        if (zones.isEmpty()) {
            sendMessage(sender, "&7No zones available. Use &e/zone create &7to create some!");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Zones:\n");
        for (String zoneName : zones) {
            EventZone zone = zoneManager.getZone(zoneName);

            message.append("&e")
                    .append(zone.getName())
                    .append(" &7(")
                    .append(zone.getType());

            if (zone.getType() == ZoneType.TEAM_ONLY && zone.getAllowedTeam() != null) {
                Team team = teamManager.getTeam(zone.getAllowedTeam());
                ChatColor teamColor = team != null ? team.getColor() : ChatColor.WHITE;
                message.append(" - ").append(teamColor).append(zone.getAllowedTeam());
            }

            message.append(")");

            message.append("\n&8> &7Status: ")
                    .append(zone.isActive() ? "&aActive" : "&cInactive")
                    .append("&7, Radius: ")
                    .append(zone.getRadius())
                    .append("&7, Shape: ")
                    .append(zone.getShape());

            Location center = zone.getCenter();
            message.append("\n&8> &7Location: ")
                    .append(center.getWorld().getName())
                    .append(" &8(")
                    .append(center.getBlockX())
                    .append(", ")
                    .append(center.getBlockY())
                    .append(", ")
                    .append(center.getBlockZ())
                    .append(")");

            Set<Player> playersInZone = zone.getPlayersInside();
            message.append("\n&8> &7Players: ");
            if (playersInZone.isEmpty()) {
                message.append("&7None");
            } else {
                message.append(playersInZone.stream()
                        .map(Player::getName)
                        .collect(Collectors.joining("&7, ")));
            }

            message.append("\n");
        }

        sendMessage(sender, message.toString());
        return true;
    }

    private boolean handleZoneMoveHere(Player sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /zone movehere <name>");
            return true;
        }

        String zoneName = args[1];
        EventZone zone = zoneManager.getZone(zoneName);

        if (zone == null) {
            sendMessage(sender, "&cZone '" + zoneName + "' not found!");
            return true;
        }

        EventZone newZone = new EventZone(
                zone.getName(),
                sender.getLocation(),
                zone.getShape(),
                zone.getRadius(),
                zone.getType(),
                zone.getEffects(),
                zone.getDamage(),
                teamManager
        );

        if (zone.getType() == ZoneType.TEAM_ONLY) {
            newZone.setAllowedTeam(zone.getAllowedTeam());
        }

        zoneManager.removeZone(zone.getName());
        zoneManager.addZone(newZone);

        sendMessage(sender, String.format(
                "&eMoved zone '%s' to your current location!",
                zoneName
        ));
        return true;
    }

    private boolean handleZoneResize(Player sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /zone resize <name> <radius> [stepCount]");
            return true;
        }

        String zoneName = args[1];
        EventZone zone = zoneManager.getZone(zoneName);

        if (zone == null) {
            sendMessage(sender, "&cZone '" + zoneName + "' not found!");
            return true;
        }

        try {
            int newRadius = Integer.parseInt(args[2]);
            newRadius = Math.min(newRadius, 50);

            int stepCount = 1;
            if (args.length >= 4) {
                stepCount = Math.min(Integer.parseInt(args[3]), 300);
                if (stepCount <= 0) {
                    sendMessage(sender, "&cStep count must be positive! (1-60)");
                    return true;
                }
            }

            cancelResizeTask(zoneName);

            int currentRadius = zone.getRadius();
            int difference = newRadius - currentRadius;
            double changePerStep = (double)difference / stepCount;

            if (stepCount > 1) {
                int finalNewRadius = newRadius;
                int finalStepCount = stepCount;
                BukkitTask task = new BukkitRunnable() {
                    private int stepsCompleted = 0;
                    private double current = currentRadius;

                    @Override
                    public void run() {
                        if (zoneManager.getZone(zoneName) == null) {
                            this.cancel();
                            return;
                        }

                        if (stepsCompleted >= finalStepCount) {
                            zone.setRadius(finalNewRadius);
                            sendMessage(sender, String.format(
                                    "&aZone &e'%s' &ahas finished resizing to radius &e%d",
                                    zoneName, finalNewRadius
                            ));
                            this.cancel();
                            return;
                        }

                        current += changePerStep;
                        zone.setRadius((int)Math.round(current));
                        stepsCompleted++;

                        if (stepsCompleted % 5 == 0 || stepsCompleted == finalStepCount) {
                            sendMessage(sender, String.format(
                                    "&7Zone &e'%s' &7progress: &a%d%% &8(%d/%d steps)",
                                    zoneName,
                                    (stepsCompleted * 100) / finalStepCount,
                                    stepsCompleted,
                                    finalStepCount
                            ));
                        }
                    }
                }.runTaskTimer(this, 0L, 20L);

                activeResizeTasks.put(zoneName, task);

                sendMessage(sender, String.format(
                        "&aZone &e'%s' &aresizing from &e%d &ato &e%d &ain &e%d &aseconds",
                        zoneName, currentRadius, newRadius, stepCount
                ));
            } else {
                zone.setRadius(newRadius);
                sendMessage(sender, String.format(
                        "&aZone &e'%s' &aresized to radius &e%d",
                        zoneName, newRadius
                ));
            }

            return true;
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number format for radius or step count!");
            return true;
        }
    }

    private void sendZoneHelp(CommandSender sender) {
        sendMessage(sender,
                "&6Zone Commands:\n" +
                        "&e/zone create <name> <circle|square> <radius> <effect|must_stay|safe|team_only> [effect:amplifier|team] &7- Create a new zone\n" +
                        "&e/zone delete <name> &7- Delete a zone\n" +
                        "&e/zone info &7- Show detailed zone info\n" +
                        "&e/zone toggle <name> &7- Toggles a zone\n" +
                        "&e/zone resize <name> <radius> [stepCount] &7- Resizes a zone\n" +
                        "&e/zone movehere <name> &7- Moves zone to your location\n"
        );
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
        sendMessage(player, String.format("&eZone '%s' is now %s",
                zone.getName(),
                zone.isActive() ? "&aACTIVE" : "&cINACTIVE"
        ));
        return true;
    }

    private boolean handleChangeGamemode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /changegamemode <mode> <player|all|alive|eliminated|@team>");
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

        List<Player> targets = getTargetPlayers(sender, args[1])
                .stream()
                .filter(p -> !(sender instanceof Player && p.equals((Player) sender)))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[1].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't change your own gamemode!");
            }
            else if (getTargetPlayers(sender, args[1]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't change that player's gamemode!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        for (Player player : targets) {
            player.setGameMode(mode);
            sendMessage(player, "&aYour gamemode was changed to " + mode.name().toLowerCase());
        }

        String targetName;
        if (args[1].startsWith("@")) {
            targetName = "team " + args[1].substring(1);
        } else {
            targetName = args[1].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[1];
        }

        sendMessage(sender, String.format("&eChanged gamemode of %s to %s", targetName, mode.name().toLowerCase()));
        return true;
    }

    private boolean handleKitCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /kit <kitName> <player|all|alive|eliminated|@team>");
            return true;
        }

        String kitName = args[0].toLowerCase();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");

        if (kitsSection == null || !kitsSection.contains(kitName)) {
            sendMessage(sender, "&cKit '" + kitName + "' not found!");
            sendMessage(sender, "&7Available kits: " + String.join(", ", kitsSection.getKeys(false)));
            return true;
        }

        List<Player> targets = getTargetPlayers(sender, args[1])
                .stream()
                .filter(p -> !(sender instanceof Player && p.equals((Player) sender)))
                .filter(p -> !p.hasPermission("eventtools.bypass"))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            if (args[1].equalsIgnoreCase(sender.getName())) {
                sendMessage(sender, "&cYou can't give a kit to yourself!");
            }
            else if (getTargetPlayers(sender, args[1]).stream().anyMatch(p -> p.hasPermission("eventtools.bypass"))) {
                sendMessage(sender, "&cYou can't give a kit to that player!");
            }
            else {
                sendMessage(sender, "&cNo matching players found!");
            }
            return true;
        }

        for (Player player : targets) {
            giveKit(player, kitName);
            sendMessage(player, "&aYou received the " + kitName + " kit!");
        }

        String targetName;
        if (args[1].startsWith("@")) {
            targetName = "team " + args[1].substring(1);
        } else {
            targetName = args[1].matches("all|alive|eliminated") ?
                    targets.size() + " players" : args[1];
        }

        sendMessage(sender, String.format("&eGave %s kit to %s!", kitName, targetName));
        return true;
    }

    private final Map<String, BukkitTask> activeResizeTasks = new HashMap<>();

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
            case "join":
                return handleTeamJoin(sender, args);
            case "leave":
                return handleTeamLeave(sender, args);
            case "color":
                return handleTeamColor(sender, args);
            case "info":
                return handleTeamInfo(sender);
            case "settings":
                return handleTeamSettings(sender, args);
            default:
                sendTeamHelp(sender);
                return true;
        }
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /team create <name> <color>");
            sendMessage(sender, "&7Available colors: " + getColorList());
            return true;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());

            if (!isColorCode(color)) {
                sendMessage(sender, "&c" + args[2] + " is not a valid color! Use: " + getColorList());
                return true;
            }

            if (teamManager.createTeam(args[1], color)) {
                sendMessage(sender, "&eCreated team " + color + args[1]);
            } else {
                sendMessage(sender, "&cMax teams reached (16) or team already exists!");
            }
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid color! Use: " + getColorList());
        }
        return true;
    }

    private boolean handleTeamDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /team delete <name>");
            return true;
        }

        if (teamManager.deleteTeam(args[1])) {
            sendMessage(sender, "&eDeleted team " + args[1]);

            if (teamManager.getTeamNames().size() == 1) {
                String lastTeamName = teamManager.getTeamNames().get(0);
                if (teamManager.deleteTeam(lastTeamName)) {
                    sendMessage(sender, "&aAutomatically deleted last remaining team: &e" + lastTeamName);
                }
            }
        } else {
            sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    private boolean handleTeamAssign(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /team assign <player> <team>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMessage(sender, "&cPlayer not found!");
            return true;
        }

        if (target.hasPermission("eventtools.bypass")) {
            sendMessage(sender, "&cYou can't assign that player to teams!");
            return true;
        }

        if (teamManager.addToTeam(target, args[2])) {
            sendMessage(sender, "&eAssigned " + target.getName() + " to " + args[2]);
            sendMessage(target, "&aYou've been assigned to team " + args[2]);
        } else {
            sendMessage(sender, "&cTeam not found!");
        }
        return true;
    }

    private boolean handleTeamJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }

        if (eventActive) {
            sendMessage(sender, "&cYou cannot join teams during an active event!");
            return true;
        }

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /team join <team>");
            return true;
        }

        Player player = (Player) sender;
        if (player.hasPermission("eventtools.bypass")) {
            sendMessage(sender, "&You cannot join teams!");
            return true;
        }

        Team team = teamManager.getTeam(args[1]);
        if (team == null) {
            sendMessage(sender, "&cTeam not found!");
            return true;
        }

        Optional<Team> currentTeam = teamManager.getPlayerTeam(player);
        if (currentTeam.isPresent()) {
            Team oldTeam = currentTeam.get();
            oldTeam.removeMember(player);
            sendMessage(sender, "&aYou left team " + oldTeam.getColor() + oldTeam.getName());
        }

        if (teamManager.addToTeam(player, args[1])) {
            sendMessage(sender, "&aYou joined team " + team.getColor() + team.getName());
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
            return true;
        }

        sendMessage(sender, "&cCould not join team!");
        return true;
    }

    private boolean handleTeamLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cOnly players can use this command!");
            return true;
        }

        if (eventActive) {
            sendMessage(sender, "&cYou cannot leave teams during an active event!");
            return true;
        }

        Player player = (Player) sender;
        if (player.hasPermission("eventtools.bypass")) {
            sendMessage(sender, "&cYou cannot leave teams!");
            return true;
        }

        Optional<Team> currentTeam = teamManager.getPlayerTeam(player);
        if (!currentTeam.isPresent()) {
            sendMessage(sender, "&cYou are not in any team!");
            return true;
        }

        Team team = currentTeam.get();
        team.removeMember(player);
        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
        sendMessage(sender, "&aYou have left team " + team.getColor() + team.getName());
        return true;
    }

    private boolean handleTeamColor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
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
            sendMessage(sender, "&eTeam color updated!");
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid color! Use: " + Arrays.toString(ChatColor.values()));
        }
        return true;
    }

    private boolean handleTeamSettings(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (args.length < 4) {
            sendMessage(sender, "&cUsage: /team settings <team|all> <property> <true|false>");
            return true;
        }
        if (!Arrays.asList("friendlyfire", "nametags", "collision").contains(args[2].toLowerCase())) {
            sendMessage(sender, "&cInvalid property! Use: friendlyfire, nametags, collision");
            return true;
        }

        String teamName = args[1];
        String property = args[2].toLowerCase();
        boolean value = args[3].equalsIgnoreCase("true");

        if (teamName.equalsIgnoreCase("all")) {
            teamManager.getAllTeams().forEach(team -> {
                switch (property) {
                    case "friendlyfire": team.setFriendlyFire(value); break;
                    case "nametags": team.setNameTagVisibility(value); break;
                    case "collision": team.setCollisionEnabled(value); break;
                }
            });
            sendMessage(sender, "&eUpdated all teams: " + property + ": " + (value ? "&atrue" : "&cfalse"));
        } else {
            Team team = teamManager.getTeam(teamName);
            ChatColor teamColor = team.getColor();
            if (team == null) {
                sendMessage(sender, "&cTeam not found!");
                return true;
            }
            switch (property) {
                case "friendlyfire": team.setFriendlyFire(value); break;
                case "nametags": team.setNameTagVisibility(value); break;
                case "collision": team.setCollisionEnabled(value); break;
            }
            sendMessage(sender, "&eUpdated team " + teamColor + teamName + "&e's' " + property + ": " + (value ? "&atrue" : "&cfalse"));
        }
        return true;
    }

    private boolean handleTeamInfo(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {
            sendMessage(sender, "&cYou don't have the required permission to run this command!");
            return true;
        }
        if (teamManager.getAllTeams().isEmpty()) {
            sendMessage(sender, "&7No teams available. Use &e/team create &7to create some!");
            return true;
        }

        StringBuilder message = new StringBuilder("&6Teams:\n");
        teamManager.getAllTeams().forEach(team -> {
            message.append(team.getColor())
                    .append(team.getName())
                    .append(" &7(")
                    .append(team.size())
                    .append(" Member").append(team.size() != 1 ? "s" : "").append(")");

            message.append("\n&8> &7Settings: ");
            message.append("Friendly Fire: ").append(team.friendlyFire ? "&atrue" : "&cfalse");
            message.append("&7, Collision: ").append(team.collisionEnabled ? "&atrue" : "&cfalse");
            message.append("&7, Nametags: ").append(team.nameTagVisibility ? "&atrue" : "&cfalse");

            message.append("\n&8> &7Members: ");
            if (team.getMembers().isEmpty()) {
                message.append("&7None");
            } else {
                message.append(team.getMembers().stream()
                        .map(uuid -> {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                            String name = player.getName() != null ? player.getName() : "Unknown";
                            boolean isOnline = player.isOnline();
                            boolean isEliminated = isEliminated(uuid);

                            return (isOnline ? "&7" : "&8") +
                                    (isEliminated ? "&m" + name + "&r" : name);
                        })
                        .collect(Collectors.joining("&7,&r ")));
            }
            message.append("\n");
        });

        sendMessage(sender, message.toString());
        return true;
    }

    private void sendTeamHelp(CommandSender sender) {
        if (!sender.hasPermission("eventtools.admin")) {

            sendMessage(sender, "&6Team Commands:\n" +
                    "&e/team join <team> &7- Join a team\n" +
                    "&e/team leave &7- Leave a team\n");
            return;
        }
        sendMessage(sender, "&6Team Commands:\n" +
                "&e/team create <name> <color> &7- Create a new team\n" +
                "&e/team delete <name> &7- Delete a team\n" +
                "&e/team assign <player> <team> &7- Assign a player to a team\n" +
                "&e/team color <name> <color> &7- Change team color\n" +
                "&e/team info &7- Show detailed team info\n" +
                "&e/team settings &7- Modify settings of teams\n");
    }

    private boolean isColorCode(ChatColor color) {
        return color.isColor() && !color.isFormat();
    }

    private String getColorList() {
        return Arrays.stream(ChatColor.values())
                .filter(this::isColorCode)
                .map(color -> color + color.name().toLowerCase())
                .collect(Collectors.joining("&7, "));
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
                broadcastMessage(playerTeam.getColor() + playerTeam.getName() + " &chas been fully eliminated!");
            }
            broadcastMessage(playerTeam.getColor() + playerTeam.getName() +
                    " &7> &c" + player.getName() + " has been eliminated!");
        } else {
            broadcastMessage("&c" + player.getName() + " has been eliminated!");
        }

        checkForEventEnd();
    }

    private void checkForEventEnd() {
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

                    Color randomColor = Color.fromRGB(
                            random.nextInt(256),
                            random.nextInt(256),
                            random.nextInt(256)
                    );

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

    private boolean revivePlayer(CommandSender sender, Player player) {
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
            handleElimination(player);
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
                sendMessage(player, "&7Your &aYES &7vote has been counted!");
                playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
                event.setCancelled(true);
            }
            else if (message.equals("no") || message.equals("n") || message.equals("disagree")) {
                votes.put(player.getUniqueId(), false);
                sendMessage(player, "&7Your &aNO &7vote has been counted!");
                playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
                event.setCancelled(true);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("eventtools.admin");
        String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (!isAdmin && !cmd.getName().equalsIgnoreCase("team")) {
            return completions;
        }

        switch (cmd.getName().toLowerCase()) {
            case "team":
                if (args.length == 1) {
                    List<String> commands = new ArrayList<>();
                    commands.add("join");
                    commands.add("leave");

                    if (isAdmin) {
                        commands.addAll(Arrays.asList("create", "delete", "assign", "color", "info", "settings"));
                    }

                    return filterCompletions(commands, args[0]);
                }
                else if (args.length == 2) {
                    switch (args[0].toLowerCase()) {
                        case "join":
                            return filterCompletions(teamManager.getTeamNames(), args[1]);
                        case "leave":
                            if (sender instanceof Player) {
                                return filterCompletions(
                                        teamManager.getPlayerTeam((Player)sender)
                                                .map(team -> Collections.singletonList(team.getName()))
                                                .orElse(Collections.emptyList()),
                                        args[1]
                                );
                            }
                            break;
                        case "create":
                            if (isAdmin) return filterCompletions(Collections.singletonList("<name>"), args[1]);
                            break;
                        case "delete":
                        case "color":
                            if (isAdmin) return filterCompletions(teamManager.getTeamNames(), args[1]);
                            break;
                        case "assign":
                            if (isAdmin) return filterCompletions(getOnlinePlayerNames(), args[1]);
                            break;
                        case "settings":
                            if (isAdmin) {
                                List<String> options = new ArrayList<>(teamManager.getTeamNames());
                                options.add("all");
                                return filterCompletions(options, args[1]);
                            }
                            break;
                    }
                }
                else if (args.length == 3 && isAdmin) {
                    switch (args[0].toLowerCase()) {
                        case "create":
                        case "color":
                            return filterCompletions(
                                    Arrays.stream(ChatColor.values())
                                            .filter(ChatColor::isColor)
                                            .map(color -> color.name().toLowerCase())
                                            .collect(Collectors.toList()),
                                    args[2]
                            );
                        case "assign":
                            return filterCompletions(teamManager.getTeamNames(), args[2]);
                        case "settings":
                            return filterCompletions(Arrays.asList("friendlyfire", "nametags", "collision"), args[2]);
                    }
                }
                else if (args.length == 4 && isAdmin && args[0].equalsIgnoreCase("settings")) {
                    return filterCompletions(Arrays.asList("true", "false"), args[3]);
                }
                break;

            default:
                if (isAdmin) {
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
                                List<String> zoneCommands = new ArrayList<>(Arrays.asList(
                                        "create", "delete", "info", "toggle",
                                        "movehere", "resize"
                                ));
                                return filterCompletions(zoneCommands, args[0]);
                            }
                            else if (args.length == 2) {
                                switch (args[0].toLowerCase()) {
                                    case "create":
                                        return filterCompletions(Collections.singletonList("<name>"), args[1]);
                                    case "delete":
                                    case "toggle":
                                    case "movehere":
                                    case "resize":
                                        return filterCompletions(zoneManager.getZoneNames(), args[1]);
                                }
                            }
                            else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
                                return filterCompletions(Arrays.asList("circle", "square"), args[2]);
                            }
                            else if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
                                if (args[2].equalsIgnoreCase("circle") || args[2].equalsIgnoreCase("square")) {
                                    return filterCompletions(Collections.singletonList("<radius>"), args[3]);
                                }
                            }
                            else if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
                                return filterCompletions(Arrays.asList(
                                        "effect", "must_stay", "safe",
                                        "team_only", "damage"
                                ), args[4]);
                            }
                            else if (args.length == 6 && args[0].equalsIgnoreCase("create")) {
                                switch (args[4].toLowerCase()) {
                                    case "effect":
                                        return filterCompletions(
                                                Arrays.stream(PotionEffectType.values())
                                                        .map(e -> e.getName().toLowerCase())
                                                        .collect(Collectors.toList()),
                                                args[5]
                                        );
                                    case "team_only":
                                        return filterCompletions(teamManager.getTeamNames(), args[5]);
                                    case "damage":
                                        return filterCompletions(Arrays.asList("1", "2", "3", "4", "5"), args[5]);
                                }
                            }
                            else if (args.length == 3 && args[0].equalsIgnoreCase("resize")) {
                                return filterCompletions(Collections.singletonList("<radius>"), args[2]);
                            }
                            else if (args.length == 4 && args[0].equalsIgnoreCase("resize")) {
                                return filterCompletions(Collections.singletonList("[stepCount]"), args[3]);
                            }
                            break;

                        case "seteventspawn":
                        case "stopevent":
                        case "mutechat":
                        case "clearchat":
                        case "endvote":
                            break;
                    }
                }
                break;
        }

        return filterCompletions(completions, currentArg);
    }

    private void addTargetCompletions(List<String> completions) {
        completions.addAll(Arrays.asList("all", "alive", "eliminated"));
        completions.addAll(getOnlinePlayerNames());
        teamManager.getTeamNames().forEach(name -> completions.add("@" + name));
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