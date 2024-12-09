package ti4.buttons.handlers.tech;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

@UtilityClass
class GetTechButtonHandler {

    @ButtonHandler("getTech_")
    public static void getTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        PlayerTechService.getTech(game, player, event, buttonID);
    }
}
