package ti4.discord.interactions.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.button.ReactionService;

@UtilityClass
class ReactionButtonHandler {

    @ButtonHandler(Constants.GENERIC_BUTTON_ID_PREFIX)
    public static void reactToGenericButton(ButtonInteractionEvent event, Game game, Player player) {
        ReactionService.addReaction(event, game, player);
    }

    @ButtonHandler("pass_on_abilities")
    public static void passOnAbilities(ButtonInteractionEvent event, Game game, Player player) {
        ReactionService.addReaction(
                event, game, player, " is " + event.getButton().getLabel().toLowerCase() + ".");
    }
}
