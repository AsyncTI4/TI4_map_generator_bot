package ti4;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import ti4.autocomplete.AutoCompleteListener;
import ti4.buttons.ButtonListener;
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
import ti4.commands.search.SearchCommand;
import ti4.commands.special.SpecialCommand;
import ti4.commands.statistics.StatisticsCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveAllCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.tokens.RemoveToken;
import ti4.commands.uncategorized.AllInfo;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.uncategorized.DeleteGame;
import ti4.commands.uncategorized.ShowDistances;
import ti4.commands.uncategorized.ShowGame;
import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveAllUnitDamage;
import ti4.commands.units.RemoveAllUnits;
import ti4.commands.units.RemoveUnitDamage;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Storage;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class AsyncTI4DiscordBot {

    public static final long START_TIME_MILLISECONDS = System.currentTimeMillis();
    public static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()));
    public static final List<Role> adminRoles = new ArrayList<>();
    public static final List<Role> developerRoles = new ArrayList<>();
    public static final List<Role> bothelperRoles = new ArrayList<>();

    public static JDA jda;
    public static String userID;
    public static Guild guildPrimary;
    public static Guild guildSecondary;
    public static Guild guild3rd;
    public static Guild guildFogOfWar;
    public static Guild guildCommunityPlays;
    public static Set<Guild> guilds = new HashSet<>();
    public static boolean readyToReceiveCommands;

    public static void main(String[] args) {
        GlobalSettings.loadSettings();

        jda = JDABuilder.createDefault(args[0])
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .setEnableShutdownHook(false)
            .build();

        jda.addEventListener(
            new MessageListener(),
            new ButtonListener(),
            new UserJoinServerListener(),
            new AutoCompleteListener());

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            MessageHelper.sendMessageToBotLogWebhook("Error waiting for bot to get ready");
        }

        jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("STARTING UP: Connecting to Servers"));

        userID = args[1];
        guildPrimary = jda.getGuildById(args[2]);
        guilds.add(guildPrimary);
        MessageHelper.sendMessageToBotLogWebhook("# `" + new Timestamp(System.currentTimeMillis()) + "`  BOT IS STARTING UP");

        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();

        //ROLES - FOR COMMAND PERMISSIONS
        //ADMIN ROLES
        adminRoles.add(jda.getRoleById("943596173896323072")); // Async TI4 Server (Hub)
        adminRoles.add(jda.getRoleById("1090914497352446042")); // Async Secondary
        adminRoles.add(jda.getRoleById("1146511484264906814")); // Async 3rd Server
        adminRoles.add(jda.getRoleById("1062804021385105500")); // FoW Server
        adminRoles.add(jda.getRoleById("1067866210865250445")); // PrisonerOne's Test Server
        adminRoles.add(jda.getRoleById("1060656344581017621")); // Softnum's Server
        adminRoles.add(jda.getRoleById("1109657180170371182")); // Jazz's Server
        adminRoles.add(jda.getRoleById("1100120742093406319")); // Moo's Server
        adminRoles.add(jda.getRoleById("1126610851034583050")); // Fin's Server
        adminRoles.add(jda.getRoleById("824111008863092757")); // Fireseal's Server
        adminRoles.add(jda.getRoleById("336194595501244417")); // tedw4rd's Server
        adminRoles.add(jda.getRoleById("1149705227625316352")); // who dis

        adminRoles.removeIf(Objects::isNull);

        //DEVELOPER ROLES
        developerRoles.addAll(adminRoles); //admins can also execute developer commands
        developerRoles.add(jda.getRoleById("947648366056185897")); // Async TI4 Server (Hub)
        developerRoles.add(jda.getRoleById("1090958278479052820")); // Async Secondary
        developerRoles.add(jda.getRoleById("1146529125184581733")); // Async 3rd server
        developerRoles.add(jda.getRoleById("1088532767773564928")); // FoW Server
        developerRoles.removeIf(Objects::isNull);

        //BOTHELPER ROLES
        bothelperRoles.addAll(developerRoles); //admins and developers can also execute bothelper commands
        bothelperRoles.add(jda.getRoleById("1166011604488425482")); // Async TI4 Server (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary
        bothelperRoles.add(jda.getRoleById("1146539257725464666")); // Async 3rd server
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.add(jda.getRoleById("1063464689218105354")); // FoW Server Game Admin
        bothelperRoles.add(jda.getRoleById("1131925041219653714")); //Jonjo's Server

        bothelperRoles.removeIf(Objects::isNull);

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new AllInfo());
        commandManager.addCommand(new CardsInfo());
        commandManager.addCommand(new ShowGame());
        commandManager.addCommand(new ShowDistances());
        commandManager.addCommand(new DeleteGame());
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

        CommandListUpdateAction commands = guildPrimary.updateCommands();
        commandManager.getCommandList().forEach(command -> command.registerCommands(commands));
        commands.queue();

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = jda.getGuildById(args[3]);
            if (guildCommunityPlays != null) {
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  BOT STARTED UP: " + guildCommunityPlays.getName());
                CommandListUpdateAction commandsC = guildCommunityPlays.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsC));
                commandsC.queue();
                guilds.add(guildCommunityPlays);
            }
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = jda.getGuildById(args[4]);
            if (guildFogOfWar != null) {
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  BOT STARTED UP: " + guildFogOfWar.getName());
                CommandListUpdateAction commandsD = guildFogOfWar.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
                guilds.add(guildFogOfWar);
            }
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = jda.getGuildById(args[5]);
            if (guildSecondary != null) {
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  BOT STARTED UP: " + guildSecondary.getName());
                CommandListUpdateAction commandsD = guildSecondary.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
                guilds.add(guildSecondary);
            }
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guild3rd = jda.getGuildById(args[6]);
            if (guild3rd != null) {
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  BOT STARTED UP: " + guild3rd.getName());
                CommandListUpdateAction commandsD = guild3rd.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
                guilds.add(guild3rd);
            }
        }

        BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  BOT STARTED UP: " + guildPrimary.getName());
        BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  LOADING MAPS: " + guildPrimary.getName());
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Maps"));
        GameSaveLoadManager.loadMaps();

        BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  CHECKING FOR DATA MIGRATIONS");
        DataMigrationManager.runMigrations();
        BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "`  FINISHED CHECKING FOR DATA MIGRATIONS");

        readyToReceiveCommands = true;
        jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing("Async TI4"));
        BotLogger.log("# `" + new Timestamp(System.currentTimeMillis()) + "`  FINISHED LOADING MAPS");

        // Shutdown hook to run when SIGTERM is recieved from docker stop
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("BOT IS SHUTTING DOWN"));
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "# ` SHUTDOWN PROCESS STARTED");
                readyToReceiveCommands = false;
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "` NO LONGER ACCEPTING COMMANDS");
                TimeUnit.SECONDS.sleep(10); // wait for current commands to complete
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "` SAVING MAPS");
                GameSaveLoadManager.saveMaps();
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "` MAPS HAVE BEEN SAVED");
                BotLogger.log("`" + new Timestamp(System.currentTimeMillis()) + "` SHUTDOWN PROCESS COMPLETE");
                TimeUnit.SECONDS.sleep(1); // wait for BotLogger
                jda.shutdown();
                jda.awaitShutdown();
                mainThread.join();
            } catch (Exception e) {
                MessageHelper.sendMessageToBotLogWebhook("Error encountered within shutdown hook:\n> " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }
}
