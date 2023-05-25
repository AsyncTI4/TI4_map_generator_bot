package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListUnits extends HelpSubcommandData {

    public ListUnits() {
        super(Constants.LIST_UNITS, "List all units");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String unitList = Mapper.getUnitList();
        MessageHelper.sendMessageToThread(event.getChannel(), "Unit List", unitList);

    }
}
