package ti4.discord.interactions.buttons.handlers.planet;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.planet.PlanetInfoService;

@UtilityClass
class PlanetInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_PLANET_INFO, save = false)
    public static void sendPlanetInfo(Player player) {
        PlanetInfoService.sendPlanetInfo(player);
    }
}
