package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListPlanets extends HelpSubcommandData {

    public ListPlanets() {
        super(Constants.LIST_PLANETS, "List all planets");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tilesList = Mapper.getPlanetList();
        MessageHelper.sendMessageToChannel(event.getChannel(), tilesList);
    }
}
