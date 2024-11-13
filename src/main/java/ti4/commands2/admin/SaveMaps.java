package ti4.commands2.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class SaveMaps extends AdminSubcommandData {

    public SaveMaps() {
        super(Constants.SAVE_GAMES, "Save all games");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameSaveLoadManager.saveMaps();
        MessageHelper.sendMessageToEventChannel(event, "Saved all maps");
    }
}
