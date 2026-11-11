package ti4.discord;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.commons.lang3.function.Consumers;
import ti4.AsyncTI4DiscordBot;
import ti4.contest.cron.CombatReplayCron;
import ti4.contest.cron.CombatReplayPromotionCron;
import ti4.contest.cron.CombatReplayPromotionScoreBackfillCron;
import ti4.contest.cron.CombatReplaySelectionCron;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.cron.AutoPingCron;
import ti4.cron.BothelperDashboardCron;
import ti4.cron.CardsInfoPinCleanupCron;
import ti4.cron.CategoryCleanupCron;
import ti4.cron.CloseLaunchThreadsCron;
import ti4.cron.CronManager;
import ti4.cron.DiskUsageCron;
import ti4.cron.EndOldGamesCron;
import ti4.cron.FastScFollowCron;
import ti4.cron.FlushUserActiveHoursCron;
import ti4.cron.GameDatabaseReconciliationCron;
import ti4.cron.GameMessageCleanupCron;
import ti4.cron.InteractionLogCron;
import ti4.cron.KeepThreadsAliveCron;
import ti4.cron.LogButtonRuntimeStatisticsCron;
import ti4.cron.LogCacheStatsCron;
import ti4.cron.LongExecutionHistoryCron;
import ti4.cron.MatchmakerCron;
import ti4.cron.OldUndoFileCleanupCron;
import ti4.cron.ReuploadStaleEmojisCron;
import ti4.cron.SabotageAutoReactCron;
import ti4.cron.TechSummaryCron;
import ti4.cron.UploadRecentStatsCron;
import ti4.cron.UploadStatsCron;
import ti4.cron.WinningPathCron;
import ti4.discord.interactions.commands.SlashCommandManager;
import ti4.discord.interactions.context.ContextCommandManager;
import ti4.discord.interactions.listeners.ListenerManager;
import ti4.discord.interactions.selections.SelectionManager;
import ti4.executors.ExecutorServiceManager;
import ti4.executors.ExecutorUtility;
import ti4.executors.ShutdownResult;
import ti4.game.persistence.GameDatabaseSyncPipeline;
import ti4.game.persistence.GameManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
import ti4.helpers.TIGLHelper;
import ti4.image.map.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.TileHelper;
import ti4.service.draft.SliceGenerationPipeline;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.statistics.StatisticsPipeline;
import ti4.settings.GlobalSettings;
import ti4.settings.users.UserActiveHourRecorder;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.websocket.GameWebStatePipeline;

@UtilityClass
public class JdaService {

    private static final String JDA_EVENT_POOL_NAME = "JDA Event Pool";
    static final String FLUSH_USER_ACTIVE_HOURS = "flush user active hours";
    static final String RELEASE_ACTIVE_LEASE = "release active lease";
    private static final ShutdownReport SHUTDOWN_REPORT = new ShutdownReport();
    private static final int EVENT_POOL_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final int JDA_SHUTDOWN_TIMEOUT_SECONDS = 20;
    public static final int DISCORD_REQUEST_TIMEOUT_SECONDS = 60;
    private static final Set<CacheFlag> DISABLED_JDA_CACHE_FLAGS = EnumSet.of(
            // User is playing a game, listening to Spotify, etc.
            CacheFlag.ACTIVITY,
            // User on Desktop, Mobile, or Web? Could be useful for stats
            CacheFlag.CLIENT_STATUS,
            // User is online, idle, etc
            CacheFlag.ONLINE_STATUS,
            // Needed for Role.getTags()
            CacheFlag.ROLE_TAGS,
            CacheFlag.SCHEDULED_EVENTS,
            CacheFlag.SOUNDBOARD_SOUNDS,
            CacheFlag.STICKER,
            CacheFlag.VOICE_STATE);

