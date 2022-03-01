package ti4;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import ti4.commands.AddTile;
import ti4.commands.AddUnit;
import ti4.commands.CommandManager;
import ti4.commands.CreateMap;
import ti4.commands.Shutdown;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static JDA jda;

    public static void main(String[] args)
            throws LoginException {

        jda = JDABuilder.createDefault(args[0]).build();
        jda.addEventListener(new MessageListener());

        PositionMapper.init();
        TilesMapper.init();

        CommandManager commandManager = CommandManager.getInstance();
        commandManager.addCommand(new AddTile());
        commandManager.addCommand(new AddUnit());
        commandManager.addCommand(new CreateMap());
        commandManager.addCommand(new Shutdown());
    }
}
