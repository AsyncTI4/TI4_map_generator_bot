package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.thundersedge.TeHelperTechs;
import ti4.message.MessageHelper;

@UtilityClass
public class ProFormaSessionBrActionCardHandler {
    private static final String RESOLVE = "resolveProFormaSessionBr";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                List.of(Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Pro Forma Session")));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveProFormaSession(ButtonInteractionEvent event, Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !game.getStoredValue("executiveOrder").isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Pro Forma Session_ cannot be resolved now.");
            return;
        }
        TeHelperTechs.revealProFormaSessionAgenda(event, game, player);
        ButtonHelper.deleteMessage(event);
    }
}
