package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class AdministrativeExemptionLLButtonHandler {
    private static final String RESOLVE = "resolveAdministrativeExemption";
    public static final String STATE = "administrativeExemption_";

    @ButtonHandler(RESOLVE)
    public static void resolveAdministrativeExemption(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(STATE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " played _Administrative Exemption_. Their next strategy-card secondary will not cost a command token.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasExemption(Game game, Player player) {
        return !game.getStoredValue(STATE + player.getFaction()).isEmpty();
    }

    public static boolean useExemption(Game game, Player player) {
        if (!hasExemption(game, player)) return false;
        game.removeStoredValue(STATE + player.getFaction());
        return true;
    }
}
