package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RemoveEvent extends GameStateSubcommand {

    public RemoveEvent() {
        super(Constants.REMOVE_EVENT, "Remove event", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Integer eventID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        boolean success = game.removeEventInEffect(eventID);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event removed.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event ID not found.");
        }
    }
}
