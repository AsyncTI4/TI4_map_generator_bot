package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListUnits implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.LIST_UNITS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String unitList = Mapper.getUnitList();
        MessageHelper.replyToMessage(event, unitList);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.LIST_UNITS, "Shows available units")

        );
    }
}
