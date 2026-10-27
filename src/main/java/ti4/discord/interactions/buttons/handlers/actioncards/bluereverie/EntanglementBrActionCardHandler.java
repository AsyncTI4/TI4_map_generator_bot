package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class EntanglementBrActionCardHandler {
    private static final String RESOLVE = "resolveEntanglementBr";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Entanglement"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveEntanglement(ButtonInteractionEvent event, Game game, Player player) {
        TransactionHelper.activateEntanglement(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " resolved _Entanglement_. Their next transaction may exchange agents, commanders, and faction technologies.");
    }
}
