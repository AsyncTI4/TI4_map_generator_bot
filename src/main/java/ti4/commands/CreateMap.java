package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.ResourceHelper;
import ti4.generator.GenerateMap;
import ti4.message.MessageHelper;

import java.io.File;

public class CreateMap implements Command{

    //todo remove static, add map handles. left for testing
    public static GenerateMap generateMapInstance;

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().equals(":create_map");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        String tileFile = ResourceHelper.getInstance().getTileFile("6player_setup.png");
        if (tileFile == null)
        {
            MessageHelper.replyToMessage(event.getMessage(), "Could not find base setup file");
            return;
        }
        File setupFile = new File(tileFile);
        generateMapInstance = new GenerateMap(setupFile);
        MessageHelper.replyToMessage(event.getMessage(), "Map initializes");
    }
}
