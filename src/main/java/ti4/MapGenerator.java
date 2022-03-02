package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import ti4.commands.*;
import ti4.generator.GenerateMap;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;
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
        jda.addEventListener(new MessageListener());

        userID = args[1];

        PositionMapper.init();
        TilesMapper.init();

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new AddUnit());
        commandManager.addCommand(new CreateMap());
        commandManager.addCommand(new Shutdown());
        commandManager.addCommand(new ListTiles());
        commandManager.addCommand(new SaveMaps());
        commandManager.addCommand(new ListMaps());
        commandManager.addCommand(new SetMap());

        MapSaveLoadManager.loadMaps();
    }
}
