package ti4;

import static org.reflections.scanners.Scanners.SubTypes;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ti4.commands.CommandManager;
import ti4.cron.AutoPingCron;
import ti4.cron.CloseLaunchThreadsCron;
import ti4.cron.CronManager;
import ti4.cron.EndOldGamesCron;
import ti4.cron.FastScFollowCron;
import ti4.cron.InteractionLogCron;
import ti4.cron.LogButtonRuntimeStatisticsCron;
import ti4.cron.LogCacheStatsCron;
import ti4.cron.OldUndoFileCleanupCron;
import ti4.cron.ReuploadStaleEmojisCron;
import ti4.cron.SabotageAutoReactCron;
import ti4.cron.TechSummaryCron;
import ti4.cron.UploadStatsCron;
import ti4.cron.WinningPathCron;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.listeners.AutoCompleteListener;
import ti4.listeners.BotRuntimeStatsListener;
import ti4.listeners.ButtonListener;
import ti4.listeners.ChannelCreationListener;
import ti4.listeners.DeletionListener;
import ti4.listeners.MessageListener;
import ti4.listeners.ModalListener;
import ti4.listeners.SelectionMenuListener;
import ti4.listeners.SlashCommandListener;
import ti4.listeners.UserJoinServerListener;
import ti4.listeners.UserLeaveServerListener;
import ti4.map.persistence.GameManager;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogBufferManager;
import ti4.migration.DataMigrationManager;
import ti4.selections.SelectionManager;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.statistics.StatisticsPipeline;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;

