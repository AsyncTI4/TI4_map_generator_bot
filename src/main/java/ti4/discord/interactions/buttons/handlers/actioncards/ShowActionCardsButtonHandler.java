package ti4.discord.interactions.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.service.decks.ShowActionCardsService;

@UtilityClass
class ShowActionCardsButtonHandler {

    @ButtonHandler(value = "showDeck_unplayedAC", save = false)
    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        ShowActionCardsService.showUnplayedACs(game, event);
    }

    @ButtonHandler(value = "ACShowDiscardFullText", save = false)
    public static void showDiscardFullText(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        ShowActionCardsService.showDiscard(game, event, true);
    }

    @ButtonHandler(value = "ACShowUnplayedFullText", save = false)
    public static void showUnplayedFullText(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        ShowActionCardsService.showUnplayedACs(game, event, true);
    }
}
