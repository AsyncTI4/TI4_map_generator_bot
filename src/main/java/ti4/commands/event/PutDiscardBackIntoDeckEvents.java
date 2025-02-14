package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class PutDiscardBackIntoDeckEvents extends GameStateSubcommand {

    public PutDiscardBackIntoDeckEvents() {
        super(Constants.PUT_DISCARD_BACK_INTO_DECK, "Put event back into deck from discard", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID, which is found between ()")
                .setRequired(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.SHUFFLE_EVENTS,
                "Enter True to shuffle, otherwise False to put on top (default is True)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean shuffleEvents = event.getOption(Constants.SHUFFLE_EVENTS, true, OptionMapping::getAsBoolean);
        Game game = getGame();
        boolean success;
        if (shuffleEvents) {
            success = game.shuffleEventBackIntoDeck(
                    event.getOption(Constants.EVENT_ID).getAsInt());
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event shuffled into deck.");
        } else {
            success = game.putEventBackIntoDeckOnTop(
                    event.getOption(Constants.EVENT_ID).getAsInt());
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event placed on top of deck.");
        }

        if (!success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No event found.");
        }
    }
}
