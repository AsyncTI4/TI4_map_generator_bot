package ti4.discord.interactions.buttons.handlers.objective;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.objectives.SecretsOnlyService;

@UtilityClass
class SecretsOnlyButtonHandler {

    @ButtonHandler(SecretsOnlyService.KEEP_BUTTON_PREFIX)
    public static void keepOfferedSecret(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] tagAndSecret = buttonID.substring(SecretsOnlyService.KEEP_BUTTON_PREFIX.length())
                .split("_", 2);
        if (tagAndSecret.length != 2 || !SecretsOnlyService.keep(game, player, tagAndSecret[0], tagAndSecret[1])) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "That secret objective offer is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        int picksLeft = SecretsOnlyService.picksLeft(player, tagAndSecret[0]);
        if (picksLeft > 0) {
            ButtonHelper.deleteTheOneButton(event);
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Kept. Choose " + picksLeft + " more from the offer above.");
        } else {
            ButtonHelper.deleteMessage(event);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " kept 1 of the secret objectives they were offered.");
        Game.checkSOLimit(player);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        if (SecretsOnlyService.STARTING_OFFER_TAG.equals(tagAndSecret[0]) && !game.isFowMode()) {
            long stillChoosing = game.getRealPlayers().stream()
                    .filter(SecretsOnlyService::hasPendingOffer)
                    .count();
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    stillChoosing == 0
                            ? "Everyone has chosen their starting secret objectives."
                            : stillChoosing + " player(s) still choosing their starting secret objectives.");
        }
    }
}
