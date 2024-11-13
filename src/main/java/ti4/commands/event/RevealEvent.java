package ti4.commands.event;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class RevealEvent extends GameStateSubcommand {

    public RevealEvent() {
        super(Constants.REVEAL, "Reveal top Agenda from deck", true, false);
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of cards to reveal (Default = 1)");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM, "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean revealFromBottom = event.getOption(Constants.REVEAL_FROM_BOTTOM, false, OptionMapping::getAsBoolean);
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);

        Game game = getGame();
        for (int i = 0; i < count; i++) {
            revealEvent(event, game, event.getChannel(), game.revealEvent(revealFromBottom));
        }
    }

    public static void revealEvent(GenericInteractionCreateEvent event, Game game, MessageChannel channel) {
        revealEvent(event, game, channel, game.revealEvent(false));
    }

    public static void revealEvent(GenericInteractionCreateEvent event, Game game, MessageChannel channel, String eventID) {
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel != null) {
            channel.sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong revealing an event; eventID: " + eventID);
        }
    }
}
