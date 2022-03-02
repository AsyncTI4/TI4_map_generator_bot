package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.MapGenerator;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class SaveMaps implements Command {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":save_maps");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        if (event.getAuthor().getId().equals(MapGenerator.userID)) {
            MapSaveLoadManager.saveMaps();
            MessageHelper.replyToMessage(event.getMessage(), "Saved");
        } else {
            MessageHelper.replyToMessage(event.getMessage(), "Not Authorized save attempt");
        }
    }
}