    // TODO:
    //       we may not want to trust any old "Admin" role on a server
    //       should actually have admin rights
    public static final Set<Role> adminRoles = new HashSet<>();
    public static final Set<Role> developerRoles = new HashSet<>();
    public static final Set<Role> tiglAdminRoles = new HashSet<>();
    public static final Set<Role> bothelperRoles = new HashSet<>();
    private static final Pattern NUMERIC_GUILD_ID_PATTERN = Pattern.compile("\\b[0-9]+\\b");

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
    private static Guild guildNonary;
    private static Guild guildDecenary;
    private static Guild guildUndenary;
    private static Guild guildDuodenary;
    private static Guild guildTredenary;
    private static Guild guildQuadrodenary;
    public static Guild guildFogOfWar;
    private static Guild guildFogOfWarSecondary;
    public static Guild guildCommunityPlays;
    private static Guild guildMegagame;
    private static Guild guildTourney;
    public static final Set<Guild> guilds = new HashSet<>();
    public static final Set<Guild> serversToCreateNewGamesOn = new HashSet<>();
    public static final Set<Guild> fowServers = new HashSet<>();

    private static final ExecutorService EVENT_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            Thread.ofPlatform().name("ti4-jda-event-", 0).factory());

    public static void startJdaAndRegisterListeners(String[] args) {
        BotLogger.info("STARTING JDA");
        RestAction.setDefaultTimeout(DISCORD_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        jda = JDABuilder.createDefault(args[0])
                .setEventPool(EVENT_EXECUTOR)
                .enableIntents(
                        // Needed to listen for joins/leaves
                        // Needed to cache all members of a guild (including chunking) - remove?
                        GatewayIntent.GUILD_MEMBERS,
                        // Needed to parse raw user messages
                        GatewayIntent.MESSAGE_CONTENT,
                        // Needed for emoji searches and validation
                        GatewayIntent.GUILD_EXPRESSIONS)
                // It *appears* we need to pull all members or else the bot has trouble pinging players
                // but that may be a misunderstanding, in case we want to try to use an LRU cache in the future
                // and avoid loading every user at startup
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .disableCache(DISABLED_JDA_CACHE_FLAGS)
                // This allows us to use our own ShutdownHook, created below
                .setEnableShutdownHook(false)
                .build();

        BotLogger.info("INITIALIZING LISTENERS");
        ListenerManager.registerListeners(jda);
    }

    public static boolean awaitJdaReady() {
        BotLogger.info("AWAITING JDA READY");
        try {
            jda.awaitReady();
            return true;
        } catch (Throwable t) {
            BotLogger.critical("Error waiting for bot to get ready", t);
            return false;
        }
    }

    public static boolean initializeGuilds(String[] args) {
        jda.getPresence()
                .setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("STARTING UP: Connecting to Servers"));

        BotLogger.info("INITIALIZING SERVERS");

        // Primary HUB Server
        guildPrimaryID = args[2];
        tryToInitGuild(args[2], false);

        if (guildPrimary == null) {
            BotLogger.critical("Failed to start the bot on the primary guild. Aborting.");
            return false;
        }

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = tryToInitGuild(args[3], false);
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = tryToInitGuild(args[4], false);
            if (guildFogOfWar != null) fowServers.add(guildFogOfWar);
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = tryToInitGuild(args[5], true);
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guildTertiary = tryToInitGuild(args[6], true);
        }

        // Async: War Sun Tzu
        if (args.length >= 8) {
            guildQuaternary = tryToInitGuild(args[7], true);
        }

        // Async: Fighter Club
        if (args.length >= 9) {
            guildQuinary = tryToInitGuild(args[8], true);
        }

        // Async: Tommer Hawk
        if (args.length >= 10) {
            guildSenary = tryToInitGuild(args[9], true);
        }

        // Async: Duder's Domain
        if (args.length >= 11) {
            guildSeptenary = tryToInitGuild(args[10], true);
        }

        // Async: What's up Dock
        if (args.length >= 12) {
            guildOctonary = tryToInitGuild(args[11], true);
        }

        // Async: Megagame server
        if (args.length >= 13) {
            guildMegagame = tryToInitGuild(args[12], false);
        }

        // Async: Ship Flag
        if (args.length >= 14) {
            guildNonary = tryToInitGuild(args[13], true);
        }

        // Async: FOW Chapter Secondary
        if (args.length >= 15) {
            guildFogOfWarSecondary = tryToInitGuild(args[14], false);
            if (guildFogOfWarSecondary != null) fowServers.add(guildFogOfWarSecondary);
        }

        // Async: Tournament Server 1
        if (args.length >= 16) {
            guildTourney = tryToInitGuild(args[15], false);
        }

        // Async: Great Carrier Reef
        if (args.length >= 17) {
            guildDecenary = tryToInitGuild(args[16], true);
        }

        // Async: PDStrians
        if (args.length >= 18) {
            guildUndenary = tryToInitGuild(args[17], true);
        }

        // Async: Stroaty McStroatface
        if (args.length >= 19) {
            guildDuodenary = tryToInitGuild(args[18], true);
        }

        // Async: Planetary Duck System
        if (args.length >= 20) {
            guildTredenary = tryToInitGuild(args[19], true);
        }

        // Async: Dannel's Camp Ground
        if (args.length >= 21) {
            guildQuadrodenary = tryToInitGuild(args[20], true);
        }

        if (isProduction()) leaveNonWhitelistedGuilds();

        // Check for and report a missing bot-log webhook
        if (!GlobalSettings.settingExists(GlobalSettings.ImplementedSettings.BOT_LOG_WEBHOOK_URL)) {
            BotLogger.warning(
                    "BOT-LOG WEBHOOK NOT FOUND for Primary GuildID:" + guildPrimaryID
                            + "\nPlease set a valid bot-log Webhook URL using `/developer setting setting_name:bot_log_webhook_url setting_type:string setting_value:<url>`");
        }
        return true;
    }

    public static void loadStaticDataAndResources() {
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Data"));
        ApplicationEmojiService.uploadNewEmojis();
        // load all /resources/planets/ and /resources/systems/ .json files, into 3 HashMaps (not 2)
        TileHelper.init();
        // load all /resources/positions/ .properties files, each into 1 Properties
        MapPositionMapper.init();
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
    }

    public static void registerAndStartCronJobs() {
        AutoPingCron.register();
        ReuploadStaleEmojisCron.register();
        LogCacheStatsCron.register();
        WinningPathCron.register();
        GameDatabaseReconciliationCron.register();
        UploadStatsCron.register();
        UploadRecentStatsCron.register();
        OldUndoFileCleanupCron.register();
        DiskUsageCron.register();
        EndOldGamesCron.register();
        GameMessageCleanupCron.register();
        CardsInfoPinCleanupCron.register();
        LogButtonRuntimeStatisticsCron.register();
        FlushUserActiveHoursCron.register();
        TechSummaryCron.register();
        SabotageAutoReactCron.register();
        FastScFollowCron.register();
        MatchmakerCron.register();
        CloseLaunchThreadsCron.register();
        KeepThreadsAliveCron.register();
        if (CombatContestSettings.isEnabledStatic()) {
            CombatReplaySelectionCron.register();
            CombatReplayPromotionCron.register();
            CombatReplayPromotionScoreBackfillCron.register();
            CombatReplayCron.register();
        }
        InteractionLogCron.register();
        LongExecutionHistoryCron.register();
        CategoryCleanupCron.register();
        BothelperDashboardCron.register();
    }

    public static void markProcessReady() {
        ActiveLeaseService.setCurrentProcessReady(true);
    }

    public static String describeServers() {
        return String.format(
                "%,d SERVERS (%,d FOR NEW GAMES, %,d FOG OF WAR)",
                guilds.size(), serversToCreateNewGamesOn.size(), fowServers.size());
    }

    private static Guild tryToInitGuild(String guildID, boolean addToNewGameServerList) {
        try {
            return initGuild(guildID, addToNewGameServerList);
        } catch (Throwable t) {
            BotLogger.critical("Failed to initialize guild " + guildID + ". Skipping.", t);
            return null;
        }
    }

    private static Guild initGuild(String guildID, boolean addToNewGameServerList) {
        if (!NUMERIC_GUILD_ID_PATTERN.matcher(guildID).matches()) {
            BotLogger.error(
                    "Invalid Guild ID provided: `" + guildID
                            + "` - If this is running in Production, please correct the ID [here](https://github.com/AsyncTI4/TI4_map_generator_bot/settings/variables/actions/GUILDID_LIST)");
            return null;
        }
        Guild guild = jda.getGuildById(guildID);
        if (guild == null) {
            BotLogger.error("JDA FAILED TO FIND GUILD with ID: `" + guildID
                    + "` - please ensure AsyncTI4 is added to that server and has Admin permissions.");
            return null;
        }
        if (!startBot(guild)) {
            BotLogger.error("Failed to start bot for guild: " + guild.getName());
            return null;
        }
        if (addToNewGameServerList) {
            serversToCreateNewGamesOn.add(guild);
        }
        return guild;
    }

    private static boolean startBot(Guild guild) {
        if (guild == null) {
            return false;
        }
        if (guildPrimaryID.equals(guild.getId())) {
            guildPrimary = guild;
            BotLogger.init(); // requires guildPrimary bot-log channel existing
        }
        try {
            CommandListUpdateAction commands = guild.updateCommands();
            SlashCommandManager.getCommands().forEach(command -> command.register(commands));
            ContextCommandManager.getCommands().forEach(cmd -> cmd.register(commands));
            commands.queue(Consumers.nop(), BotLogger::catchRestError);
            guilds.add(guild);
        } catch (Exception e) {
            BotLogger.error("\n# FAILED TO START BOT ", e);
            return false;
        }
        return true;
    }

    public static void updatePresence() {
        long activeGames = GameManager.getActiveGameCount();
        jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing(activeGames + " games of Async TI4"));
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
        adminRoles.add(jda.getRoleById("1410728648817770532")); // Async Nonary (Ship Flag)
        adminRoles.add(jda.getRoleById("1434632452097446046")); // Async Tourney
        adminRoles.add(jda.getRoleById("1434180793139204204")); // Async Decenary (Great Carrier Reef)
        adminRoles.add(jda.getRoleById("1434181175944941655")); // Async Undenary (PDStrians)
        adminRoles.add(jda.getRoleById("1458844879709929540")); // Async Duodenary (Stroaty McStroatface)
        adminRoles.add(jda.getRoleById("1458845770672377997")); // Async Tredenary (Planetary Duck System)
        adminRoles.add(jda.getRoleById("1458845518393246040")); // Async Quadrodenary (Dannel's Camp Ground)
        adminRoles.add(jda.getRoleById("1062804021385105500")); // FoW Server
        adminRoles.add(jda.getRoleById("1429853811899502675")); // FoW Server Chapter 2
        adminRoles.add(jda.getRoleById("951230650680225863")); // Community Server
        adminRoles.add(jda.getRoleById("1218342096474341396")); // Megagame Server
        adminRoles.add(jda.getRoleById("1067866210865250445")); // PrisonerOne's Test Server
        adminRoles.add(jda.getRoleById("1443842222922530929")); // Spice & Thyme's Server
        adminRoles.add(jda.getRoleById("1060656344581017621")); // Softnum's Server
        adminRoles.add(jda.getRoleById("1072366828879355935")); // Jazz's Server
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
        adminRoles.add(jda.getRoleById("1335330636935987343")); // Jabberwocky's server
        adminRoles.add(jda.getRoleById("1465619434839347276")); // Ariel's server
        adminRoles.add(jda.getRoleById("1487725249398308884")); // Balacasi's server
        adminRoles.add(jda.getRoleById("1500012691224395906")); // BEANS's server
        adminRoles.add(jda.getRoleById("1516450864376578238")); // Stabar's Server
        adminRoles.add(jda.getRoleById("1527947707518423150")); // niugnip's Server

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
        developerRoles.add(jda.getRoleById("1410728648817770531")); // Async Nonary (Ship Flag)
        developerRoles.add(jda.getRoleById("1434632452097446045")); // Async Tourney
        developerRoles.add(jda.getRoleById("1434180793139204203")); // Async Decenary (Great Carrier Reef)
        developerRoles.add(jda.getRoleById("1434181175944941654")); // Async Undenary (PDStrians)
        developerRoles.add(jda.getRoleById("1458844879709929538")); // Async Duodenary (Stroaty McStroatface)
        developerRoles.add(jda.getRoleById("1458845770672377995")); // Async Tredenary (Planetary Duck System)
        developerRoles.add(jda.getRoleById("1458845518393246038")); // Async Quadrodenary (Dannel's Camp Ground)
        developerRoles.add(jda.getRoleById("1088532767773564928")); // FoW Server
        developerRoles.add(jda.getRoleById("1429853811882594528")); // FoW Server Chapter 2
        developerRoles.add(jda.getRoleById("1395072365389680711")); // Megagame Server
        developerRoles.add(jda.getRoleById("1172651397397880832")); // PrisonerOne's Test Server
        developerRoles.add(jda.getRoleById("1215453013154734130")); // Sigma's Server
        developerRoles.add(jda.getRoleById("1225597362186223746")); // ForlornGeas's Server
        developerRoles.add(jda.getRoleById("1226068105071956058")); // Rintsi's Server
        developerRoles.add(jda.getRoleById("1226805601422676069")); // Solax's Server
        developerRoles.add(jda.getRoleById("1313966002551128166")); // ppups's Server
        developerRoles.add(jda.getRoleById("1311111944832553090")); // TSI's Server
        developerRoles.add(jda.getRoleById("1368344979579338762")); // gozer's server (marshmallow manosphere)
        developerRoles.add(jda.getRoleById("1378475796301217792")); // Hadouken's Server
        developerRoles.add(jda.getRoleById("1406188584163213332")); // Will's server
        developerRoles.add(jda.getRoleById("1335330959767375902")); // Jabberwocky's server
        developerRoles.add(jda.getRoleById("1465619572718567526")); // Ariel's server
        developerRoles.add(jda.getRoleById("1487725369766449173")); // Balacasi's server
        developerRoles.add(jda.getRoleById("1500012939326001263")); // BEANS's server
        developerRoles.add(jda.getRoleById("1516450864376578238")); // Stabar's Server
        developerRoles.add(jda.getRoleById("1527947972615209041")); // niugnip's Server

        developerRoles.removeIf(Objects::isNull);

        // TIGL ADMIN ROLES

        tiglAdminRoles.add(jda.getRoleById("1440088130261745845")); // TIGL Admin

        tiglAdminRoles.removeIf(Objects::isNull);

        // BOTHELPER ROLES

        bothelperRoles.addAll(developerRoles); // developers may also execute bothelper commands
        bothelperRoles.addAll(adminRoles); // admins can also execute bothelper commands
        bothelperRoles.addAll(tiglAdminRoles); // TIGL admins may also execute bothelper commands
        bothelperRoles.add(jda.getRoleById("1166011604488425482")); // Async Primary (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary (Stroter's Paradise)
        bothelperRoles.add(jda.getRoleById("1146539257725464666")); // Async Tertiary (Dreadn't)
        bothelperRoles.add(jda.getRoleById("1176104225978204164")); // Async Quaternary (War Sun Tzu)
        bothelperRoles.add(jda.getRoleById("1209956332380229675")); // Async Quinary (Fighter Club)
        bothelperRoles.add(jda.getRoleById("1250131684393881613")); // Async Senary (Tommer Hawk)
        bothelperRoles.add(jda.getRoleById("1312882116597518419")); // Async Septenary (Duder's Domain)
        bothelperRoles.add(jda.getRoleById("1378702133297414167")); // Async Octonary (What's up Dock)
        bothelperRoles.add(jda.getRoleById("1410728648817770529")); // Async Nonary (Ship Flag)
        bothelperRoles.add(jda.getRoleById("1434632452097446043")); // Async Tourney
        bothelperRoles.add(jda.getRoleById("1434180793139204201")); // Async Decenary (Great Carrier Reef)
        bothelperRoles.add(jda.getRoleById("1434181175944941652")); // Async Undenary (PDStrians)
        bothelperRoles.add(jda.getRoleById("1458844879709929536")); // Async Duodenary (Stroaty McStroatface)
        bothelperRoles.add(jda.getRoleById("1458845770672377993")); // Async Tredenary (Planetary Duck System)
        bothelperRoles.add(jda.getRoleById("1458845518393246036")); // Async Quadrodenary (Dannel's Camp Ground)
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.add(jda.getRoleById("1063464689218105354")); // FoW Server Game Admin
        bothelperRoles.add(jda.getRoleById("1429853811891241128")); // FoW Server Chapter 2 Bothelper
        bothelperRoles.add(jda.getRoleById("1429853811891241129")); // FoW Server Chapter 2 Game Supervisor
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
        bothelperRoles.add(jda.getRoleById("1335331011147595929")); // Jabberwocky's Server
        bothelperRoles.add(jda.getRoleById("1465619810577678442")); // Ariel's server
        bothelperRoles.add(jda.getRoleById("1487725393673719950")); // Balacasi's server
        bothelperRoles.add(jda.getRoleById("1500013009492246558")); // BEANS's server
        bothelperRoles.add(jda.getRoleById("1516450864376578238")); // Stabar's Server
        bothelperRoles.add(jda.getRoleById("1527947912108183686")); // niugnip's Server

        bothelperRoles.removeIf(Objects::isNull);
    }

    public static String getBotId() {
        return jda.getSelfUser().getId();
    }

    public static boolean isReadyToReceiveCommands() {
        return ActiveLeaseService.isCurrentProcessReady();
    }

    public static List<Category> getAvailablePBDCategories() {
        return guilds.stream()
                .flatMap(guild -> guild.getCategories().stream())
                .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
                .toList();
    }

    public static boolean isValidGuild(String guildId) {
        return guilds.stream().anyMatch(g -> g.getId().equals(guildId));
    }

    @Nullable
    public static String getUsername(String userId) {
        Member member = guildPrimary.getMemberById(userId);
        if (member != null) return member.getEffectiveName();
        User user = jda.getUserById(userId);
        if (user != null) return user.getEffectiveName();
        return null;
    }

    private static void leaveNonWhitelistedGuilds() {
        jda.getGuilds().forEach(JdaService::leaveGuildIfNotWhitelisted);
    }

    public static void leaveGuildIfNotWhitelisted(Guild guild) {
        // if (!isProduction() || isWhitelistedGuild(guild)) return;
        // BotLogger.warning(
        //         "Leaving guild '" + guild.getName() + "' (" + guild.getId() + ") because it isn't whitelisted!");
        // guild.leave().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static boolean isProduction() {
        return Constants.ASYNCTI4_HUB_SERVER_ID.equals(guildPrimaryID);
    }

    private static boolean isWhitelistedGuild(Guild guild) {
        return guilds.stream().anyMatch(whitelistGuild -> whitelistGuild.getId().equals(guild.getId()))
                || Constants.EMOJI_FARM_SERVERS.containsKey(guild.getId());
    }

    public static void shutdown() {
        runShutdownPhases(shutdownPhases(), JdaService::reportFailedShutdownStep);
    }

    static List<List<ShutdownStep>> shutdownPhases() {
        return List.of(
                List.of(new ShutdownStep("mark shutting down", AsyncTI4DiscordBot::markShuttingDown)),
                List.of(new ShutdownStep("set shutdown presence", JdaService::setShutdownPresence)),
                List.of(new ShutdownStep("stop accepting commands", JdaService::stopAcceptingCommands)),
                List.of(executorStep(JDA_EVENT_POOL_NAME, JdaService::shutdownEventExecutor)),
                List.of(
                        executorStep(ExecutorServiceManager.class.getSimpleName(), ExecutorServiceManager::shutdown),
                        executorStep(CronManager.class.getSimpleName(), CronManager::shutdown)),
                List.of(
                        executorStep(SliceGenerationPipeline.class.getSimpleName(), SliceGenerationPipeline::shutdown),
                        new ShutdownStep(FLUSH_USER_ACTIVE_HOURS, JdaService::flushUserActiveHours)),
                List.of(new ShutdownStep(RELEASE_ACTIVE_LEASE, JdaService::releaseActiveLease)),
                List.of(
                        executorStep(MapRenderPipeline.class.getSimpleName(), MapRenderPipeline::shutdown),
                        executorStep(StatisticsPipeline.class.getSimpleName(), StatisticsPipeline::shutdown),
                        executorStep(GameWebStatePipeline.class.getSimpleName(), GameWebStatePipeline::shutdown),
                        executorStep(
                                GameDatabaseSyncPipeline.class.getSimpleName(), GameDatabaseSyncPipeline::shutdown)),
                List.of(new ShutdownStep("send buffered logs", JdaService::sendBufferedLogsBeforeJdaShutdown)),
                List.of(new ShutdownStep("shut down JDA", JdaService::shutdownJda)));
    }

    record ShutdownStep(String name, Runnable action) {}

    static void runShutdownPhases(List<List<ShutdownStep>> phases, BiConsumer<String, Throwable> onFailure) {
        for (List<ShutdownStep> phase : phases) {
            if (phase.size() == 1) {
                runShutdownStep(phase.getFirst(), onFailure);
            } else {
                runTogether(phase, onFailure);
            }
        }
    }

    private static void runShutdownStep(ShutdownStep step, BiConsumer<String, Throwable> onFailure) {
        try {
            step.action().run();
        } catch (Throwable t) {
            onFailure.accept(step.name(), t);
        }
    }

    private static void runTogether(List<ShutdownStep> steps, BiConsumer<String, Throwable> onFailure) {
        List<Thread> threads = steps.stream()
                .map(step -> Thread.ofVirtual()
                        .name("shutdown-" + step.name())
                        .start(() -> runShutdownStep(step, onFailure)))
                .toList();
        boolean interrupted = false;
        for (Thread thread : threads) {
            interrupted |= joinUninterruptibly(thread);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean joinUninterruptibly(Thread thread) {
        boolean interrupted = false;
        while (true) {
            try {
                thread.join();
                return interrupted;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
    }

    private static void reportFailedShutdownStep(String stepName, Throwable failure) {
        try {
            BotLogger.error("Shutdown step failed: " + stepName, failure);
        } catch (Exception loggingFailure) {
            System.err.println("Shutdown step failed: " + stepName);
            failure.printStackTrace();
        }
    }

    private static ShutdownStep executorStep(String executorName, Supplier<ShutdownResult> shutdown) {
        return new ShutdownStep(executorName, () -> {
            long startedAt = System.currentTimeMillis();
            ShutdownResult result = shutdown.get();
            SHUTDOWN_REPORT.record(
                    System.currentTimeMillis() - startedAt, executorName + describeIfNotGraceful(result));
            warnIfNotGraceful(executorName, result);
        });
    }

    private static void setShutdownPresence() {
        jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("BOT IS SHUTTING DOWN"));
    }

    private static void stopAcceptingCommands() {
        ActiveLeaseService.setCurrentProcessReady(false);
        SHUTDOWN_REPORT.start();
        BotLogger.info("STOPPED ACCEPTING COMMANDS");
    }

    private static void flushUserActiveHours() {
        long startedAt = System.currentTimeMillis();
        int flushedUsers = UserActiveHourRecorder.flush();
        SHUTDOWN_REPORT.record(
                System.currentTimeMillis() - startedAt,
                "active hours for " + StringHelper.pluralize(flushedUsers, "user"));
    }

    private static void releaseActiveLease() {
        SpringContext.getBean(ActiveLeaseService.class).releaseLease();
        BotLogger.info(SHUTDOWN_REPORT.summarize("RELEASED THE ACTIVE LEASE"));
    }

    private static void sendBufferedLogsBeforeJdaShutdown() {
        BotLogger.info(SHUTDOWN_REPORT.summarize("FINISHED DRAINING"));
        BotLogger.sendBufferedLogsToDiscord();
    }

    private static String describeIfNotGraceful(ShutdownResult result) {
        return switch (result) {
            case GRACEFUL_TERMINATION -> "";
            case FORCED_TERMINATION -> " (interrupted)";
            case TIMED_OUT -> " (timed out)";
            case INTERRUPTED -> " (shutdown interrupted)";
        };
    }

    private static void warnIfNotGraceful(String executorName, ShutdownResult result) {
        switch (result) {
            case GRACEFUL_TERMINATION -> {}
            case FORCED_TERMINATION -> BotLogger.warning(executorName + " terminated after interrupt request.");
            case TIMED_OUT -> BotLogger.warning(executorName + " did not terminate before the shutdown timeout.");
            case INTERRUPTED -> BotLogger.warning(executorName + " shutdown was interrupted.");
        }
    }

    private static final class ShutdownReport {

        private final List<String> steps = new ArrayList<>();
        private long stoppedAcceptingCommandsAt = System.currentTimeMillis();

        private synchronized void start() {
            stoppedAcceptingCommandsAt = System.currentTimeMillis();
            steps.clear();
        }

        private synchronized void record(long millis, String step) {
            steps.add(String.format("%,d MS %s", millis, step));
        }

        private synchronized String summarize(String milestone) {
            String summary = String.format(
                    "%s %,d MS AFTER STOPPING COMMANDS: %s",
                    milestone, System.currentTimeMillis() - stoppedAcceptingCommandsAt, String.join(", ", steps));
            steps.clear();
            return summary;
        }
    }

    private static ShutdownResult shutdownEventExecutor() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                EVENT_EXECUTOR, EVENT_POOL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void shutdownJda() {
        jda.shutdown();
        try {
            if (!jda.awaitShutdown(JDA_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                jda.shutdownNow();
                jda.awaitShutdown(JDA_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }
}
