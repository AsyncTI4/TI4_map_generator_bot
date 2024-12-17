package ti4.buttons.handlers.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.game.EndGameService;

@UtilityClass
public class EndGameButtonHandler {

    @ButtonHandler("gameEnd")
    public static void gameEnd(ButtonInteractionEvent event, Game game) {
        EndGameService.secondHalfOfGameEnd(event, game, true, true);
        ButtonHelper.deleteMessage(event);
    }
}
