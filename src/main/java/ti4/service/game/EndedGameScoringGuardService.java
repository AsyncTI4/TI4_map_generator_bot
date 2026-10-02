package ti4.service.game;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class EndedGameScoringGuardService {

    public static final String CONTINUE_PLAYING_BUTTON_ID = "continuePlayingAfterEnd";
    static final String SCORING_BLOCKED_MESSAGE =
            "It appears that this game has already ended. If that is a mistake, use this button to continue playing.";

    public static boolean sendPromptIfGameEnded(Game game, MessageChannel channel) {
        if (!game.isHasEnded()) {
            return false;
        }
        if (game.getHighestScore() < game.getVp()) {
            game.setHasEnded(false);
            return false;
        }
        if (channel != null) {
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, SCORING_BLOCKED_MESSAGE, getButtons());
        }
        return true;
    }

    static List<Button> getButtons() {
        return List.of(
                Buttons.green(CONTINUE_PLAYING_BUTTON_ID, "Continue Playing"),
                Buttons.gray("deleteButtons", "Delete These Buttons"));
    }
}
