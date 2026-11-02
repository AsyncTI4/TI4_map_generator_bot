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
import ti4.service.game.EndedGameScoringGuardService;

@UtilityClass
class EndGameButtonHandler {

    @ButtonHandler("gameEnd")
    public static void gameEnd(ButtonInteractionEvent event, Game game) {
        EndGameService.secondHalfOfGameEnd(event, game, true, true, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EndGameService.MOST_POINTS_END_GAME_BUTTON_ID)
    public static void gameEndWithMostPointsWinner(ButtonInteractionEvent event, Game game) {
        EndGameService.recordMostPointsWinner(game, event.getMessageChannel());
        gameEnd(event, game);
    }

    @ButtonHandler("gameEndConfirmation")
    public static void gameEndConfirmation(ButtonInteractionEvent event, Game game) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("gameEnd", "Confirm to End and Delete Game"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please confirm to end and DELETE the game", buttons);
    }

    @ButtonHandler(EndedGameScoringGuardService.CONTINUE_PLAYING_BUTTON_ID)
    public static void continuePlayingAfterEnd(ButtonInteractionEvent event, Game game) {
        game.reopen();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "This game's ended flag has been cleared. You may continue playing.");
        ButtonHelper.deleteMessage(event);
    }
}
