package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;

public class CreateGameChannels extends BothelperSubcommandData {
    public CreateGameChannels(){
        super(Constants.CREATE_GAME_CHANNELS, "Create Role and Game Channels for a New Game");

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

    }
}
