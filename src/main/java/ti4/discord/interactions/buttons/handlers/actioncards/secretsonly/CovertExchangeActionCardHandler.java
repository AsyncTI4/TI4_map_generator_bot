package ti4.discord.interactions.buttons.handlers.actioncards.secretsonly;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.objectives.SecretsOnlyService;

@UtilityClass
public class CovertExchangeActionCardHandler {

    public static final String AUTOMATION_ID = "covert_exchange";
    private static final String RESOLVE = "resolveCovertExchange";

    public static void offerResolution(Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Covert Exchange"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        String drawn = SecretsOnlyService.drawScorableSecretWithoutLimitCheck(game, player);
        if (drawn == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " could not draw a secret objective for _Covert Exchange_: none they can still score are left in the deck.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " drew 1 secret objective with _Covert Exchange_ and will return 1 to the deck.");
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose 1 secret objective to return to the deck for _Covert Exchange_.",
                SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player));
    }
}
