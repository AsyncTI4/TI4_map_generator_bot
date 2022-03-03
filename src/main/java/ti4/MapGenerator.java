package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.*;
import ti4.generator.GenerateMap;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.LoggerHandler;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.util.StringTokenizer;

public class MapGenerator {

    public static JDA jda;
    public static String userID;

    public static void main(String[] args)
            throws LoginException {

        jda = JDABuilder.createDefault(args[0]).build();
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








        userID = args[1];

        PositionMapper.init();
        TilesMapper.init();
        AliasHandler.init();

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new AddUnit());
        commandManager.addCommand(new CreateMap());
        commandManager.addCommand(new Shutdown());
        commandManager.addCommand(new ListTiles());
        commandManager.addCommand(new SaveMaps());
        commandManager.addCommand(new ListMaps());
        commandManager.addCommand(new SetMap());
        commandManager.addCommand(new ShowMap());


        Guild guild = jda.getGuildById(args[2]);

        CommandListUpdateAction commands = guild.updateCommands();

        commandManager.getCommandList().forEach(command -> command.registerCommands(commands));
        commands.queue();
        //        guild.upsertCommand("show_map", "Shows selected map").queue();

        MapSaveLoadManager.loadMaps();
    }
}
