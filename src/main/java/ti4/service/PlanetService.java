package ti4.service;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class PlanetService {

    public static void refreshPlanet(Player player, String planet) {
        if (!player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " the bot doesn't think you have a planet by the name of " + planet);
        }
        player.refreshPlanet(planet);
    }

    public static String getPlanet(Tile tile, String planetName) {
        if (tile.isSpaceHolderValid(planetName)) return planetName;
        return tile.getUnitHolders().keySet().stream()
                .filter(id -> !Constants.SPACE.equals(id))
                .filter(unitHolderID -> unitHolderID.startsWith(planetName))
                .findFirst()
                .orElse(planetName);
    }
}
