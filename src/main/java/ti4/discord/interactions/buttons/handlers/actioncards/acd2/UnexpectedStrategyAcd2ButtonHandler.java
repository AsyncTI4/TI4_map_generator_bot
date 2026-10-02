package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class UnexpectedStrategyAcd2ButtonHandler {

    @ButtonHandler("resolveUnexpectedStrategy")
    public static void resolveUnexpectedStrategy(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "unexpectedstrategy");
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no command tokens on the game board to remove for _Unexpected Strategy_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose which system to remove your command token from for _Unexpected Strategy_.",
                buttons);
    }
}
