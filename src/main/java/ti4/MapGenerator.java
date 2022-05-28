package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import ti4.commands.CommandManager;
import ti4.commands.HelpAction;
import ti4.commands.admin.AdminCommand;
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.cards.CardsCommand;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.info.*;
import ti4.commands.map.*;
import ti4.commands.player.PlayerCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tokens.*;
import ti4.commands.units.*;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.LoggerHandler;
import ti4.helpers.Storage;
import ti4.map.MapSaveLoadManager;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static JDA jda;
    public static String userID;

    public static void main(String[] args)
            throws LoginException {

        jda = JDABuilder.createDefault(args[0])
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
//                .enableIntents(Arrays.asList(GatewayIntent.values()))
                .build();
//        jda = JDABuilder.createLight(args[0], Collections.emptyList())
//                .addEventListeners(new Bot())
//                .setActivity(Activity.playing("Type /ping"))
//                .build();


        jda.addEventListener(new MessageListener());

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            LoggerHandler.log("Error waiting for bot to get ready");
        }
//        User user = event.getJDA().getUserById();
//            user.getName()
        userID = args[1];
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();


        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new RemoveTile());
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new CreateGame());
        commandManager.addCommand(new ListTiles());
        commandManager.addCommand(new ListGames());
        commandManager.addCommand(new SetGame());
        commandManager.addCommand(new ShowGame());
        commandManager.addCommand(new AddTileList());
        commandManager.addCommand(new DeleteGame());
        commandManager.addCommand(new ListUnits());
        commandManager.addCommand(new AddCC());
        commandManager.addCommand(new RemoveCC());
        commandManager.addCommand(new RemoveAllCC());
        commandManager.addCommand(new AddFrontierTokens());
        commandManager.addCommand(new AddControl());
        commandManager.addCommand(new RemoveControl());
        commandManager.addCommand(new MoveUnits());
        commandManager.addCommand(new ListPlanets());
        commandManager.addCommand(new RemoveToken());
        commandManager.addCommand(new AddToken());
        commandManager.addCommand(new AddUnitDamage());
        commandManager.addCommand(new RemoveUnitDamage());
        commandManager.addCommand(new RemoveAllUnitDamage());
        commandManager.addCommand(new HelpAction());
        commandManager.addCommand(new LogMessage());
        
        commandManager.addCommand(new ExploreCommand());
        commandManager.addCommand(new AdminCommand());
        commandManager.addCommand(new PlayerCommand());
        commandManager.addCommand(new GameCommand());
        commandManager.addCommand(new CardsCommand());
        commandManager.addCommand(new PNCardsCommand());
        commandManager.addCommand(new StatusCommand());
        commandManager.addCommand(new AgendaCommand());

        Guild guild = jda.getGuildById(args[2]);

        CommandListUpdateAction commands = guild.updateCommands();
        commandManager.getCommandList().forEach(command -> command.registerCommands(commands));
        commands.queue();

       //TI Community game
       if (args.length == 4) { 
	       Guild guild2 = jda.getGuildById(args[3]);
	       CommandListUpdateAction commandsC = guild2.updateCommands();
	       commandManager.getCommandList().forEach(command -> command.registerCommands(commandsC));
	       commandsC.queue();
       }
        //------------------------------------------------

//        CommandListUpdateAction commands_ = jda.updateCommands();
//        commandManager.getCommandList().forEach(command -> command.registerCommands(commands_));
//        commands_.queue();

//        guild.updateCommands().queue();
//        jda.updateCommands().queue();

        MapSaveLoadManager.loadMaps();
    }
}
