package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class EmergencyAppropriationsLLButtonHandler {
    private static final String RESOLVE = "resolveEmergencyAppropriations";
    public static final String STATE = "emergencyAppropriations_";

    @ButtonHandler(RESOLVE)
    public static void resolveEmergencyAppropriations(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(STATE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " played _Emergency Appropriations_. Their planets' influence counts as resources for this payment.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isActive(Game game, Player player) {
        return !game.getStoredValue(STATE + player.getFaction()).isEmpty();
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
    }
}
