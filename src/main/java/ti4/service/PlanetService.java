package ti4.service;

import lombok.experimental.UtilityClass;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class PlanetService {

    public static void refreshPlanet(Player player, String planet) {
        if (!player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " the bot doesn't think you have a planet by the name of " + planet);
        }
        player.refreshPlanet(planet);
    }
}
