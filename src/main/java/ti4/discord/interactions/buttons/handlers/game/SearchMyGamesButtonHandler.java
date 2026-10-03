package ti4.discord.interactions.buttons.handlers.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.helpers.SearchGameHelper;

@UtilityClass
class SearchMyGamesButtonHandler {

    @ButtonHandler(value = "searchMyGames", save = false)
    public static void searchMyGames(ButtonInteractionEvent event) {
        SearchGameHelper.searchGames(event.getUser(), event, false, false, false, true, false, true, false, false);
    }
}
