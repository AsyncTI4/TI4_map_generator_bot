package ti4.discord.interactions.buttons.handlers.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.game.SwapFactionService;

@UtilityClass
class SwapFactionButtonHandler {

    @ButtonHandler("swapToFaction_")
    public static void swapToFaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("swapToFaction_", "");
        SwapFactionService.secondHalfOfSwap(
                game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
