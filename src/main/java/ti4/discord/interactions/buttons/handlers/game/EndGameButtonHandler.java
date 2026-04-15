package ti4.discord.interactions.buttons.handlers.game;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.game.EndGameService;

@UtilityClass
class EndGameButtonHandler {

    @ButtonHandler("gameEnd")
    public static void gameEnd(ButtonInteractionEvent event, Game game) {
        EndGameService.secondHalfOfGameEnd(event, game, true, true, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gameEndConfirmation")
    public static void gameEndConfirmation(ButtonInteractionEvent event, Game game) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("gameEnd", "Confirm to End and Delete Game"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please confirm to end and DELETE the game", buttons);
    }
}
