package ti4;

import javax.imageio.ImageIO;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
import ti4.commands.CommandManager;
import ti4.commands.admin.AdminCommand;
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.bothelper.BothelperCommand;
import ti4.commands.button.GenericButtonCommand;
import ti4.commands.capture.CaptureCommand;
import ti4.commands.cardsac.ACCardsCommand;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.cardsso.SOCardsCommand;
import ti4.commands.combat.CombatCommand;
import ti4.commands.custom.CustomCommand;
import ti4.commands.developer.DeveloperCommand;
import ti4.commands.ds.DiscordantStarsCommand;
import ti4.commands.event.EventCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
import ti4.commands.franken.FrankenCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.help.HelpCommand;
import ti4.commands.installation.InstallationCommand;
import ti4.commands.leaders.LeaderCommand;
import ti4.commands.map.MapCommand;
import ti4.commands.milty.MiltyCommand;
import ti4.commands.planet.PlanetCommand;
import ti4.commands.player.PlayerCommand;
import ti4.commands.relic.RelicCommand;
import ti4.commands.search.SearchCommand;
import ti4.commands.special.SpecialCommand;
import ti4.commands.statistics.StatisticsCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tigl.TIGLCommand;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveAllCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.tokens.RemoveToken;
import ti4.commands.uncategorized.AllInfo;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.uncategorized.SelectionBoxDemo;
import ti4.commands.uncategorized.ShowDistances;
import ti4.commands.uncategorized.ShowGame;
import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveAllUnitDamage;
import ti4.commands.units.RemoveAllUnits;
import ti4.commands.units.RemoveUnitDamage;
import ti4.commands.units.RemoveUnits;
import ti4.commands.user.UserCommand;
import ti4.commands.user.UserSettingsManager;
import ti4.cron.AutoPingCron;
import ti4.cron.LogCacheStatsCron;
import ti4.generator.MapRenderPipeline;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.FoWHelper;
import ti4.helpers.GlobalSettings;
import ti4.helpers.GlobalSettings.ImplementedSettings;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper;
import ti4.listeners.AutoCompleteListener;
import ti4.listeners.ButtonListener;
import ti4.listeners.MessageListener;
import ti4.listeners.ModalListener;
import ti4.listeners.SelectionMenuListener;
import ti4.listeners.SlashCommandListener;
import ti4.listeners.UserJoinServerListener;
import ti4.map.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.selections.SelectionManager;

import static org.reflections.scanners.Scanners.SubTypes;

