package ti4.discord.interactions.buttons.handlers.tigl;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.TIGLHelper;
import ti4.message.MessageHelper;
import ti4.service.tigl.TiglSetupService;

@UtilityClass
public class TiglButtonHandler {

    @ButtonHandler(TiglSetupService.LADDER_BUTTON_PREFIX)
    public static void setLadder(ButtonInteractionEvent event, Game game, String buttonID) {
        if (game == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "This button only works in the game's own channels.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!TiglSetupService.mayChangeLadder(game, event)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), TiglSetupService.ladderLockedMessage());
            ButtonHelper.deleteMessage(event);
            return;
        }

        String choice = StringUtils.substringAfter(buttonID, TiglSetupService.LADDER_BUTTON_PREFIX);
        TiglSetupService.LadderChoice result = TiglSetupService.applyLadderChoice(game, choice);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result.message());
        if (result.accepted()) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(TIGLHelper.RETRY_RANK_SNAPSHOT_BUTTON)
    public static void retryRankSnapshot(ButtonInteractionEvent event, Game game) {
        if (game == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "This button only works in the game's own channels.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        TIGLHelper.initializeRanksAsync(game, event.getMessageChannel());
    }
}
