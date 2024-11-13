package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddEvent extends GameStateSubcommand {

    public AddEvent() {
        super(Constants.ADD_EVENT, "Add Event as if it were a Law (Permanent/Temporary)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Integer eventID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        boolean success = game.addEventInEffect(eventID);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event added");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event ID not found");
        }
    }
}
