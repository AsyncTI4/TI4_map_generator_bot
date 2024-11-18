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
import ti4.commands.capture.CaptureCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.leaders.LeaderCommand;
import ti4.commands.map.MapCommand;
import ti4.commands.planet.PlanetCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveAllCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.tokens.RemoveToken;
import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.ModifyUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveAllUnitDamage;
import ti4.commands.units.RemoveAllUnits;
import ti4.commands.units.RemoveUnitDamage;
import ti4.commands.units.RemoveUnits;
import ti4.commands2.admin.AdminCommand;
import ti4.commands2.agenda.AgendaCommand;
import ti4.commands2.bothelper.BothelperCommand;
import ti4.commands2.button.GenericButtonCommand;
import ti4.commands2.cardsac.ACCardsCommand;
import ti4.commands2.cardspn.PNCardsCommand;
import ti4.commands2.cardsso.SOCardsCommand;
import ti4.commands2.combat.CombatCommand;
import ti4.commands2.custom.CustomCommand;
import ti4.commands2.developer.DeveloperCommand;
import ti4.commands2.ds.DiscordantStarsCommand;
import ti4.commands2.event.EventCommand;
import ti4.commands2.franken.FrankenCommand;
import ti4.commands2.help.HelpCommand;
import ti4.commands2.installation.InstallationCommand;
import ti4.commands2.milty.MiltyCommand;
import ti4.commands2.player.PlayerCommand;
import ti4.commands2.relic.RelicCommand;
import ti4.commands2.search.SearchCommand;
import ti4.commands2.special.SpecialCommand;
import ti4.commands2.statistics.StatisticsCommand;
import ti4.commands2.status.StatusCommand;
import ti4.commands2.tigl.TIGLCommand;
import ti4.commands2.uncategorized.AllInfo;
import ti4.commands2.uncategorized.CardsInfo;
import ti4.commands2.uncategorized.SelectionBoxDemo;
import ti4.commands2.uncategorized.ShowDistances;
import ti4.commands2.uncategorized.ShowGame;
import ti4.commands2.user.UserCommand;
import ti4.cron.AutoPingCron;
import ti4.cron.LogCacheStatsCron;
import ti4.cron.UploadStatsCron;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
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
import ti4.map.GameSaveLoadManager;
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

        CommandManager.addCommand(new AddUnits());
        CommandManager.addCommand(new RemoveUnits());
        CommandManager.addCommand(new RemoveAllUnits());
        CommandManager.addCommand(new AllInfo());
        CommandManager.addCommand(new CardsInfo());
        CommandManager.addCommand(new ShowGame());
        CommandManager.addCommand(new ShowDistances());
        CommandManager.addCommand(new AddCC());
        CommandManager.addCommand(new RemoveCC());
        CommandManager.addCommand(new RemoveAllCC());
        CommandManager.addCommand(new AddFrontierTokens());
        CommandManager.addCommand(new MoveUnits());
        CommandManager.addCommand(new ModifyUnits());
        CommandManager.addCommand(new RemoveToken());
        CommandManager.addCommand(new AddToken());
        CommandManager.addCommand(new AddUnitDamage());
        CommandManager.addCommand(new RemoveUnitDamage());
        CommandManager.addCommand(new RemoveAllUnitDamage());

        CommandManager.addCommand(new MapCommand());
        CommandManager.addCommand(new HelpCommand());
        CommandManager.addCommand(new SearchCommand());
        CommandManager.addCommand(new ExploreCommand());
        CommandManager.addCommand(new RelicCommand());

        CommandManager.addCommand(new AdminCommand());
        CommandManager.addCommand(new DeveloperCommand());
        CommandManager.addCommand(new BothelperCommand());
        CommandManager.addCommand(new PlayerCommand());
        CommandManager.addCommand(new GameCommand());

        CommandManager.addCommand(new ACCardsCommand());
        CommandManager.addCommand(new PNCardsCommand());
        CommandManager.addCommand(new SOCardsCommand());
        CommandManager.addCommand(new StatusCommand());
        CommandManager.addCommand(new AgendaCommand());
        CommandManager.addCommand(new EventCommand());

        CommandManager.addCommand(new SpecialCommand());
        CommandManager.addCommand(new LeaderCommand());
        CommandManager.addCommand(new CombatCommand());
        CommandManager.addCommand(new CustomCommand());
        CommandManager.addCommand(new FOWCommand());
        CommandManager.addCommand(new InstallationCommand());
        CommandManager.addCommand(new MiltyCommand());
        CommandManager.addCommand(new FrankenCommand());
        CommandManager.addCommand(new CaptureCommand());
        CommandManager.addCommand(new GenericButtonCommand());
        CommandManager.addCommand(new DiscordantStarsCommand());
        CommandManager.addCommand(new StatisticsCommand());
        CommandManager.addCommand(new TechCommand());
        CommandManager.addCommand(new PlanetCommand());
        CommandManager.addCommand(new SelectionBoxDemo());
        CommandManager.addCommand(new UserCommand());
        CommandManager.addCommand(new TIGLCommand());

        // Primary HUB Server
        guildPrimary = jda.getGuildById(args[2]);
        startBot(guildPrimary);

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = jda.getGuildById(args[3]);
            startBot(guildCommunityPlays);
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = jda.getGuildById(args[4]);
            startBot(guildFogOfWar);

            // JAZZ WILL GET PINGED IF SHIT IS BROKEN FOR FOG GAMES
            FoWHelper.sanityCheckFowReacts();
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = jda.getGuildById(args[5]);
            startBot(guildSecondary);
            serversToCreateNewGamesOn.add(guildSecondary);
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guildTertiary = jda.getGuildById(args[6]);
            startBot(guildTertiary);
            serversToCreateNewGamesOn.add(guildTertiary);
        }

        // Async: War Sun Tzu
        if (args.length >= 8) {
            guildQuaternary = jda.getGuildById(args[7]);
            startBot(guildQuaternary);
            serversToCreateNewGamesOn.add(guildQuaternary);
        }

        // Async: Fighter Club
        if (args.length >= 9) {
            guildQuinary = jda.getGuildById(args[8]);
            startBot(guildQuinary);
            serversToCreateNewGamesOn.add(guildQuinary);
        }

        // Async: Tommer Hawk
        if (args.length >= 10) {
            guildSenary = jda.getGuildById(args[9]);
            startBot(guildSenary);
            serversToCreateNewGamesOn.add(guildSenary);
        }

        // LOAD DATA
        BotLogger.logWithTimestamp(" LOADING DATA");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Data"));
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        SelectionManager.init();
        initializeWhitelistedRoles();
        TIGLHelper.validateTIGLness();

        // LOAD GAMES
        BotLogger.logWithTimestamp(" LOADING GAMES");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Games"));
        GameSaveLoadManager.loadGame();

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
        UploadStatsCron.start();

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

    private static void startBot(Guild guild) {
        if (guild == null) {
            return;
        }
        CommandListUpdateAction commands = guild.updateCommands();
        CommandManager.getCommands().forEach(command -> command.register(commands));
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
