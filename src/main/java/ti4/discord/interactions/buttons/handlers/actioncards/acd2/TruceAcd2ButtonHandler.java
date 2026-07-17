package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class TruceAcd2ButtonHandler {

    @ButtonHandler("resolveTruce")
    public static void resolveTruce(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " played _Truce_: the ground combat ends in a draw and "
                        + player.getRepresentationNoPing()
                        + "'s participating units remain in coexistence on the contested planet. The attacking player"
                        + " cannot engage these coexisting units in combat after this draw.");
    }
}
