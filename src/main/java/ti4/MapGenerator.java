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
import ti4.commands.bothelper.BothelperCommand;
import ti4.commands.button.GenericButton;
import ti4.commands.capture.CaptureCommand;
import ti4.commands.cards.CardsInfo;
import ti4.commands.cardsac.ACCardsCommand;
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.all_info.AllInfo;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.cardsso.SOCardsCommand;
import ti4.commands.custom.CustomCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
import ti4.commands.franken.FrankenCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.help.*;
import ti4.commands.leaders.LeaderCommand;
import ti4.commands.map.*;
import ti4.commands.milty.MiltyCommand;
import ti4.commands.player.PlayerCommand;
import ti4.commands.special.SpecialCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tokens.*;
import ti4.commands.units.*;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Storage;
import ti4.map.MapSaveLoadManager;
import ti4.message.BotLogger;

import java.util.ArrayList;
import java.util.List;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static JDA jda;
    public static String userID;
    public static Guild guildPrimary = null;
    public static Guild guildSecondary = null;
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
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();

        //ROLES - FOR COMMAND PERMISSIONS
        //ADMIN ROLES
        adminRoles.add(jda.getRoleById("943596173896323072")); // Async TI4 Server (Hub)
        adminRoles.add(jda.getRoleById("1090914497352446042")); // Async Secondary
        adminRoles.add(jda.getRoleById("1062804021385105500")); // FoW Server
        adminRoles.add(jda.getRoleById("1067866210865250445")); // PrisonerOne's Test Server
        adminRoles.add(jda.getRoleById("1060656344581017621")); // Softnum's Server
        adminRoles.removeIf(r -> r == null);

        //DEVELOPER ROLES
        developerRoles.addAll(adminRoles); //admins can also execute developer commands
        developerRoles.add(jda.getRoleById("947648366056185897")); // Async TI4 Server (Hub)
        developerRoles.add(jda.getRoleById("1090958278479052820")); // Async Secondary
        developerRoles.add(jda.getRoleById("1088532767773564928")); // FoW Server
        developerRoles.removeIf(r -> r == null);

        //BOTHELPER ROLES
        bothelperRoles.addAll(developerRoles); //admins and developers can also execute bothelper commands
        bothelperRoles.add(jda.getRoleById("970033771179028531")); // Async TI4 Server (Hub)
        bothelperRoles.add(jda.getRoleById("1090914992301281341")); // Async Secondary
        bothelperRoles.add(jda.getRoleById("1088532690803884052")); // FoW Server
        bothelperRoles.removeIf(r -> r == null);



        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new RemoveTile());
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new CreateGame());
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
        // commandManager.addCommand(new AddControl()); //disabled due to confusing players - /player planet_add should be used
        // commandManager.addCommand(new RemoveControl()); //disabled due to confusing players - /player planet_remove should be used
        commandManager.addCommand(new MoveUnits());
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
        commandManager.addCommand(new CustomCommand());
        commandManager.addCommand(new FOWCommand());
        commandManager.addCommand(new MiltyCommand());
        commandManager.addCommand(new FrankenCommand());
        commandManager.addCommand(new CaptureCommand());
        commandManager.addCommand(new GenericButton());

        guildPrimary = jda.getGuildById(args[2]);

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

        BotLogger.log("BOT STARTED UP: " + guildPrimary.getName());
        MapSaveLoadManager.loadMaps();

        readyToReceiveCommands = true;
        BotLogger.log("BOT HAS FINISHED LOADING MAPS");

    }
}