@SpringBootApplication
public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();
    public static final Set<Role> adminRoles = new HashSet<>();
    public static final Set<Role> developerRoles = new HashSet<>();
    public static final Set<Role> bothelperRoles = new HashSet<>();

    public static JDA jda;
    public static String guildPrimaryID;
    public static boolean testingMode;
    public static Guild guildPrimary;
    private static Guild guildSecondary;
    private static Guild guildTertiary;
    private static Guild guildQuaternary;
    private static Guild guildQuinary;
    private static Guild guildSenary;
    private static Guild guildSeptenary;
    private static Guild guildOctonary;
    public static Guild guildFogOfWar;
    public static Guild guildCommunityPlays;
    private static Guild guildMegagame;
    public static final Set<Guild> guilds = new HashSet<>();
    public static final List<Guild> serversToCreateNewGamesOn = new ArrayList<>();
    public static final List<Guild> fowServers = new LinkedList<>();

    private static final List<Class<?>> classes = new ArrayList<>();

    public static void main(String[] args) {
        SpringApplication.run(AsyncTI4DiscordBot.class, args);

        // guildPrimaryID must be set before initializing listeners that use webhook logging
        guildPrimaryID = args[2];

        GlobalSettings.loadSettings();
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
        jda = JDABuilder.createDefault(args[0])
                // This is a privileged gateway intent that is used to update user information and join/leaves
                // (including kicks).
                // This is required to cache all members of a guild (including chunking)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                // This is a privileged gateway intent this is only used to enable access to the user content in
                // messages
                // (also including embeds/attachments/components).
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                // not 100 sure this is needed? It may be for the Emoji cache... but do we actually need that?
                .enableIntents(GatewayIntent.GUILD_EXPRESSIONS)
                // It *appears* we need to pull all members or else the bot has trouble pinging players
                // but that may be a misunderstanding, in case we want to try to use an LRU cache in the future
                // and avoid loading every user at startup
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                // This allows us to use our own ShutdownHook, created below
                .setEnableShutdownHook(false)
                .build();

        jda.addEventListener(
                new BotRuntimeStatsListener(),
                new MessageListener(),
                new DeletionListener(),
                new ChannelCreationListener(),
                new SlashCommandListener(),
                ButtonListener.getInstance(),
                ModalListener.getInstance(),
                new SelectionMenuListener(),
                new UserJoinServerListener(),
                new UserLeaveServerListener(),
                new AutoCompleteListener());

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            BotLogger.error("Error waiting for bot to get ready", e);
        }

        jda.getPresence()
                .setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("STARTING UP: Connecting to Servers"));

        BotLogger.info("# `" + new Timestamp(System.currentTimeMillis()) + "`  BOT IS STARTING UP");

        // Primary HUB Server
        guildPrimary = jda.getGuildById(args[2]);
        boolean success = startBot(guildPrimary);

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = jda.getGuildById(args[3]);
            success &= startBot(guildCommunityPlays);
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = jda.getGuildById(args[4]);
            success &= startBot(guildFogOfWar);
            fowServers.add(guildFogOfWar);
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = jda.getGuildById(args[5]);
            success &= startBot(guildSecondary);
            serversToCreateNewGamesOn.add(guildSecondary);
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guildTertiary = jda.getGuildById(args[6]);
            success &= startBot(guildTertiary);
            serversToCreateNewGamesOn.add(guildTertiary);
        }

        // Async: War Sun Tzu
        if (args.length >= 8) {
            guildQuaternary = jda.getGuildById(args[7]);
            success &= startBot(guildQuaternary);
            serversToCreateNewGamesOn.add(guildQuaternary);
        }

        // Async: Fighter Club
        if (args.length >= 9) {
            guildQuinary = jda.getGuildById(args[8]);
            success &= startBot(guildQuinary);
            serversToCreateNewGamesOn.add(guildQuinary);
        }

        // Async: Tommer Hawk
        if (args.length >= 10) {
            guildSenary = jda.getGuildById(args[9]);
            success &= startBot(guildSenary);
            serversToCreateNewGamesOn.add(guildSenary);
        }

        // Async: Duder's Domain
        if (args.length >= 11) {
            guildSeptenary = jda.getGuildById(args[10]);
            success &= startBot(guildSeptenary);
            serversToCreateNewGamesOn.add(guildSeptenary);
        }

        // Async: What's up Dock
        if (args.length >= 12) {
            guildOctonary = jda.getGuildById(args[11]);
            success &= startBot(guildOctonary);
            serversToCreateNewGamesOn.add(guildOctonary);
        }

        // Async: Megagame server
        if (args.length >= 13) {
            guildMegagame = jda.getGuildById(args[12]);
            success &= startBot(guildMegagame);
            // serversToCreateNewGamesOn.add(guildMegagame);  // Don't create random games on this server
        }

        // Async: FOW Chapter Secondary
        // if (args.length >= 13) {
        //    guildFogOfWarSecondary = jda.getGuildById(args[12]);
        //    startBot(guildFogOfWarSecondary);
        //    fowServers.add(guildFogOfWarSecondary);
        // }

        if (!success) {
            BotLogger.info("Failed to start the bot on one or more servers. Aborting.");
            BotLogger.warning("Failed to start the bot on one or more servers. Aborting.");
            BotLogger.error("Failed to start the bot on one or more servers. Aborting.");
            return;
        }

        // Attempt to start a "Search Only" version of the bot on eligible servers
        for (Guild searchGuild : jda.getGuilds()) {
            if (guilds.stream().anyMatch(g -> g.getId().equals(searchGuild.getId()))) continue;
            startBotSearchOnly(searchGuild);
        }

        // LOAD DATA
        BotLogger.info("LOADING DATA");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Data"));
        ApplicationEmojiService.uploadNewEmojis();
        // load all /resources/planets/ and /resources/systems/ .json files, into 3 HashMaps (not 2)
        TileHelper.init();
        // load all /resources/positions/ .properties files, each into 1 Properties
        PositionMapper.init();
        // load all /resources/data/ .json and .properties files, except logging.properties, each into 1 HashMap or
        // Properties
        Mapper.init();
        // load all /resources/alias/ .properties files, except position_alias_old.properties, into
        AliasHandler.init();
        // create directories for games files
        Storage.init();
        SelectionManager.init();
        initializeWhitelistedRoles();
        TIGLHelper.validateTIGLness();

        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Games"));

        // LOAD GAMES NAMES
        BotLogger.info("LOADING GAMES");
        GameManager.initialize(); // load games, into 2 ConcurrentHashMaps: 1 for games and 1 for players

        // RUN DATA MIGRATIONS
        if (DataMigrationManager.runMigrations()) {
            BotLogger.info("RAN MIGRATIONS");
        }

        // START CRONS
        AutoPingCron.register();
        ReuploadStaleEmojisCron.register();
        LogCacheStatsCron.register();
        WinningPathCron.register();
        UploadStatsCron.register();
        OldUndoFileCleanupCron.register();
        EndOldGamesCron.register();
        LogButtonRuntimeStatisticsCron.register();
        TechSummaryCron.register();
        SabotageAutoReactCron.register();
        FastScFollowCron.register();
        CloseLaunchThreadsCron.register();
        LogBufferManager.initialize();
        InteractionLogCron.register();

        // BOT IS READY
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, true);
        jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing("Async TI4"));
        updatePresence();
        BotLogger.info("FINISHED LOADING GAMES");

        // Register Shutdown Hook to run when SIGTERM is received from docker stop
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jda.getPresence()
                        .setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("BOT IS SHUTTING DOWN"));
                BotLogger.info("SHUTDOWN PROCESS STARTED");
                GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
                BotLogger.info("NO LONGER ACCEPTING COMMANDS");
                if (ExecutorServiceManager.shutdown()) { // will wait for up to an additional 20 seconds
                    BotLogger.info("FINISHED PROCESSING ASYNC THREADPOOL");
                } else {
                    BotLogger.info("DID NOT FINISH PROCESSING ASYNC THREADPOOL");
                }
                if (MapRenderPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                    BotLogger.info("FINISHED RENDERING MAPS");
                } else {
                    BotLogger.info("DID NOT FINISH RENDERING MAPS");
                }
                if (StatisticsPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                    BotLogger.info("FINISHED PROCESSING STATISTICS");
                } else {
                    BotLogger.info("DID NOT FINISH PROCESSING STATISTICS");
                }
                CronManager.shutdown(); // will wait for up to an additional 20 seconds
                LogBufferManager.sendBufferedLogsToDiscord(); // will drain the log buffer and doesn't have a timeout
                BotLogger.info("SHUTDOWN PROCESS COMPLETE");
                TimeUnit.SECONDS.sleep(1); // wait for BotLogger
                jda.shutdown();
                jda.awaitShutdown(30, TimeUnit.SECONDS);
                mainThread.join();
            } catch (Exception e) {
                BotLogger.error("Error encountered within shutdown hook:\n> ", e);
            }
        }));
    }

    private static boolean startBot(Guild guild) {
        if (guild == null) {
            return false;
        }
        try {
            CommandListUpdateAction commands = guild.updateCommands();
            CommandManager.getCommands().forEach(command -> command.register(commands));
            commands.queue();
            BotLogger.info("BOT STARTED UP: " + guild.getName());
            guilds.add(guild);
        } catch (Exception e) {
            BotLogger.error("\n# FAILED TO START BOT ", e);
        }
        return true;
    }

    private static boolean startBotSearchOnly(Guild guild) {
        // Do not set up search commands for test bots, and definitely never for the hub server, which several test bots
        // are still in
        if (guild == null) return false;
        if (System.getenv("TESTING") != null) return false;
        if (guild.getId().equals(Constants.ASYNCTI4_HUB_SERVER_ID)) return false;

        // Disable this for now
        if (true) return false;

        try {
            CommandListUpdateAction commands = guild.updateCommands();
            CommandManager.getCommands().forEach(command -> command.registerSearchCommands(commands));
            commands.queue();
            BotLogger.info("SEARCH-ONLY BOT STARTED UP: " + guild.getName());
            guilds.add(guild);
        } catch (Exception e) {
            BotLogger.error("\n# SEARCH-ONLY BOT FAILED TO START: " + guild.getName(), e);
        }
        return true;
    }

    public static void updatePresence() {
        long activeGames = GameManager.getActiveGameCount();
        jda.getPresence().setActivity(Activity.playing(activeGames + " games of Async TI4"));
    }

    /**
     * Initializes the whitelisted roles for the bot, including admin, developer, and bothelper roles.
     * <ul>
     * <li>Admins may execute /admin, /developer, and /bothelper commands</li>
     * <li>Developers may execute /developer commands</li>
     * <li>Bothelpers may execute /bothelper commands</li>
     * </ul>
     *
     * Add your test server's role ID to enable access to these commands on your server
     */
    private static void initializeWhitelistedRoles() {
        // ADMIN ROLES

        adminRoles.add(jda.getRoleById("943596173896323072")); // Async Primary (Hub)
        adminRoles.add(jda.getRoleById("1090914497352446042")); // Async Secondary (Stroter's Paradise)
        adminRoles.add(jda.getRoleById("1146511484264906814")); // Async Tertiary (Dreadn't)
        adminRoles.add(jda.getRoleById("1176104225978204167")); // Async Quaternary (War Sun Tzu)
        adminRoles.add(jda.getRoleById("1209956332380229678")); // Async Quinary (Fighter Club)
        adminRoles.add(jda.getRoleById("1250131684393881616")); // Async Senary (Tommer Hawk)
        adminRoles.add(jda.getRoleById("1312882116597518422")); // Async Septenary (Duder's Domain)
        adminRoles.add(jda.getRoleById("1378702133297414170")); // Async Octonary (What's up Dock)
        adminRoles.add(jda.getRoleById("1062804021385105500")); // FoW Server
        adminRoles.add(jda.getRoleById("951230650680225863")); // Community Server
        adminRoles.add(jda.getRoleById("1218342096474341396")); // Megagame Server
        adminRoles.add(jda.getRoleById("1067866210865250445")); // PrisonerOne's Test Server
        adminRoles.add(jda.getRoleById("1060656344581017621")); // Softnum's Server
        adminRoles.add(jda.getRoleById("1109657180170371182")); // Jazz's Server
        adminRoles.add(jda.getRoleById("1100120742093406319")); // Moo's Server
        adminRoles.add(jda.getRoleById("1126610851034583050")); // Fin's Server
        adminRoles.add(jda.getRoleById("824111008863092757")); // Fireseal's Server
        adminRoles.add(jda.getRoleById("336194595501244417")); // tedw4rd's Server
        adminRoles.add(jda.getRoleById("1178659621225889875")); // Jepp2078's Server
        adminRoles.add(jda.getRoleById("1215451631622164610")); // Sigma's Server
        adminRoles.add(jda.getRoleById("1225597324206800996")); // ForlornGeas's Server
        adminRoles.add(jda.getRoleById("1226068025464197160")); // Rintsi's Server
        adminRoles.add(jda.getRoleById("1226805374007640095")); // Solax's Server
        adminRoles.add(jda.getRoleById("1313965793532186725")); // ppups's Server
        adminRoles.add(jda.getRoleById("1311111853912358922")); // TSI's Server
        adminRoles.add(jda.getRoleById("1368344911103000728")); // gozer's server (marshmallow manosphere)
        adminRoles.add(jda.getRoleById("1378475691531567185")); // Hadouken's Server
        adminRoles.add(jda.getRoleById("1149705227625316352")); // Will's server
        adminRoles.removeIf(Objects::isNull);

        // DEVELOPER ROLES

        developerRoles.addAll(adminRoles); // admins may also execute developer commands
        developerRoles.add(jda.getRoleById("947648366056185897")); // Async Primary (Hub)
        developerRoles.add(jda.getRoleById("1090958278479052820")); // Async Secondary (Stroter's Paradise)
        developerRoles.add(jda.getRoleById("1146529125184581733")); // Async Tertiary (Dreadn't)
        developerRoles.add(jda.getRoleById("1176104225978204166")); // Async Quaternary (War Sun Tzu)
        developerRoles.add(jda.getRoleById("1209956332380229677")); // Async Quinary (Fighter Club)
        developerRoles.add(jda.getRoleById("1250131684393881615")); // Async Senary (Tommer Hawk)
        developerRoles.add(jda.getRoleById("1312882116597518421")); // Async Septenary (Duder's Domain)
        developerRoles.add(jda.getRoleById("1378702133297414169")); // Async Octonary (What's up Dock)
        developerRoles.add(jda.getRoleById("1088532767773564928")); // FoW Server
        developerRoles.add(jda.getRoleById("1395072365389680711")); // Megagame Server
        developerRoles.add(jda.getRoleById("1215453013154734130")); // Sigma's Server
        developerRoles.add(jda.getRoleById("1225597362186223746")); // ForlornGeas's Server
        developerRoles.add(jda.getRoleById("1226068105071956058")); // Rintsi's Server
        developerRoles.add(jda.getRoleById("1226805601422676069")); // Solax's Server
        developerRoles.add(jda.getRoleById("1313966002551128166")); // ppups's Server
        developerRoles.add(jda.getRoleById("1311111944832553090")); // TSI's Server
        developerRoles.add(jda.getRoleById("1368344979579338762")); // gozer's server (marshmallow manosphere)
        developerRoles.add(jda.getRoleById("1378475796301217792")); // Hadouken's Server
        developerRoles.add(jda.getRoleById("1406188584163213332")); // Will's server
        developerRoles.removeIf(Objects::isNull);

        // BOTHELPER ROLES

        bothelperRoles.addAll(developerRoles); // developers may also execute bothelper commands
        bothelperRoles.addAll(adminRoles); // admins can also execute bothelper commands
        bothelperRoles.add(jda.getRoleById("1166011604488425482")); // Async Primary (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary (Stroter's Paradise)
        bothelperRoles.add(jda.getRoleById("1146539257725464666")); // Async Tertiary (Dreadn't)
        bothelperRoles.add(jda.getRoleById("1176104225978204164")); // Async Quaternary (War Sun Tzu)
        bothelperRoles.add(jda.getRoleById("1209956332380229675")); // Async Quinary (Fighter Club)
        bothelperRoles.add(jda.getRoleById("1250131684393881613")); // Async Senary (Tommer Hawk)
        bothelperRoles.add(jda.getRoleById("1312882116597518419")); // Async Septenary (Duder's Domain)
        bothelperRoles.add(jda.getRoleById("1378702133297414167")); // Async Octonary (What's up Dock)
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.add(jda.getRoleById("1063464689218105354")); // FoW Server Game Admin
        bothelperRoles.add(jda.getRoleById("1248693989193023519")); // Community Server
        bothelperRoles.add(jda.getRoleById("1395072619417436183")); // Megagame Server
        bothelperRoles.add(jda.getRoleById("1225597399385374781")); // ForlornGeas's Server
        bothelperRoles.add(jda.getRoleById("1131925041219653714")); // Jonjo's Server
        bothelperRoles.add(jda.getRoleById("1215450829096624129")); // Sigma's Server
        bothelperRoles.add(jda.getRoleById("1226068245010710558")); // Rintsi's Server
        bothelperRoles.add(jda.getRoleById("1226805674046914560")); // Solax's Server
        bothelperRoles.add(jda.getRoleById("1313965956338417784")); // ppups's Server
        bothelperRoles.add(jda.getRoleById("1311112004089548860")); // TSI's Server
        bothelperRoles.add(jda.getRoleById("1368345023745097898")); // gozer's server (marshmallow manosphere)
        bothelperRoles.add(jda.getRoleById("1378475822528204901")); // Hadouken's Server
        bothelperRoles.add(jda.getRoleById("1150031360610799676")); // Will's server
        bothelperRoles.removeIf(Objects::isNull);
    }

    public static String getBotId() {
        return jda.getSelfUser().getId();
    }

    public static boolean isReadyToReceiveCommands() {
        return GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.READY_TO_RECEIVE_COMMANDS.toString(), Boolean.class, false);
    }

    public static List<Category> getAvailablePBDCategories() {
        return guilds.stream()
                .flatMap(guild -> guild.getCategories().stream())
                .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
                .toList();
    }

    public static List<Class<?>> getAllClasses() {
        if (classes.isEmpty()) {
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forJavaClassPath())
                    .setScanners(new SubTypesScanner(false)));
            reflections.get(SubTypes.of(Object.class).asClass()).stream()
                    .filter(c -> c.getPackageName().startsWith("ti4"))
                    .forEach(classes::add);
        }
        return classes;
    }

    public static boolean isValidGuild(String guildId) {
        return guilds.stream().anyMatch(g -> g.getId().equals(guildId));
    }
}
