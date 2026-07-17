package ti4.discord.interactions.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;

@UtilityClass
class PickFromDiscardButtonHandler {

    @ButtonHandler("codexCardPick_")
    public static void pickACardFromDiscardStep1(Game game, Player player) {
        ActionCardHelper.pickACardFromDiscardStep1(game, player);
    }

    @ButtonHandler("pickFromDiscard_")
    public static void pickACardFromDiscardStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        ActionCardHelper.pickACardFromDiscardStep2(game, player, event, buttonID);
    }
}
