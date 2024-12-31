package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.EventHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RevealEvent extends GameStateSubcommand {

    public RevealEvent() {
        super(Constants.REVEAL, "Reveal top Agenda from deck", true, false);
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of cards to reveal (Default = 1, Min 1, Max 2)");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM, "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        count = Math.min(count, 2);
        Game game = getGame();
        if (game.getEventDeckSize() < count) {
            MessageHelper.replyToMessage(event, "This game does not have enough cards in its event deck to reveal.");
            return;
        }
        boolean revealFromBottom = event.getOption(Constants.REVEAL_FROM_BOTTOM, false, OptionMapping::getAsBoolean);
        for (int i = 0; i < count; i++) {
            EventHelper.revealEvent(event, event.getChannel(), game.revealEvent(revealFromBottom));
        }
    }
}
