package ti4.commands.info;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListUnits implements Command {

    @Override
    public String getActionID() {
        return Constants.LIST_UNITS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String unitList = Mapper.getUnitList();
        MessageHelper.sendMessageToChannel(event.getChannel(), unitList);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows available units")

        );
    }
}
