package ti4.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
class PickFromDiscardButtonHandler {

    @ButtonHandler("codexCardPick_")
    public static void pickACardFromDiscardStep1(Game game, Player player) {
        ActionCardHelper.pickACardFromDiscardStep1(game, player);
    }

    @ButtonHandler("pickFromDiscard_")
    public static void pickACardFromDiscardStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ActionCardHelper.pickACardFromDiscardStep2(game, player, event, buttonID);
    }
}
