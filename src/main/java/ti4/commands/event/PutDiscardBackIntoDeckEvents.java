package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PutDiscardBackIntoDeckEvents extends EventSubcommandData {
    public PutDiscardBackIntoDeckEvents() {
        super(Constants.PUT_DISCARD_BACK_INTO_DECK, "Put Event back into deck from discard");
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHUFFLE_EVENTS, "Enter True to shuffle, otherwise False to put on top (Defualt is True)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping option = event.getOption(Constants.EVENT_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No EVENT ID defined");
            return;
        }

        boolean shuffleEvents = event.getOption(Constants.SHUFFLE_EVENTS, true, OptionMapping::getAsBoolean);
        boolean success;
        if (shuffleEvents) {
            success = game.shuffleEventBackIntoDeck(option.getAsInt());
        } else {
            success = game.putEventBackIntoDeckOnTop(option.getAsInt());
        }

        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event put back into deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Event found");
        }
    }
}
