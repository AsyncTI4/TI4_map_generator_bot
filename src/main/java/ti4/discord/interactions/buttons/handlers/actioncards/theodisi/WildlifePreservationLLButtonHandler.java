package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class WildlifePreservationLLButtonHandler {
    private static final String RESOLVE = "resolveWildlifePreservation";
    public static final String STATE = "wildlifePreservation_";

    @ButtonHandler(RESOLVE)
    public static void resolveWildlifePreservation(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(STATE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " played _Wildlife Preservation_. Their planets use their higher resource or influence value for this payment.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isActive(Game game, Player player) {
        return !game.getStoredValue(STATE + player.getFaction()).isEmpty();
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
    }
}
