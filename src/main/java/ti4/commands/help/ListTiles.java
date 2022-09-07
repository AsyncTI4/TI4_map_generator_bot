package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListTiles extends HelpSubcommandData {

    public ListTiles() {
        super(Constants.LIST_TILES, "List all tiles");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tilesList = Mapper.getTilesList();
        MessageHelper.replyToMessage(event, tilesList);
    }
}
