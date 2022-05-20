package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;

public class PlanetRemove extends PlanetAddRemove {
    public PlanetRemove() {
        super(Constants.PLANET_REMOVE, "Remove Planet");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.removePlanet(planet);
    }
}
