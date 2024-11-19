package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.decks.ShowActionCardsService;

class ShowAllUnplayedACs extends GameStateSubcommand {

    public ShowAllUnplayedACs() {
        super(Constants.SHOW_UNPLAYED_AC, "Show all unplayed Action Cards", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ShowActionCardsService.showUnplayedACs(getGame(), event);
    }
}
