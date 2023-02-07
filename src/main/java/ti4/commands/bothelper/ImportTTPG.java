package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;

public class ImportTTPG extends BothelperSubcommandData {
    public ImportTTPG(){
        super(Constants.IMPORT_TTPG, "Create Role and Game Channels for a New Game");

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

    }
}
