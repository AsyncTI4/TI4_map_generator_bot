package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListPlanets implements Command {

    @Override
    public String getActionID() {
        return Constants.LIST_PLANETS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tilesList = Mapper.getPlanetList();
        MessageHelper.replyToMessage(event, tilesList);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows available planets")

        );
    }
}
