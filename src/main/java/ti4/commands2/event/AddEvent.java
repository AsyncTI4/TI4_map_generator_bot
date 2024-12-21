package ti4.commands2.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddEvent extends GameStateSubcommand {

    public AddEvent() {
        super(Constants.ADD_EVENT, "Add event as if it were a law (permanent/temporary)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Integer eventID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        boolean success = game.addEventInEffect(eventID);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event added.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event ID not found.");
        }
    }
}
