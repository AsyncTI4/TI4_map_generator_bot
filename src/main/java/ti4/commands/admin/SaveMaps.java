package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.GameSaveLoadManager;

public class SaveMaps extends AdminSubcommandData {

    public SaveMaps() {
        super(Constants.SAVE_GAMES, "Save all games");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameSaveLoadManager.saveMaps();
        sendMessage("Saved all maps");
    }
}
