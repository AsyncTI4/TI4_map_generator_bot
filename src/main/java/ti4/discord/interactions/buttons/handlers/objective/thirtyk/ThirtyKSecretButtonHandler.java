package ti4.discord.interactions.buttons.handlers.objective.thirtyk;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.thirtyk.ThirtyKSecretHooks;
import ti4.message.MessageHelper;

@UtilityClass
class ThirtyKSecretButtonHandler {

    @ButtonHandler(ThirtyKSecretHooks.SCORE_BUTTON)
    public static void scoreSecret(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String soID = buttonID.replace(ThirtyKSecretHooks.SCORE_BUTTON, "");
        Integer index = player.getSecretsUnscored().get(soID);
        if (index == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That secret objective is no longer in your hand.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (SecretObjectiveHelper.scoreSO(event, game, player, index, player.getCorrectChannel())) {
            ButtonHelper.deleteMessage(event);
        }
    }
}
