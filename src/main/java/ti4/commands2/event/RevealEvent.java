package ti4.commands2.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.EventHelper;
import ti4.map.Game;

class RevealEvent extends GameStateSubcommand {

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
            EventHelper.revealEvent(event, game, event.getChannel(), game.revealEvent(revealFromBottom));
        }
    }
}
