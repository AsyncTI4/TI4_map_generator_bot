package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.decks.ShowActionCardsService;

class ShowDiscardActionCards extends GameStateSubcommand {

    public ShowDiscardActionCards() {
        super(Constants.SHOW_AC_DISCARD_LIST, "Show Action Card discard list", false, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_FULL_TEXT, "'true' to show full card text"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean showFullText = event.getOption(Constants.SHOW_FULL_TEXT, false, OptionMapping::getAsBoolean);
        ShowActionCardsService.showDiscard(game, event, showFullText);
    }
}
