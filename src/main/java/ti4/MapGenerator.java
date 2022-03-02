package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import ti4.commands.*;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;
import ti4.message.MessageHelper;

import javax.security.auth.login.LoginException;

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
    }
}
