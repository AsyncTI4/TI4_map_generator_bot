package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import ti4.buttons.ButtonListener;
import ti4.commands.CommandManager;
import ti4.commands.admin.AdminCommand;
import ti4.commands.admin.RunManualDataMigration;
import ti4.commands.bothelper.BothelperCommand;
import ti4.commands.button.GenericButtonCommand;
import ti4.commands.capture.CaptureCommand;
import ti4.commands.cards.CardsInfo;
import ti4.commands.cardsac.ACCardsCommand;
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.all_info.AllInfo;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.cardsso.SOCardsCommand;
import ti4.commands.combat.CombatCommand;
import ti4.commands.custom.CustomCommand;
import ti4.commands.ds.DiscordantStarsCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
import ti4.commands.franken.FrankenCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.help.*;
import ti4.commands.installation.InstallationCommand;
import ti4.commands.leaders.LeaderCommand;
import ti4.commands.map.*;
import ti4.commands.milty.MiltyCommand;
import ti4.commands.planet.PlanetCommand;
import ti4.commands.player.PlayerCommand;
import ti4.commands.special.SpecialCommand;
import ti4.commands.statistics.StatisticsCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tokens.*;
import ti4.commands.units.*;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Storage;
import ti4.map.MapSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static JDA jda;
    public static String userID;
    public static Guild guildPrimary = null;
    public static Guild guildSecondary = null;
    public static Guild guild3rd = null;
    public static Guild guildFogOfWar = null;
    public static Guild guildCommunityPlays = null;
    public static String adminID;
    public static List<Role> adminRoles = new ArrayList<>();
    public static List<Role> developerRoles = new ArrayList<>();
    public static List<Role> bothelperRoles = new ArrayList<>();

    public static boolean readyToReceiveCommands = false;

    public static void main(String[] args)
            throws LoginException {


        // Load settings
        GlobalSettings.loadSettings();

        jda = JDABuilder.createDefault(args[0])
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .build();

        jda.addEventListener(new MessageListener(), new ButtonListener());
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            BotLogger.log("Error waiting for bot to get ready", e);
        }

        userID = args[1];
        guildPrimary = jda.getGuildById(args[2]);
        MessageHelper.sendMessageToBotLogWebhook("BOT IS STARTING UP");

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
        bothelperRoles.add(jda.getRoleById("970033771179028531")); // Async TI4 Server (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.add(jda.getRoleById("1063464689218105354"));// FoW Server Game Admin
        bothelperRoles.add(jda.getRoleById("1131925041219653714"));//Jonjo's Server

        bothelperRoles.removeIf(Objects::isNull);



        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new RemoveTile());
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new AllInfo());
        commandManager.addCommand(new CardsInfo());
        commandManager.addCommand(new SetGame());
        commandManager.addCommand(new ShowGame());
        commandManager.addCommand(new AddTileList());
        commandManager.addCommand(new DeleteGame());
        commandManager.addCommand(new AddCC());
        commandManager.addCommand(new RemoveCC());
        commandManager.addCommand(new RemoveAllCC());
        commandManager.addCommand(new AddFrontierTokens());
        commandManager.addCommand(new MoveUnits());
        commandManager.addCommand(new MoveUnits2());
        commandManager.addCommand(new RemoveToken());
        commandManager.addCommand(new AddToken());
        commandManager.addCommand(new AddUnitDamage());
        commandManager.addCommand(new RemoveUnitDamage());
        commandManager.addCommand(new RemoveAllUnitDamage());

        commandManager.addCommand(new HelpCommand());
        commandManager.addCommand(new ExploreCommand());
        commandManager.addCommand(new AdminCommand());


        commandManager.addCommand(new BothelperCommand());
        commandManager.addCommand(new PlayerCommand());
        commandManager.addCommand(new GameCommand());


        commandManager.addCommand(new ACCardsCommand());
        commandManager.addCommand(new PNCardsCommand());
        commandManager.addCommand(new SOCardsCommand());
        commandManager.addCommand(new StatusCommand());
        commandManager.addCommand(new AgendaCommand());


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
        commandManager.addCommand(new AddBorderAnomaly());
        commandManager.addCommand(new RemoveBorderAnomaly());

        CommandListUpdateAction commands = guildPrimary.updateCommands();
        commandManager.getCommandList().forEach(command -> command.registerCommands(commands));
        commands.queue();

        //TI Community game
        if (args.length >= 4) {
            guildCommunityPlays = jda.getGuildById(args[3]);
            if (guildCommunityPlays != null) {
                BotLogger.log("BOT STARTED UP: " + guildCommunityPlays.getName());
                CommandListUpdateAction commandsC = guildCommunityPlays.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsC));
                commandsC.queue();
            }
        }

        //FOW game
        if (args.length >= 5) {
            guildFogOfWar = jda.getGuildById(args[4]);
            if (guildFogOfWar != null) {
                BotLogger.log("BOT STARTED UP: " + guildFogOfWar.getName());
                CommandListUpdateAction commandsD = guildFogOfWar.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
            }
        }

        //Async Secondary
        if (args.length >= 6) {
            guildSecondary = jda.getGuildById(args[5]);
            if (guildSecondary != null) {
                BotLogger.log("BOT STARTED UP: " + guildSecondary.getName());
                CommandListUpdateAction commandsD = guildSecondary.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
            }
        }

        //Async 3rd Server
        if (args.length >= 7) {
            guild3rd = jda.getGuildById(args[6]);
            if (guild3rd != null) {
                BotLogger.log("BOT STARTED UP: " + guild3rd.getName());
                CommandListUpdateAction commandsD = guild3rd.updateCommands();
                commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
                commandsD.queue();
            }
        }

        BotLogger.log("BOT STARTED UP: " + guildPrimary.getName());
        MapSaveLoadManager.loadMaps();

        BotLogger.log("BOT CHECKING FOR DATA MIGRATIONS");
        DataMigrationManager.runMigrations(); 
        BotLogger.log("BOT FINISHED CHECKING FOR DATA MIGRATIONS");

        readyToReceiveCommands = true;
        BotLogger.log("BOT HAS FINISHED LOADING MAPS");


        // Shutdown hook to run when SIGTERM is recieved from docker stop
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    MessageHelper.sendMessageToBotLogWebhook("SHUTDOWN PROCESS STARTED");
                    MapGenerator.readyToReceiveCommands = false;
                    MessageHelper.sendMessageToBotLogWebhook("BOT IS NO LONGER ACCEPTING COMMANDS");
                    TimeUnit.SECONDS.sleep(5);
                    MapSaveLoadManager.saveMaps();
                    MessageHelper.sendMessageToBotLogWebhook("MAPS HAVE BEEN SAVED");
                    MessageHelper.sendMessageToBotLogWebhook("SHUTDOWN PROCESS COMPLETE");
                    mainThread.join();
                    //
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
