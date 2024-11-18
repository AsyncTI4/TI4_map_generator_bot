package ti4.buttons.handlers.decks;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.decks.ShowActionCardsService;

@UtilityClass
class ShowActionCardsButtonHandler {

    @ButtonHandler("showDeck_unplayedAC")
    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        ShowActionCardsService.showUnplayedACs(game, event);
    }

    @ButtonHandler("ACShowDiscardFullText")
    public static void showDiscardFullText(GenericInteractionCreateEvent event, Game game) {
        ShowActionCardsService.showDiscard(game, event, true);
    }
}