public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();
    public static final List<Role> adminRoles = new ArrayList<>();
    public static final List<Role> developerRoles = new ArrayList<>();
    public static final List<Role> bothelperRoles = new ArrayList<>();
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    public static JDA jda;
    public static String userID;
    public static String guildPrimaryID;
    public static Guild guildPrimary;
    public static Guild guildSecondary;
    public static Guild guildTertiary;
    public static Guild guildQuaternary;
    public static Guild guildQuinary;
    public static Guild guildSenary;
    public static Guild guildFogOfWar;
    public static Guild guildCommunityPlays;
    public static final Set<Guild> guilds = new HashSet<>();
    public static final List<Guild> serversToCreateNewGamesOn = new ArrayList<>();

    private static final List<Class<?>> classes = new ArrayList<>();

    public static void main(String[] args) {
        GlobalSettings.loadSettings();
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
        jda = JDABuilder.createDefault(args[0])
            // This is a privileged gateway intent that is used to update user information and join/leaves (including kicks).
            // This is required to cache all members of a guild (including chunking)
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
            // This is a privileged gateway intent this is only used to enable access to the user content in messages
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
            new MessageListener(),
            new SlashCommandListener(),
            ButtonListener.getInstance(),
            ModalListener.getInstance(),
            new SelectionMenuListener(),
            new UserJoinServerListener(),
            new AutoCompleteListener());

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            MessageHelper.sendMessageToBotLogWebhook("Error waiting for bot to get ready");
        }

        jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("STARTING UP: Connecting to Servers"));

        userID = args[1];
        guildPrimaryID = args[2];

        MessageHelper.sendMessageToBotLogWebhook("# `" + new Timestamp(System.currentTimeMillis()) + "`  BOT IS STARTING UP");

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new AllInfo());
        commandManager.addCommand(new CardsInfo());
        commandManager.addCommand(new ShowGame());
        commandManager.addCommand(new ShowDistances());
        commandManager.addCommand(new AddCC());
        commandManager.addCommand(new RemoveCC());
        commandManager.addCommand(new RemoveAllCC());
        commandManager.addCommand(new AddFrontierTokens());
        commandManager.addCommand(new MoveUnits());
        commandManager.addCommand(new RemoveToken());
        commandManager.addCommand(new AddToken());
        commandManager.addCommand(new AddUnitDamage());
        commandManager.addCommand(new RemoveUnitDamage());
        commandManager.addCommand(new RemoveAllUnitDamage());

        commandManager.addCommand(new MapCommand());
        commandManager.addCommand(new HelpCommand());
        commandManager.addCommand(new SearchCommand());
        commandManager.addCommand(new ExploreCommand());
        commandManager.addCommand(new RelicCommand());

        commandManager.addCommand(new AdminCommand());
        commandManager.addCommand(new DeveloperCommand());
        commandManager.addCommand(new BothelperCommand());
        commandManager.addCommand(new PlayerCommand());
        commandManager.addCommand(new GameCommand());

        commandManager.addCommand(new ACCardsCommand());
        commandManager.addCommand(new PNCardsCommand());
        commandManager.addCommand(new SOCardsCommand());
        commandManager.addCommand(new StatusCommand());
        commandManager.addCommand(new AgendaCommand());
        commandManager.addCommand(new EventCommand());

        commandManager.addCommand(new SpecialCommand());
        commandManager.addCommand(new LeaderCommand());
        commandManager.addCommand(new CombatCommand());
        commandManager.addCommand(new CustomCommand());
        commandManager.addCommand(new FOWCommand());
        commandManager.addCommand(new InstallationCommand());
        commandManager.addCommand(new MiltyCommand());
        commandManager.addCommand(new FrankenCommand());
        commandManager.addCommand(new CaptureCommand());
        commandManager.addCommand(new GenericButtonCommand());
        commandManager.addCommand(new DiscordantStarsCommand());
        commandManager.addCommand(new StatisticsCommand());
        commandManager.addCommand(new TechCommand());
        commandManager.addCommand(new PlanetCommand());
        commandManager.addCommand(new SelectionBoxDemo());
        commandManager.addCommand(new UserCommand());
        commandManager.addCommand(new TIGLCommand());

        // Primary HUB Server
        guildPrimary = jda.getGuildById(args[2]);
        startBot(commandManager, guildPrimary);

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = jda.getGuildById(args[3]);
            startBot(commandManager, guildCommunityPlays);
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = jda.getGuildById(args[4]);
            startBot(commandManager, guildFogOfWar);

            // JAZZ WILL GET PINGED IF SHIT IS BROKEN FOR FOG GAMES
            FoWHelper.sanityCheckFowReacts();
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = jda.getGuildById(args[5]);
            startBot(commandManager, guildSecondary);
            serversToCreateNewGamesOn.add(guildSecondary);
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guildTertiary = jda.getGuildById(args[6]);
            startBot(commandManager, guildTertiary);
            serversToCreateNewGamesOn.add(guildTertiary);
        }

        // Async: War Sun Tzu
        if (args.length >= 8) {
            guildQuaternary = jda.getGuildById(args[7]);
            startBot(commandManager, guildQuaternary);
            serversToCreateNewGamesOn.add(guildQuaternary);
        }

        // Async: Fighter Club
        if (args.length >= 9) {
            guildQuinary = jda.getGuildById(args[8]);
            startBot(commandManager, guildQuinary);
            serversToCreateNewGamesOn.add(guildQuinary);
        }

        // Async: Tommer Hawk
        if (args.length >= 10) {
            guildSenary = jda.getGuildById(args[9]);
            startBot(commandManager, guildSenary);
            serversToCreateNewGamesOn.add(guildSenary);
        }

        // LOAD DATA
        BotLogger.logWithTimestamp(" LOADING DATA");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Data"));
        UserSettingsManager.init();
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        SelectionManager.init();
        initializeWhitelistedRoles();
        TIGLHelper.validateTIGLness();

        // LOAD GAME NAMES
        BotLogger.logWithTimestamp(" LOADING GAME NAMES");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Games"));
        GameManager.getInstance();

        // RUN DATA MIGRATIONS
        BotLogger.logWithTimestamp(" CHECKING FOR DATA MIGRATIONS");
        DataMigrationManager.runMigrations();
        BotLogger.logWithTimestamp(" FINISHED CHECKING FOR DATA MIGRATIONS");

        // START MAP GENERATION
        MapRenderPipeline.start();
        ImageIO.setUseCache(false);

        // START CRONS
        AutoPingCron.start();
        LogCacheStatsCron.start();

        // BOT IS READY
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, true);
        jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing("Async TI4"));
        BotLogger.log("# `" + new Timestamp(System.currentTimeMillis()) + "`  FINISHED LOADING GAMES");

        // Register Shutdown Hook to run when SIGTERM is received from docker stop
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("BOT IS SHUTTING DOWN"));
                BotLogger.logWithTimestamp("SHUTDOWN PROCESS STARTED");
                GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
                BotLogger.logWithTimestamp("NO LONGER ACCEPTING COMMANDS, WAITING 10 SECONDS FOR COMPLETION");
                TimeUnit.SECONDS.sleep(10); // wait for current commands to complete
                if (MapRenderPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                    BotLogger.logWithTimestamp("DONE RENDERING MAPS");
                }
                BotLogger.logWithTimestamp("SHUTDOWN PROCESS COMPLETE");
                TimeUnit.SECONDS.sleep(1); // wait for BotLogger
                jda.shutdown();
                jda.awaitShutdown(30, TimeUnit.SECONDS);
                mainThread.join();
            } catch (Exception e) {
                MessageHelper.sendMessageToBotLogWebhook("Error encountered within shutdown hook:\n> " + e.getMessage());
            }
        }));
    }

    private static void startBot(CommandManager commandManager, Guild guild) {
        if (guild == null) {
            return;
        }
        CommandListUpdateAction commands = guild.updateCommands();
        commandManager.getCommandList().forEach(command -> command.registerCommands(commands));
        commands.queue();
        BotLogger.logWithTimestamp(" BOT STARTED UP: " + guild.getName());
        guilds.add(guild);
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
        //ADMIN ROLES
        adminRoles.add(jda.getRoleById("943596173896323072")); // Async Primary (Hub)
        adminRoles.add(jda.getRoleById("1090914497352446042")); // Async Secondary (Stroter's Paradise)
        adminRoles.add(jda.getRoleById("1146511484264906814")); // Async Tertiary (Dreadn't)
        adminRoles.add(jda.getRoleById("1176104225978204167")); // Async Quaternary (War Sun Tzu)
        adminRoles.add(jda.getRoleById("1209956332380229678")); // Async Quinary (Fighter Club)
        adminRoles.add(jda.getRoleById("1250131684393881616")); // Async Senary (Tommer Hawk)
        adminRoles.add(jda.getRoleById("1062804021385105500")); // FoW Server
        adminRoles.add(jda.getRoleById("1067866210865250445")); // PrisonerOne's Test Server
        adminRoles.add(jda.getRoleById("1060656344581017621")); // Softnum's Server
        adminRoles.add(jda.getRoleById("1109657180170371182")); // Jazz's Server
        adminRoles.add(jda.getRoleById("1100120742093406319")); // Moo's Server
        adminRoles.add(jda.getRoleById("1126610851034583050")); // Fin's Server
        adminRoles.add(jda.getRoleById("824111008863092757")); // Fireseal's Server
        adminRoles.add(jda.getRoleById("336194595501244417")); // tedw4rd's Server
        adminRoles.add(jda.getRoleById("1149705227625316352")); // who dis
        adminRoles.add(jda.getRoleById("1178659621225889875")); // Jepp2078's Server
        adminRoles.add(jda.getRoleById("1215451631622164610")); // Sigma's Server
        adminRoles.add(jda.getRoleById("1225597324206800996")); // ForlornGeas's Server
        adminRoles.add(jda.getRoleById("1226068025464197160")); // Rintsi's Server
        adminRoles.add(jda.getRoleById("1226805374007640095")); // Solax's Server
        adminRoles.add(jda.getRoleById("951230650680225863")); // Community Server
        adminRoles.removeIf(Objects::isNull);

        //DEVELOPER ROLES
        developerRoles.addAll(adminRoles); //admins may also execute developer commands
        developerRoles.add(jda.getRoleById("947648366056185897")); // Async Primary (Hub)
        developerRoles.add(jda.getRoleById("1090958278479052820")); // Async Secondary (Stroter's Paradise)
        developerRoles.add(jda.getRoleById("1146529125184581733")); // Async Tertiary (Dreadn't)
        developerRoles.add(jda.getRoleById("1176104225978204166")); // Async Quaternary (War Sun Tzu)
        developerRoles.add(jda.getRoleById("1209956332380229677")); // Async Quinary (Fighter Club)
        developerRoles.add(jda.getRoleById("1250131684393881615")); // Async Senary (Tommer Hawk)
        developerRoles.add(jda.getRoleById("1088532767773564928")); // FoW Server
        developerRoles.add(jda.getRoleById("1215453013154734130")); // Sigma's Server
        developerRoles.add(jda.getRoleById("1225597362186223746")); // ForlornGeas's Server
        developerRoles.add(jda.getRoleById("1226068105071956058")); // Rintsi's Server
        developerRoles.add(jda.getRoleById("1226805601422676069")); // Solax's Server
        developerRoles.removeIf(Objects::isNull);

        //BOTHELPER ROLES
        bothelperRoles.addAll(adminRoles); //admins can also execute bothelper commands
        bothelperRoles.add(jda.getRoleById("1166011604488425482")); // Async Primary (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary (Stroter's Paradise)
        bothelperRoles.add(jda.getRoleById("1146539257725464666")); // Async Tertiary (Dreadn't)
        bothelperRoles.add(jda.getRoleById("1176104225978204164")); // Async Quaternary (War Sun Tzu)
        bothelperRoles.add(jda.getRoleById("1209956332380229675")); // Async Quinary (Fighter Club)
        bothelperRoles.add(jda.getRoleById("1250131684393881613")); // Async Senary (Tommer Hawk)
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.add(jda.getRoleById("1063464689218105354")); // FoW Server Game Admin
        bothelperRoles.add(jda.getRoleById("1225597399385374781")); // ForlornGeas's Server
        bothelperRoles.add(jda.getRoleById("1131925041219653714")); // Jonjo's Server
        bothelperRoles.add(jda.getRoleById("1215450829096624129")); // Sigma's Server
        bothelperRoles.add(jda.getRoleById("1226068245010710558")); // Rintsi's Server
        bothelperRoles.add(jda.getRoleById("1226805674046914560")); // Solax's Server 
        bothelperRoles.add(jda.getRoleById("1248693989193023519")); // Community Server
        bothelperRoles.removeIf(Objects::isNull);
    }

    public static boolean isReadyToReceiveCommands() {
        return GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.READY_TO_RECEIVE_COMMANDS.toString(), Boolean.class, false);
    }

    public static <T> CompletableFuture<T> completeAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, THREAD_POOL).handle((result, exception) -> {
            if (exception != null) {
                BotLogger.log("Unable to complete async process.", exception);
                return null;
            }
            return result;
        });
    }

    public static void runAsync(Runnable runnable) {
        THREAD_POOL.submit(runnable);
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
        // classes.sort(Comparator.comparing(Class<?>::getName));
        return classes;
    }
}
