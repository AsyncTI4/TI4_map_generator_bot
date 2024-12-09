package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.TechInfoService;

@UtilityClass
class TechInfoButtonHandler {

    @ButtonHandler(Constants.REFRESH_TECH_INFO)
    public static void sendTechInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        TechInfoService.sendTechInfo(game, player, event);
    }
}
