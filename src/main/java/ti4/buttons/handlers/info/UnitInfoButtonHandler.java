package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.UnitInfoService;

@UtilityClass
class UnitInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_UNIT_INFO, save = false)
    public static void sendUnitInfoSpecial(Game game, Player player, GenericInteractionCreateEvent event) {
        UnitInfoService.sendUnitInfo(game, player, event, false);
    }

    @ButtonHandler(value = Constants.REFRESH_ALL_UNIT_INFO, save = false)
    public static void sendUnitInfoAll(Game game, Player player, GenericInteractionCreateEvent event) {
        UnitInfoService.sendUnitInfo(game, player, event, true);
    }
}
