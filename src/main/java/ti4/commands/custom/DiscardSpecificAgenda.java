package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class DiscardSpecificAgenda extends GameStateSubcommand {

    public DiscardSpecificAgenda() {
        super(Constants.DISCARD_SPECIFIC_AGENDA, "Discard Specific Agenda", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_ID, "Agenda ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean removed = game.discardSpecificAgenda(event.getOption(Constants.AGENDA_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda discarded from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in game deck");
        }
    }
}
