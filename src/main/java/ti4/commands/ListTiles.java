package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.generator.TilesMapper;
import ti4.message.MessageHelper;

public class ListTiles implements Command {
    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":list_tiles");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        String tilesList = TilesMapper.getTilesList();
        MessageHelper.replyToMessage(msg, tilesList);
    }
}
