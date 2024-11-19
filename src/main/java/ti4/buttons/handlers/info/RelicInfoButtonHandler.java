package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.RelicInfoService;

@UtilityClass
class RelicInfoButtonHandler {

    @ButtonHandler(Constants.REFRESH_RELIC_INFO)
    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        RelicInfoService.sendRelicInfo(game, player, event);
    }
}
