package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.LeaderInfoService;

@UtilityClass
class LeaderInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_LEADER_INFO, save = false)
    public static void sendLeadersInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        LeaderInfoService.sendLeadersInfo(game, player, event);
    }
}
