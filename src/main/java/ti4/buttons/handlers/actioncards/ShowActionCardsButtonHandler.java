package ti4.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.decks.ShowActionCardsService;

@UtilityClass
class ShowActionCardsButtonHandler {

    @ButtonHandler(value = "showDeck_unplayedAC", save = false)
    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        ShowActionCardsService.showUnplayedACs(game, event);
    }

    @ButtonHandler(value = "ACShowDiscardFullText", save = false)
    public static void showDiscardFullText(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteTheOneButton(event, "ACShowDiscardFullText", false);
        ShowActionCardsService.showDiscard(game, event, true);
    }

    @ButtonHandler(value = "ACShowUnplayedFullText", save = false)
    public static void showUnplayedFullText(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteTheOneButton(event, "ACShowUnplayedFullText", false);
        ShowActionCardsService.showUnplayedACs(game, event, true);
    }
}
