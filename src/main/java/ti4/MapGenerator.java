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
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.cards.CardsCommand;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.cardsso.SOCardsCommand;
import ti4.commands.custom.CustomCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
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
import ti4.helpers.Storage;
import ti4.map.MapSaveLoadManager;
import ti4.message.BotLogger;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static JDA jda;
    public static String userID;
    public static Guild guild;
    public static String adminID;
    public static Role adminRole;
    public static Role developerRole;
    public static Role bothelperRole;

    public static void main(String[] args)
            throws LoginException {

        jda = JDABuilder.createDefault(args[0])
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
//                .enableIntents(Arrays.asList(GatewayIntent.values()))
                .build();

        jda.addEventListener(new MessageListener(), new ButtonListener());
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            BotLogger.log("Error waiting for bot to get ready");
        }
//        User user = event.getJDA().getUserById();
        userID = args[1];
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();

        adminRole = jda.getRoleById("943596173896323072");
        developerRole = jda.getRoleById("947648366056185897");
        bothelperRole = jda.getRoleById("970033771179028531");

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new RemoveTile());
        commandManager.addCommand(new AddUnits());
        commandManager.addCommand(new RemoveUnits());
        commandManager.addCommand(new RemoveAllUnits());
        commandManager.addCommand(new CreateGame());
        commandManager.addCommand(new SetGame());
        commandManager.addCommand(new ShowGame());
        commandManager.addCommand(new AddTileList());
        commandManager.addCommand(new DeleteGame());
        commandManager.addCommand(new AddCC());
        commandManager.addCommand(new RemoveCC());
        commandManager.addCommand(new RemoveAllCC());
        commandManager.addCommand(new AddFrontierTokens());
        commandManager.addCommand(new AddControl());
        commandManager.addCommand(new RemoveControl());
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
        commandManager.addCommand(new CardsCommand());
        commandManager.addCommand(new PNCardsCommand());
        commandManager.addCommand(new SOCardsCommand());
        commandManager.addCommand(new StatusCommand());
        commandManager.addCommand(new AgendaCommand());
        commandManager.addCommand(new SpecialCommand());
        commandManager.addCommand(new LeaderCommand());
        commandManager.addCommand(new CustomCommand());
        commandManager.addCommand(new FOWCommand());
        commandManager.addCommand(new MiltyCommand());

        guild = jda.getGuildById(args[2]);

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

       if (args.length == 5) {
	       Guild guild3 = jda.getGuildById(args[4]);
	       CommandListUpdateAction commandsD = guild3.updateCommands();
	       commandManager.getCommandList().forEach(command -> command.registerCommands(commandsD));
	       commandsD.queue();
       }
       BotLogger.log("BOT STARTED UP!!!");
        //------------------------------------------------

//        CommandListUpdateAction commands_ = jda.updateCommands();
//        commandManager.getCommandList().forEach(command -> command.registerCommands(commands_));
//        commands_.queue();

//        guild.updateCommands().queue();
//        jda.updateCommands().queue();
        MapSaveLoadManager.loadMaps();
    }
}
